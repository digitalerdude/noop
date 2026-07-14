package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #393: on-device nightly HRV/RHR must seed the recovery baseline on days the import left BLANK
 * (present-but-null), not only on days with no daily row at all. Before the fix, a strap user whose daily
 * rows came from a Health-Connect SLEEP import (avgHrv always null) had the on-device HRV blocked, so the
 * baseline starved below the 4-night seed and Charge never calibrated. Byte-identical to the Swift
 * NightlyBaselineMergeTests. Pure: no DB, no strap.
 */
class NightlyBaselineMergeTest {

    private val hrvCfg = Baselines.metricCfg.getValue("hrv")

    @Test
    fun fillBlankNights_fillsAbsentAndPresentNull_neverOverwritesReal() {
        val hist = linkedMapOf<String, Double?>(
            "2026-06-01" to 50.0,   // real imported value -> must WIN
            "2026-06-02" to null,   // present-but-null -> must be filled (the #393 case)
        )
        val nightly = mapOf<String, Double?>(
            "2026-06-01" to 99.0,   // must NOT overwrite the real 50.0
            "2026-06-02" to 40.0,   // fills the present-null day
            "2026-06-03" to 42.0,   // absent day -> added
        )
        NightlyBaselineMerge.fillBlankNights(hist, nightly)
        assertEquals(50.0, hist["2026-06-01"])   // real value preserved
        assertEquals(40.0, hist["2026-06-02"])   // present-null filled
        assertEquals(42.0, hist["2026-06-03"])   // absent filled
    }

    @Test
    fun blankImportedNights_seedFromOnDeviceHrv_crossSeed_andTrackLevel() {
        // A strap + Health-Connect user: every daily row exists (HC sleep) but carries avgHrv = null.
        val days = listOf("2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04", "2026-06-05")

        fun foldWithInjected(level: Double): BaselineState {
            val hist = LinkedHashMap<String, Double?>()
            for (d in days) hist[d] = null                 // present-but-null (HC sleep-only)
            val nightly: Map<String, Double?> = days.associateWith { level }
            NightlyBaselineMerge.fillBlankNights(hist, nightly)
            val seq = hist.entries.sortedBy { it.key }.map { it.value }
            return Baselines.foldHistory(seq, hrvCfg)
        }

        // Regression: pre-fix these 5 present-null days would have stayed null -> nValid 0 -> never usable.
        val lowBase = foldWithInjected(35.0)
        val highBase = foldWithInjected(70.0)
        assertTrue("5 filled HRV nights must cross the seed gate", lowBase.usable)
        assertTrue("5 filled HRV nights must cross the seed gate", highBase.usable)

        // Varying input (CLAUDE.md): the baseline must TRACK the injected level, not coincidentally match one.
        assertTrue(
            "baseline must track the injected HRV level (${lowBase.baseline} < ${highBase.baseline})",
            lowBase.baseline < highBase.baseline,
        )
        assertEquals(35.0, lowBase.baseline, 3.0)
        assertEquals(70.0, highBase.baseline, 3.0)

        // End-to-end: recovery is non-null once the filled baseline is usable.
        val rhrBase = Baselines.foldHistory(listOf(52.0, 51.0, 53.0, 52.0), Baselines.restingHRCfg)
        val score = RecoveryScorer.recovery(
            hrv = 35.0, rhr = 52.0, resp = null,
            hrvBaseline = lowBase, rhrBaseline = rhrBase, respBaseline = null, sleepPerf = 0.9,
        )
        assertNotNull("recovery must be non-null once the filled baseline is usable", score)
    }
}
