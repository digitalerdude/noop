import XCTest
@testable import StrandAnalytics

/// #393 twin of the Kotlin NightlyBaselineMergeTest. On-device nightly HRV/RHR must seed the recovery
/// baseline on days the import left BLANK (present-but-nil), not only on days with no daily row at all.
/// Before the fix, a strap user whose daily rows came from a Health-Connect SLEEP import (avgHrv always nil)
/// had the on-device HRV blocked, so the baseline starved below the 4-night seed and Charge never
/// calibrated. Pure: no DB, no strap.
final class NightlyBaselineMergeTests: XCTestCase {

    private let hrvCfg = Baselines.metricCfg["hrv"]!

    func testFillBlankNights_fillsAbsentAndPresentNil_neverOverwritesReal() {
        // Use updateValue so "2026-06-02" is present-but-nil (subscript-assigning nil would drop the key).
        var hist: [String: Double?] = ["2026-06-01": 50.0]   // real imported value -> must WIN
        hist.updateValue(nil, forKey: "2026-06-02")          // present-but-nil -> must be filled (#393)
        let nightly: [String: Double?] = [
            "2026-06-01": 99.0,   // must NOT overwrite the real 50.0
            "2026-06-02": 40.0,   // fills the present-nil day
            "2026-06-03": 42.0,   // absent day -> added
        ]
        NightlyBaselineMerge.fillBlankNights(&hist, nightly)
        XCTAssertEqual(hist["2026-06-01"] ?? nil, 50.0)   // real value preserved
        XCTAssertEqual(hist["2026-06-02"] ?? nil, 40.0)   // present-nil filled
        XCTAssertEqual(hist["2026-06-03"] ?? nil, 42.0)   // absent filled
    }

    func testBlankImportedNights_seedFromOnDeviceHrv_crossSeed_andTrackLevel() {
        // A strap + Health-Connect user: every daily row exists (HC sleep) but carries avgHrv = nil.
        let days = ["2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05"]

        func foldWithInjected(_ level: Double) -> BaselineState {
            var hist: [String: Double?] = [:]
            for d in days { hist.updateValue(nil, forKey: d) }   // present-but-nil (HC sleep-only)
            var nightly: [String: Double?] = [:]
            for d in days { nightly[d] = level }
            NightlyBaselineMerge.fillBlankNights(&hist, nightly)
            let seq = hist.keys.sorted().map { hist[$0]! }
            return Baselines.foldHistory(seq, cfg: hrvCfg)
        }

        // Regression: pre-fix these 5 present-nil days would have stayed nil -> nValid 0 -> never usable.
        let lowBase = foldWithInjected(35.0)
        let highBase = foldWithInjected(70.0)
        XCTAssertTrue(lowBase.usable, "5 filled HRV nights must cross the seed gate")
        XCTAssertTrue(highBase.usable, "5 filled HRV nights must cross the seed gate")

        // Varying input (CLAUDE.md): the baseline must TRACK the injected level, not coincidentally match one.
        XCTAssertLessThan(lowBase.baseline, highBase.baseline)
        XCTAssertEqual(lowBase.baseline, 35.0, accuracy: 3.0)
        XCTAssertEqual(highBase.baseline, 70.0, accuracy: 3.0)

        // End-to-end: recovery is non-nil once the filled baseline is usable.
        let rhrBase = Baselines.foldHistory([52.0, 51.0, 53.0, 52.0], cfg: Baselines.restingHRCfg)
        let score = RecoveryScorer.recovery(
            hrv: 35.0, rhr: 52.0, resp: nil,
            hrvBaseline: lowBase, rhrBaseline: rhrBase, respBaseline: nil, sleepPerf: 0.9)
        XCTAssertNotNil(score, "recovery must be non-nil once the filled baseline is usable")
    }
}
