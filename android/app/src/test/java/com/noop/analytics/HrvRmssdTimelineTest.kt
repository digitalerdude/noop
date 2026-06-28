package com.noop.analytics

import com.noop.data.RrInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Intraday rMSSD timeline (#803): the Deep Timeline "HRV" series must show real HRV variability (rMSSD per
 * time window), not the raw R-R tachogram. Faithful twin of Swift HRVRmssdTimelineTests, so the two
 * platforms compute the intraday HRV identically.
 */
class HrvRmssdTimelineTest {

    private fun rr(ts: Long, ms: Int) = RrInterval(deviceId = "d", ts = ts, rrMs = ms)

    /** 25 beats alternating 800/810 ms in one 300 s window -> every successive |delta| = 10 -> rMSSD = 10. */
    @Test fun computesPerWindowRmssdFromCleanRR() {
        val series = (0 until 25).map { rr(it.toLong(), if (it % 2 == 0) 800 else 810) }
        val pts = HrvAnalyzer.rmssdTimeline(series, 300L)
        assertEquals(1, pts.size)
        assertEquals(10.0, pts.first().second, 0.5)
    }

    /** A single out-of-range artifact (2400 ms) is range-filtered before rMSSD, so it must not inflate it. */
    @Test fun artifactDoesNotInflateRmssd() {
        val series = (0 until 25).map { rr(it.toLong(), if (it == 12) 2400 else if (it % 2 == 0) 800 else 810) }
        val pts = HrvAnalyzer.rmssdTimeline(series, 300L)
        assertEquals(1, pts.size)
        assertTrue(pts.first().second < 50.0)
    }

    /** Fewer than MIN_BEATS clean intervals in a window -> no point (an honest gap, not a fake value). */
    @Test fun sparseWindowProducesNoPoint() {
        val series = (0 until 5).map { rr(it.toLong(), 800) }
        assertTrue(HrvAnalyzer.rmssdTimeline(series, 300L).isEmpty())
    }

    /** Beats in two different windows produce one rMSSD point per window, ascending by ts. */
    @Test fun separateWindowsProduceOnePointEach() {
        val w1 = (0 until 25).map { rr(it.toLong(), if (it % 2 == 0) 800 else 810) }
        val w2 = (0 until 25).map { rr(300L + it, if (it % 2 == 0) 900 else 920) }
        val pts = HrvAnalyzer.rmssdTimeline(w1 + w2, 300L)
        assertEquals(2, pts.size)
        assertTrue(pts.first().first < pts.last().first)
    }

    /** A window where >35% of beats were noise is dropped (spot honesty gate #585), even with MIN_BEATS clean. */
    @Test fun windowDominatedByNoiseIsDropped() {
        val series = (0 until 25).map { rr(it.toLong(), if (it % 2 == 0) 800 else 810) } +
            (25 until 60).map { rr(it.toLong(), 2400) } // 35/60 = 58% rejected > 35% gate
        assertTrue(HrvAnalyzer.rmssdTimeline(series, 300L).isEmpty())
    }
}
