package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Faithful Kotlin port of
 * Packages/StrandAnalytics/Tests/StrandAnalyticsTests/EffectRankerTests.swift.
 * Same fixtures, same numbers — cross-platform parity is the contract.
 */
class EffectRankerTest {

    private fun ymd(y: Int, m: Int, d: Int): String = "%04d-%02d-%02d".format(y, m, d)

    private fun row(rows: List<RankedEffect>, behavior: String): RankedEffect? =
        rows.firstOrNull { it.behavior == behavior }

    /** Deterministic per-calendar-day jitter in {-2,-1,0,1,2}, mirroring the Swift fixture, so
     *  with/without groups carry real spread (a constant group yields pooled SD 0 / d 0). */
    private fun jitter(dayOfMonth: Int): Double = ((dayOfMonth * 7) % 5 - 2).toDouble()

    /** Test-only: the BehaviorEffect at a specific lag, via the engine's own shift alignment. */
    private fun effectAtLag(behaviorDays: Set<String>, askedDays: Set<String>, outcome: Map<String, Double>, lag: Int) =
        EffectRanker.effect(behaviorDays, askedDays, EffectRanker.shiftedOutcome(outcome, lag), "Alcohol", "Charge")

    // Planted lag-1 effect is found at L=1 and beats L=0/L=2

    @Test
    fun plantedLag1IsFoundAndWins() {
        val outcome = HashMap<String, Double>()
        val behaviorDays = HashSet<String>()

        // Anchors Jun 1,5,9,13,17,21 (6, spaced 4 apart).
        for (i in 0 until 6) behaviorDays.add(ymd(2026, 6, 1 + 4 * i))
        for (d in 1..30) outcome[ymd(2026, 6, d)] = 70.0 + jitter(d)
        for (d in 1..8) outcome[ymd(2026, 7, d)] = 70.0 + jitter(d)
        for (i in 0 until 6) {
            val dip = 2 + 4 * i
            outcome[ymd(2026, 6, dip)] = 50.0 + jitter(dip)
        }
        val askedDays = outcome.keys.toSet()   // reproduce old semantics: every outcome day was "asked"

        val out = EffectRanker.rank(mapOf("Alcohol" to behaviorDays), mapOf("Alcohol" to askedDays), outcome, "Charge")
        val r = row(out, "Alcohol")
        assertNotNull(r)
        assertEquals(1, r!!.lag)
        assertEquals("next morning", r.leadLagText)
        assertTrue(r.effect.cohensD < 0)
        assertTrue(r.effect.significant)
        assertTrue(r.effect.meanWith < 55)
        assertTrue(r.effect.meanWithout > 65)

        val d1 = abs(r.effect.cohensD)
        val d0 = abs(effectAtLag(behaviorDays, askedDays, outcome, 0)!!.cohensD)
        val d2 = abs(effectAtLag(behaviorDays, askedDays, outcome, 2)!!.cohensD)
        assertTrue(d1 > d0)
        assertTrue(d1 > d2)
    }

    // askedDays membership does not shift with the lag (#631)

    @Test
    fun askedDaysNotShiftedAcrossLags() {
        val outcome = HashMap<String, Double>()
        for (d in 1..40) outcome[ymd(2026, 6, d)] = 70.0 + jitter(d)
        val behaviorDays = HashSet<String>()
        val askedNo = HashSet<String>()
        for (i in 0 until 5) {
            behaviorDays.add(ymd(2026, 6, 1 + 6 * i))     // 1,7,13,19,25
            askedNo.add(ymd(2026, 6, 3 + 6 * i))          // 3,9,15,21,27
        }
        val askedDays = behaviorDays + askedNo

        for (lag in EffectRanker.lagSet) {
            val shifted = EffectRanker.shiftedOutcome(outcome, lag)
            val e = EffectRanker.effect(behaviorDays, askedDays, shifted, "X", "Charge")!!
            assertEquals("lag $lag", 5, e.nWith)
            assertEquals("lag $lag", 5, e.nWithout)   // must stay 5, not shift with the lag
        }
    }

    // effect() partition — never-asked days (#631: a missing journal answer is not an implicit "no")

    @Test
    fun effectExcludesUnaskedDaysFromBothGroups() {
        // "z" has an outcome value but was never asked (not in behaviorDays or askedDays) →
        // dropped entirely, not counted as "without".
        val outcome = mapOf("a" to 60.0, "b" to 62.0, "c" to 70.0, "d" to 72.0, "z" to 65.0)
        val e = EffectRanker.effect(setOf("a", "b"), setOf("a", "b", "c", "d"), outcome, "X", "Recovery")!!
        assertEquals(2, e.nWith)      // a, b
        assertEquals(2, e.nWithout)   // c, d — "z" excluded, not counted as without
    }

    @Test
    fun effectAskedNoStillCountsAsWithout() {
        // A day that WAS asked and explicitly answered "no" still counts as "without" — the fix
        // only excludes NEVER-asked days.
        val outcome = mapOf("a" to 60.0, "b" to 61.0, "c" to 70.0, "d" to 71.0, "e" to 72.0)
        val e = EffectRanker.effect(setOf("a", "b"), setOf("a", "b", "c", "d", "e"), outcome, "X", "Recovery")!!
        assertEquals(2, e.nWith)
        assertEquals(3, e.nWithout)
    }

    @Test
    fun effectBehaviorDayNotInAskedStillCountsAsWith() {
        // Defensive OR: a "yes" day missing from askedDays (a caller build gap) is never dropped
        // from "with".
        val outcome = mapOf("a" to 80.0, "b" to 60.0, "c" to 61.0)
        val e = EffectRanker.effect(setOf("a"), setOf("b", "c"), outcome, "X", "Recovery")!!
        assertEquals(1, e.nWith)      // "a" still counted despite the askedDays gap
        assertEquals(2, e.nWithout)
    }

    @Test
    fun effectRealisticSparseJournalQuestion() {
        // #631 shape: a rarely-asked question logged "yes" on 3 days and "no" on 2 days, out of
        // 300 total outcome-days. The other 295 days must be excluded, not lumped into "without".
        val outcome = HashMap<String, Double>()
        for (i in 0 until 300) outcome["d$i"] = (60 + i % 10).toDouble()
        val behaviorDays = setOf("d0", "d1", "d2")
        val askedDays = behaviorDays + setOf("d3", "d4")
        val e = EffectRanker.effect(behaviorDays, askedDays, outcome, "Travelled in a car or train?", "Charge")!!
        assertEquals(3, e.nWith)
        assertEquals(2, e.nWithout)   // NOT 297
    }

    // Group gate suppresses thin behaviours

    @Test
    fun thinBehaviourIsDropped() {
        val outcome = HashMap<String, Double>()
        val thin = HashSet<String>()
        for (d in 1..3) {
            val day = ymd(2026, 6, d)
            thin.add(day)
            outcome[day] = 50.0 + jitter(d)
            outcome[ymd(2026, 6, d + 1)] = 50.0 + jitter(d + 1)
        }
        for (d in 1..8) outcome[ymd(2026, 7, d)] = 70.0 + jitter(d)

        val out = EffectRanker.rank(mapOf("Sparse" to thin), mapOf("Sparse" to outcome.keys.toSet()), outcome, "Charge")
        assertTrue(out.isEmpty())
    }

    // Ranking order matches BehaviorInsights.rank

    @Test
    fun rankingOrder() {
        val outcome = HashMap<String, Double>()
        val big = HashSet<String>()
        for (d in 1..6) {
            val day = ymd(2026, 1, d)
            big.add(day)
            outcome[day] = 50.0 + jitter(d)
        }
        val small = HashSet<String>()
        for (d in 1..6) {
            val day = ymd(2026, 3, d)
            small.add(day)
            outcome[day] = 66.0 + jitter(d)
        }
        for (d in 10..20) outcome[ymd(2026, 5, d)] = 70.0 + jitter(d)
        val askedDays = outcome.keys.toSet()

        val out = EffectRanker.rank(
            mapOf("Big" to big, "Small" to small),
            mapOf("Big" to askedDays, "Small" to askedDays),
            outcome, "Charge",
        )
        assertEquals(listOf("Big", "Small"), out.map { it.behavior })
        assertEquals(0, row(out, "Big")!!.lag)
        assertEquals(0, row(out, "Small")!!.lag)
    }

    // Confidence tiers from paired-day count

    @Test
    fun confidenceTiers() {
        assertEquals(ScoreConfidence.CALIBRATING, EffectRanker.confidence(4))
        assertEquals(ScoreConfidence.BUILDING, EffectRanker.confidence(5))
        assertEquals(ScoreConfidence.BUILDING, EffectRanker.confidence(9))
        assertEquals(ScoreConfidence.SOLID, EffectRanker.confidence(10))
    }

    // shiftedOutcome alignment

    @Test
    fun shiftedOutcomeAlignment() {
        val outcome = mapOf(ymd(2026, 6, 2) to 55.0, ymd(2026, 6, 3) to 60.0)
        assertEquals(outcome, EffectRanker.shiftedOutcome(outcome, 0))
        val s1 = EffectRanker.shiftedOutcome(outcome, 1)
        assertEquals(55.0, s1[ymd(2026, 6, 1)])
        assertEquals(60.0, s1[ymd(2026, 6, 2)])
        assertNull(s1[ymd(2026, 6, 3)])
    }

    // sentence appends the lead/lag clause

    @Test
    fun sentenceAppendsLeadLag() {
        val e = BehaviorEffect(
            behavior = "Alcohol", outcome = "Charge",
            meanWith = 50.0, meanWithout = 70.0, delta = -20.0,
            pctChange = -100.0 * 20.0 / 70.0, nWith = 6, nWithout = 8,
            cohensD = -2.0, pApprox = 0.001, significant = true,
        )
        val r = RankedEffect("Alcohol", "Charge", 1, e, ScoreConfidence.BUILDING)
        assertTrue(r.sentence().endsWith("(next morning)."))
    }
}
