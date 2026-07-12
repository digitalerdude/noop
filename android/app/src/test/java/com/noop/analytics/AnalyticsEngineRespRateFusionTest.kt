package com.noop.analytics

import com.noop.data.GravitySample
import com.noop.data.HrSample
import com.noop.data.PpgRespSample
import com.noop.data.RrInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.sin

/**
 * [AnalyticsEngine.analyzeDay] end-to-end: the prefer-PPG-else-RSA respiratory-rate fusion (#103).
 * Mirrors the Swift AnalyticsEngineTests.testAnalyzeDayPrefersPpgRespRateOverRsaWhenAvailable /
 * testAnalyzeDayFallsBackToRsaWhenPpgCoverageIsInsufficient.
 */
class AnalyticsEngineRespRateFusionTest {
    private val deviceId = "my-whoop"

    /** A [hours]-long night ending at 06:00 UTC on [endDay], with steady HR=50 + still gravity, and
     *  an R-R stream RSA-modulated to recover a planted 15 breaths/min. */
    private fun night(endDay: String, hours: Int): Triple<Long, Long, Pair<List<HrSample>, List<GravitySample>>> {
        val dayMidnight = java.time.LocalDate.parse(endDay).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
        val end = dayMidnight + 6 * 3600
        val start = end - hours * 3600L
        val hr = (start until end).map { HrSample(deviceId = deviceId, ts = it, bpm = 50) }
        val grav = (start until end).map { GravitySample(deviceId = deviceId, ts = it, x = 0.0, y = 0.0, z = 1.0) }
        return Triple(start, end, hr to grav)
    }

    private fun plantedRr(start: Long, end: Long): List<RrInterval> {
        val rr = ArrayList<RrInterval>()
        var tSec = 0.0
        while (tSec < (end - start).toDouble()) {
            val rrMs = 1200.0 + 40.0 * sin(2.0 * Math.PI * 0.25 * tSec) // planted 15 bpm
            tSec += rrMs / 1000.0
            rr.add(RrInterval(deviceId = deviceId, ts = start + tSec.toLong(), rrMs = rrMs.toInt()))
        }
        return rr
    }

    @Test
    fun analyzeDayPrefersPpgRespRateOverRsaWhenAvailable() {
        val day = "2021-06-21"
        val (start, end, hg) = night(day, 7)
        val (hr, grav) = hg
        val rr = plantedRr(start, end)
        // 12 high-confidence PPG bursts (well above minPpgBurstsForEstimate) spread across the night,
        // all agreeing on 12 bpm — clearly distinct from RSA's planted 15 bpm above.
        val ppgResp = (0 until 12).map {
            PpgRespSample(deviceId = deviceId, ts = start + it * 600, bpm = 12.0, conf = 20.0)
        }
        val result = AnalyticsEngine.analyzeDay(
            day = day, hr = hr, rr = rr, gravity = grav, ppgResp = ppgResp, profile = UserProfile(),
        )
        assertEquals(1, result.sleepSessions.size)
        val resp = assertNotNullAndGet(result.daily.respRateBpm)
        assertEquals(
            "expected the PPG estimate (12 bpm) to be preferred over RSA's planted 15 bpm",
            12.0, resp, 0.5,
        )
    }

    @Test
    fun analyzeDayFallsBackToRsaWhenPpgCoverageIsInsufficient() {
        val day = "2021-06-21"
        val (start, end, hg) = night(day, 7)
        val (hr, grav) = hg
        val rr = plantedRr(start, end)
        // Only 3 PPG bursts (well below minPpgBurstsForEstimate) — must not override RSA.
        val sparsePpgResp = (0 until 3).map {
            PpgRespSample(deviceId = deviceId, ts = start + it * 600, bpm = 12.0, conf = 20.0)
        }
        val result = AnalyticsEngine.analyzeDay(
            day = day, hr = hr, rr = rr, gravity = grav, ppgResp = sparsePpgResp, profile = UserProfile(),
        )
        val resp = assertNotNullAndGet(result.daily.respRateBpm)
        assertEquals(
            "expected fallback to the RSA estimate (~15 bpm) when PPG coverage is too sparse",
            15.0, resp, 3.0,
        )
    }

    private fun assertNotNullAndGet(v: Double?): Double {
        assertNotNull(v)
        return v!!
    }
}
