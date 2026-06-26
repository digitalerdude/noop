package com.noop.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * (#767) Mirror of the Swift StrapFutureClockTests — same threshold, same message. A strap with a
 * corrupt RTC banks records stamped far in the future; per-connection clock correction then scatters
 * them across the wrong calendar days so the dashboard stays empty even after a clean offload.
 * strapFutureClockGuide detects the implausibly-future newest banked record and returns a drain/recharge
 * recovery guide. The message must stay byte-identical with the Swift twin (date-free string).
 */
class StrapFutureClockTest {

    private val expectedGuide = "Your strap's clock is wrong: it's reporting banked data dated far in the future, so NOOP can't file your history on the right day and the dashboard stays empty even after a sync completes. Reconnecting won't fix it; the strap's clock has to reset. Fully drain the strap to 0%, charge it back to 100%, then reconnect. If it still reports future dates after that, it's a strap firmware issue for WHOOP support."

    @Test fun futureNewestSurfacesGuide() {
        val now = 1_750_000_000L
        // ~5 months ahead (the #767 reporter saw 2026-11 .. 2029-11 on a 2026 phone).
        val future = now + 150 * 86_400L
        assertEquals(expectedGuide, WhoopBleClient.strapFutureClockGuide(future, now))
    }

    @Test fun presentNewestReturnsNull() {
        val now = 1_750_000_000L
        // Newest banked record is last night — normal, no guide.
        assertNull(WhoopBleClient.strapFutureClockGuide(now - 86_400L, now))
    }

    @Test fun twoDayMarginClearsBenignSkew() {
        val now = 1_750_000_000L
        // Exactly two days ahead is NOT tripped (margin clears device/phone clock skew)...
        assertNull(WhoopBleClient.strapFutureClockGuide(now + 2 * 86_400L, now))
        // ...one second past the margin IS tripped.
        assertEquals(expectedGuide, WhoopBleClient.strapFutureClockGuide(now + 2 * 86_400L + 1, now))
    }
}
