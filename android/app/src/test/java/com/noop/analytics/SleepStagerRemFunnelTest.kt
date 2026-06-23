package com.noop.analytics

import com.noop.data.GravitySample
import com.noop.data.HrSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests SleepStager.stageFunnel — the REM-suppression funnel diagnostic. Kotlin twin of the Swift
 * SleepStagerTests REM-funnel cases.
 */
class SleepStagerRemFunnelTest {

    private fun g(ts: Long) = GravitySample("dev", ts, 0.0, 0.0, 1.0)
    private fun h(ts: Long) = HrSample("dev", ts, 55)

    @Test
    fun stageFunnelNullWhenTooLittleGravity() {
        // Same degenerate case stageSession answers with a single "light" span → diagnostic returns null.
        assertNull(SleepStager.stageFunnel(0L, 3600L, emptyList(), emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun stageFunnelCountsConsistentAndRRVStarvedWithoutRR() {
        // A 1-hour still window with dense HR but NO R-R (the WHOOP 4.0 sparse-R-R situation): the funnel
        // must come back populated, its counts internally consistent, and finiteRRVFrac == 0 — i.e. the
        // PRIMARY rem rule (which needs a finite rrv) is starved, exactly the mechanism behind 0% REM.
        val start = 0L
        val end = 3600L
        val grav = ArrayList<GravitySample>()
        val hr = ArrayList<HrSample>()
        var t = start
        while (t < end) { grav.add(g(t)); t += 30 }
        t = start
        while (t < end) { hr.add(h(t)); t += 2 }

        val f = SleepStager.stageFunnel(start, end, grav, hr, emptyList(), emptyList())
            ?: error("a stageable window must return a funnel")
        assertTrue(f.epochs > 0)
        assertTrue(f.remFinal <= f.remSmoothed)   // reimposePhysiology only demotes REM -> light
        assertTrue(f.remRaw >= 0)
        assertTrue(f.remSmoothed <= f.epochs)
        assertEquals(0.0, f.finiteRRVFrac, 1e-9)  // no R-R supplied -> rrv NaN everywhere
        assertTrue(f.finiteRRVFrac in 0.0..1.0)
        assertTrue(f.finiteHRVarFrac in 0.0..1.0)
    }
}
