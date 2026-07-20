import XCTest
@testable import StrandAnalytics

final class BehaviorInsightsTests: XCTestCase {

    // MARK: - effect core computation

    func testEffectMeansDeltaAndSign() {
        // With-days outcome mean 60.5, without-days mean 70.125 → delta -9.625,
        // pct ≈ -13.7255%. Behavior lowers the outcome → negative delta & cohensD.
        let outcome: [String: Double] = [
            "d01": 60, "d02": 62, "d03": 58, "d04": 61, "d05": 59, "d06": 63,   // with
            "d07": 70, "d08": 72, "d09": 68, "d10": 71, "d11": 69, "d12": 73,   // without
            "d13": 70, "d14": 68,
        ]
        let behaviorDays: Set<String> = ["d01", "d02", "d03", "d04", "d05", "d06"]
        let e = BehaviorInsights.effect(behaviorDays: behaviorDays, askedDays: Set(outcome.keys),
                                        outcomeByDay: outcome,
                                        behavior: "Alcohol", outcome: "Recovery")!
        XCTAssertEqual(e.nWith, 6)
        XCTAssertEqual(e.nWithout, 8)
        XCTAssertEqual(e.meanWith, 60.5, accuracy: 1e-9)
        XCTAssertEqual(e.meanWithout, 70.125, accuracy: 1e-9)
        XCTAssertEqual(e.delta, -9.625, accuracy: 1e-9)
        XCTAssertEqual(e.pctChange!, -13.725490196078432, accuracy: 1e-9)
        XCTAssertLessThan(e.cohensD, 0)                  // lower outcome → negative
        XCTAssertEqual(e.cohensD, -5.247290322400142, accuracy: 1e-6)
        XCTAssertTrue(e.significant)                     // big separation, n≥5 both sides
    }

    func testEffectPositiveDirection() {
        // Behavior RAISES the outcome → positive delta.
        let outcome: [String: Double] = [
            "a": 80, "b": 82, "c": 78, "d": 81, "e": 79,   // with (mean 80)
            "f": 70, "g": 72, "h": 68, "i": 71, "j": 69,   // without (mean 70)
        ]
        let e = BehaviorInsights.effect(behaviorDays: ["a", "b", "c", "d", "e"],
                                        askedDays: Set(outcome.keys),
                                        outcomeByDay: outcome,
                                        behavior: "Meditation", outcome: "Recovery")!
        XCTAssertEqual(e.delta, 10.0, accuracy: 1e-9)
        XCTAssertGreaterThan(e.cohensD, 0)
        XCTAssertEqual(e.pctChange!, 100.0 * 10.0 / 70.0, accuracy: 1e-9)
        XCTAssertTrue(e.significant)
    }

    func testEffectNilWhenOneGroupEmpty() {
        // Behavior logged every day → no "without" group.
        let outcome: [String: Double] = ["a": 60, "b": 61, "c": 62]
        XCTAssertNil(BehaviorInsights.effect(behaviorDays: ["a", "b", "c"],
                                             askedDays: Set(outcome.keys),
                                             outcomeByDay: outcome,
                                             behavior: "X", outcome: "Recovery"))
        // Behavior never logged → no "with" group.
        XCTAssertNil(BehaviorInsights.effect(behaviorDays: [],
                                             askedDays: Set(outcome.keys),
                                             outcomeByDay: outcome,
                                             behavior: "X", outcome: "Recovery"))
    }

    func testEffectIgnoresBehaviorDaysWithNoOutcome() {
        // "z" is in behaviorDays but has no outcome value → not counted in nWith.
        let outcome: [String: Double] = ["a": 60, "b": 62, "c": 70, "d": 72]
        let e = BehaviorInsights.effect(behaviorDays: ["a", "b", "z"],
                                        askedDays: Set(outcome.keys).union(["z"]),
                                        outcomeByDay: outcome,
                                        behavior: "X", outcome: "Recovery")!
        XCTAssertEqual(e.nWith, 2)        // a, b only
        XCTAssertEqual(e.nWithout, 2)     // c, d
    }

    // MARK: - never-asked days (#631: a missing journal answer is not an implicit "no")

    func testEffectExcludesUnaskedDaysFromBothGroups() {
        // "z" has an outcome value but was never asked (not in behaviorDays or askedDays) →
        // dropped entirely, not counted as "without".
        let outcome: [String: Double] = ["a": 60, "b": 62, "c": 70, "d": 72, "z": 65]
        let e = BehaviorInsights.effect(behaviorDays: ["a", "b"],
                                        askedDays: ["a", "b", "c", "d"],
                                        outcomeByDay: outcome,
                                        behavior: "X", outcome: "Recovery")!
        XCTAssertEqual(e.nWith, 2)      // a, b
        XCTAssertEqual(e.nWithout, 2)   // c, d — "z" excluded, not counted as without
    }

    func testEffectAskedNoStillCountsAsWithout() {
        // A day that WAS asked and explicitly answered "no" (in askedDays, not in behaviorDays)
        // still counts as "without" — the fix only excludes NEVER-asked days.
        let outcome: [String: Double] = ["a": 60, "b": 61, "c": 70, "d": 71, "e": 72]
        let e = BehaviorInsights.effect(behaviorDays: ["a", "b"],
                                        askedDays: ["a", "b", "c", "d", "e"],
                                        outcomeByDay: outcome,
                                        behavior: "X", outcome: "Recovery")!
        XCTAssertEqual(e.nWith, 2)
        XCTAssertEqual(e.nWithout, 3)
    }

    func testEffectBehaviorDayNotInAskedStillCountsAsWith() {
        // Defensive OR: a "yes" day missing from askedDays (a caller build gap) is never dropped
        // from "with".
        let outcome: [String: Double] = ["a": 80, "b": 60, "c": 61]
        let e = BehaviorInsights.effect(behaviorDays: ["a"],
                                        askedDays: ["b", "c"],   // "a" deliberately missing
                                        outcomeByDay: outcome,
                                        behavior: "X", outcome: "Recovery")!
        XCTAssertEqual(e.nWith, 1)      // "a" still counted despite the askedDays gap
        XCTAssertEqual(e.nWithout, 2)
    }

    func testEffectRealisticSparseJournalQuestion() {
        // #631 shape: a rarely-asked question logged "yes" on 3 days and "no" on 2 days, out of
        // 300 total outcome-days. The other 295 days must be excluded, not lumped into "without".
        var outcome: [String: Double] = [:]
        for i in 0..<300 { outcome["d\(i)"] = Double(60 + i % 10) }
        let behaviorDays: Set<String> = ["d0", "d1", "d2"]
        let askedDays: Set<String> = behaviorDays.union(["d3", "d4"])
        let e = BehaviorInsights.effect(behaviorDays: behaviorDays, askedDays: askedDays,
                                        outcomeByDay: outcome,
                                        behavior: "Travelled in a car or train?", outcome: "Charge")!
        XCTAssertEqual(e.nWith, 3)
        XCTAssertEqual(e.nWithout, 2)   // NOT 297
    }

    // MARK: - significance flips

    func testSignificanceFlipsWithGroupSize() {
        // SAME clear separation (≈60 vs ≈70), but only 4 days per group → even
        // with a tiny p-value the min-group guard (≥5) blocks significance.
        let smallOutcome: [String: Double] = [
            "w1": 60, "w2": 61, "w3": 59, "w4": 60,
            "o1": 70, "o2": 71, "o3": 69, "o4": 70,
        ]
        let small = BehaviorInsights.effect(behaviorDays: ["w1", "w2", "w3", "w4"],
                                            askedDays: Set(smallOutcome.keys),
                                            outcomeByDay: smallOutcome,
                                            behavior: "X", outcome: "Recovery")!
        XCTAssertLessThan(small.pApprox, 0.05)       // strong evidence numerically…
        XCTAssertEqual(Swift.min(small.nWith, small.nWithout), 4)
        XCTAssertFalse(small.significant)            // …but n too small → not flagged

        // Add a 5th day per group with the same separation → now significant.
        let bigOutcome: [String: Double] = [
            "w1": 60, "w2": 61, "w3": 59, "w4": 60, "w5": 60,
            "o1": 70, "o2": 71, "o3": 69, "o4": 70, "o5": 70,
        ]
        let big = BehaviorInsights.effect(behaviorDays: ["w1", "w2", "w3", "w4", "w5"],
                                          askedDays: Set(bigOutcome.keys),
                                          outcomeByDay: bigOutcome,
                                          behavior: "X", outcome: "Recovery")!
        XCTAssertEqual(Swift.min(big.nWith, big.nWithout), 5)
        XCTAssertTrue(big.significant)
    }

    func testSignificanceFlipsWithSeparation() {
        // Big groups but NO real separation (heavily overlapping) → not significant.
        let outcome: [String: Double] = [
            "w1": 65, "w2": 71, "w3": 60, "w4": 75, "w5": 66, "w6": 70,
            "o1": 64, "o2": 72, "o3": 61, "o4": 74, "o5": 67, "o6": 69,
        ]
        let e = BehaviorInsights.effect(behaviorDays: ["w1", "w2", "w3", "w4", "w5", "w6"],
                                        askedDays: Set(outcome.keys),
                                        outcomeByDay: outcome,
                                        behavior: "X", outcome: "Recovery")!
        XCTAssertGreaterThan(e.pApprox, 0.05)    // no separation → weak evidence
        XCTAssertFalse(e.significant)
    }

    // MARK: - ranking

    func testRankOrdersByEffectSizeSignificantFirst() {
        // Three behaviors over a shared outcome series of 12 days.
        let outcome: [String: Double] = [
            "d1": 50, "d2": 52, "d3": 48, "d4": 51, "d5": 49, "d6": 53,
            "d7": 70, "d8": 72, "d9": 68, "d10": 71, "d11": 69, "d12": 73,
        ]
        // Strong: cleanly splits the low half (d1..d6) vs high half → big |d|, significant.
        let strong: Set<String> = ["d1", "d2", "d3", "d4", "d5", "d6"]
        // Weak: a scattered 3-day set, small + not enough per-group for significance.
        let weak: Set<String> = ["d1", "d7", "d2"]
        // Tiny-but-significant-impossible: 2 days only.
        let tiny: Set<String> = ["d3", "d9"]
        let asked: [String: Set<String>] = [
            "Strong": Set(outcome.keys), "Weak": Set(outcome.keys), "Tiny": Set(outcome.keys),
        ]

        let ranked = BehaviorInsights.rank(behaviors: ["Strong": strong, "Weak": weak, "Tiny": tiny],
                                           asked: asked,
                                           outcomeByDay: outcome, outcome: "Recovery")
        XCTAssertEqual(ranked.count, 3)
        XCTAssertEqual(ranked.first?.behavior, "Strong")   // significant + largest |d|
        XCTAssertTrue(ranked.first!.significant)
        // Non-significant entries trail the significant one.
        XCTAssertFalse(ranked[1].significant)
        XCTAssertFalse(ranked[2].significant)
        // Among the non-significant, larger |cohensD| comes first.
        XCTAssertGreaterThanOrEqual(abs(ranked[1].cohensD), abs(ranked[2].cohensD))
    }

    func testRankDropsUncomputableBehaviors() {
        let outcome: [String: Double] = ["a": 60, "b": 62, "c": 70, "d": 72]
        // "AllDays" covers every day → no without group → dropped.
        let ranked = BehaviorInsights.rank(behaviors: [
            "AllDays": ["a", "b", "c", "d"],
            "Half": ["a", "b"],
        ], asked: [
            "AllDays": Set(outcome.keys),
            "Half": Set(outcome.keys),
        ], outcomeByDay: outcome, outcome: "Recovery")
        XCTAssertEqual(ranked.count, 1)
        XCTAssertEqual(ranked.first?.behavior, "Half")
    }

    func testRankAskedDaysFallsBackToBehaviorDaysWhenMissing() {
        // A behaviour missing from `asked` falls back to its own behaviorDays — i.e. only the
        // "with" days count as asked, so there's no "without" group and the behaviour is dropped,
        // rather than crashing or silently reproducing the old "everything else is without" bug.
        let outcome: [String: Double] = ["a": 60, "b": 62, "c": 70, "d": 72]
        let ranked = BehaviorInsights.rank(behaviors: ["Half": ["a", "b"]],
                                           asked: [:],
                                           outcomeByDay: outcome, outcome: "Recovery")
        XCTAssertTrue(ranked.isEmpty)
    }

    // MARK: - sentence

    func testSentenceLowerWithPercent() {
        // Integer means avoid half-rounding ambiguity: with=60, without=80 →
        // delta -20, pct -25% → "25% lower (avg 60 vs 80, n=5 vs 5)".
        let outcome: [String: Double] = [
            "w1": 58, "w2": 62, "w3": 60, "w4": 59, "w5": 61,   // mean 60
            "o1": 78, "o2": 82, "o3": 80, "o4": 79, "o5": 81,   // mean 80
        ]
        let e = BehaviorInsights.effect(behaviorDays: ["w1", "w2", "w3", "w4", "w5"],
                                        askedDays: Set(outcome.keys),
                                        outcomeByDay: outcome,
                                        behavior: "Alcohol", outcome: "Recovery")!
        let s = BehaviorInsights.sentence(e)
        XCTAssertEqual(s, "On days you logged ‘Alcohol’, Recovery was 25% lower (avg 60 vs 80, n=5 vs 5).")
    }

    func testSentenceHigherWithPercent() {
        let outcome: [String: Double] = [
            "w1": 79, "w2": 81, "w3": 80,    // mean 80
            "o1": 49, "o2": 51, "o3": 50,    // mean 50
        ]
        let e = BehaviorInsights.effect(behaviorDays: ["w1", "w2", "w3"],
                                        askedDays: Set(outcome.keys),
                                        outcomeByDay: outcome,
                                        behavior: "Meditation", outcome: "Recovery")!
        let s = BehaviorInsights.sentence(e)
        // delta +30, pct +60% → "60% higher (avg 80 vs 50, n=3 vs 3)".
        XCTAssertEqual(s, "On days you logged ‘Meditation’, Recovery was 60% higher (avg 80 vs 50, n=3 vs 3).")
    }

    func testSentenceFallsBackToUnitsWhenPctUndefined() {
        // meanWithout 0 → pctChange nil → sentence uses absolute units.
        let outcome: [String: Double] = [
            "w1": 5, "w2": 5, "w3": 5,
            "o1": 0, "o2": 0, "o3": 0,
        ]
        let e = BehaviorInsights.effect(behaviorDays: ["w1", "w2", "w3"],
                                        askedDays: Set(outcome.keys),
                                        outcomeByDay: outcome,
                                        behavior: "X", outcome: "HRV")!
        XCTAssertNil(e.pctChange)
        let s = BehaviorInsights.sentence(e)
        XCTAssertEqual(s, "On days you logged ‘X’, HRV was 5.0 higher (avg 5 vs 0, n=3 vs 3).")
    }
}
