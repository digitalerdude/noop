import XCTest
@testable import Strand

/// (#767) A strap with a corrupt RTC banks records stamped far in the future. Per-connection clock
/// correction then scatters those records across the wrong calendar days, so the dashboard stays empty
/// even after a clean ~20k-row offload. `strapFutureClockGuide` detects the implausibly-future newest
/// banked record and returns a drain/recharge recovery guide. These pin the threshold and the message
/// text, which must stay byte-identical with the Android `StrapFutureClockTest` twin (date-free string).
@MainActor
final class StrapFutureClockTests: XCTestCase {

    // The exact recovery message, shared verbatim with Android. Any drift fails parity on one side.
    private let expectedGuide = "Your strap's clock is wrong: it's reporting banked data dated far in the future, so NOOP can't file your history on the right day and the dashboard stays empty even after a sync completes. Reconnecting won't fix it; the strap's clock has to reset. Fully drain the strap to 0%, charge it back to 100%, then reconnect. If it still reports future dates after that, it's a strap firmware issue for WHOOP support."

    func testFutureNewestSurfacesGuide() {
        let now = 1_750_000_000
        // ~5 months ahead (the #767 reporter saw 2026-11 .. 2029-11 on a 2026 phone).
        let future = now + 150 * 86_400
        XCTAssertEqual(BLEManager.strapFutureClockGuide(newestUnix: future, nowUnix: now), expectedGuide)
    }

    func testPresentNewestReturnsNil() {
        let now = 1_750_000_000
        // Newest banked record is last night — normal, no guide.
        XCTAssertNil(BLEManager.strapFutureClockGuide(newestUnix: now - 86_400, nowUnix: now))
    }

    func testTwoDayMarginClearsBenignSkew() {
        let now = 1_750_000_000
        // Exactly two days ahead is NOT tripped (margin clears device/phone clock skew)...
        XCTAssertNil(BLEManager.strapFutureClockGuide(newestUnix: now + 2 * 86_400, nowUnix: now))
        // ...one second past the margin IS tripped.
        XCTAssertEqual(BLEManager.strapFutureClockGuide(newestUnix: now + 2 * 86_400 + 1, nowUnix: now), expectedGuide)
    }
}
