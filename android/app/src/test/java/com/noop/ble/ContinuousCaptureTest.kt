package com.noop.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirror of the Swift ContinuousCaptureTests: same window, same expectations. */
class ContinuousCaptureTest {

    private val m = ContinuousCaptureMode.OVERNIGHT

    @Test fun offIsNeverArmed() {
        assertFalse(ContinuousCapture.wantsStreamNow(ContinuousCaptureMode.OFF, 3 * 60))
        assertFalse(ContinuousCapture.wantsStreamNow(ContinuousCaptureMode.OFF, 23 * 60))
    }

    @Test fun alwaysIsAlwaysArmed() {
        assertTrue(ContinuousCapture.wantsStreamNow(ContinuousCaptureMode.ALWAYS, 3 * 60))
        assertTrue(ContinuousCapture.wantsStreamNow(ContinuousCaptureMode.ALWAYS, 15 * 60))
    }

    @Test fun overnightWrapsMidnight() {
        assertTrue(ContinuousCapture.wantsStreamNow(m, 23 * 60))          // 23:00 in
        assertTrue(ContinuousCapture.wantsStreamNow(m, 0))               // 00:00 in
        assertTrue(ContinuousCapture.wantsStreamNow(m, 3 * 60))          // 03:00 in
        assertTrue(ContinuousCapture.wantsStreamNow(m, 9 * 60 + 29))     // 09:29 in
    }

    @Test fun overnightExcludesDaytime() {
        assertFalse(ContinuousCapture.wantsStreamNow(m, 9 * 60 + 30))    // 09:30 out (exclusive end)
        assertFalse(ContinuousCapture.wantsStreamNow(m, 12 * 60))        // 12:00 out
        assertFalse(ContinuousCapture.wantsStreamNow(m, 21 * 60 + 29))   // 21:29 out
    }

    @Test fun overnightStartIsInclusive() {
        assertTrue(ContinuousCapture.wantsStreamNow(m, ContinuousCapture.DEFAULT_WINDOW_START_MIN))   // 21:30 in
    }

    @Test fun customWrappingWindow() {
        // 23:00 -> 06:00 custom window.
        val s = 23 * 60; val e = 6 * 60
        assertTrue(ContinuousCapture.wantsStreamNow(m, 23 * 60, s, e))      // 23:00 in
        assertTrue(ContinuousCapture.wantsStreamNow(m, 5 * 60 + 59, s, e))  // 05:59 in
        assertFalse(ContinuousCapture.wantsStreamNow(m, 6 * 60, s, e))      // 06:00 out
        assertFalse(ContinuousCapture.wantsStreamNow(m, 22 * 60, s, e))     // 22:00 out
    }

    @Test fun customNonWrappingWindow() {
        // 13:00 -> 14:00 daytime window (start <= end, no wrap).
        val s = 13 * 60; val e = 14 * 60
        assertTrue(ContinuousCapture.wantsStreamNow(m, 13 * 60 + 30, s, e)) // 13:30 in
        assertFalse(ContinuousCapture.wantsStreamNow(m, 12 * 60, s, e))     // 12:00 out
        assertFalse(ContinuousCapture.wantsStreamNow(m, 14 * 60, s, e))     // 14:00 out (exclusive)
    }

    @Test fun zeroWidthWindowIsNeverOpen() {
        assertFalse(ContinuousCapture.wantsStreamNow(m, 8 * 60, 8 * 60, 8 * 60))
    }
}
