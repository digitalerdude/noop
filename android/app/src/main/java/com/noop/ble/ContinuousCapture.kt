package com.noop.ble

/**
 * How NOOP holds the dense realtime R-R ("Continuous HRV capture") stream open with no Live screen
 * visible. The realtime flood is the single biggest phone-radio + strap-battery cost NOOP incurs, and
 * its whole rationale is *overnight* HRV/recovery/sleep — so [OVERNIGHT] limits the always-on stream to
 * the sleep window, roughly halving the realtime duty cycle while keeping the benefit the feature exists
 * for. [ALWAYS] preserves the original 24/7 behaviour; [OFF] never holds it open. Behaviour twin of the
 * Swift `ContinuousCaptureMode`.
 */
enum class ContinuousCaptureMode { OFF, OVERNIGHT, ALWAYS }

/**
 * Pure decision for whether continuous capture wants the realtime stream armed right now. Clock-free:
 * the caller passes local minutes-of-day, so this stays unit-testable and is the byte-for-byte twin of
 * the Swift `ContinuousCapture`.
 */
object ContinuousCapture {
    /** Default OVERNIGHT window: 21:30 -> 09:30 local, as minutes-of-day, wrapping past midnight. Generous
     *  on both edges so a late night / long lie-in isn't clipped. Used when the user hasn't set their own
     *  window; the picker in Settings overrides it (persisted per platform). */
    const val DEFAULT_WINDOW_START_MIN = 21 * 60 + 30   // 21:30
    const val DEFAULT_WINDOW_END_MIN = 9 * 60 + 30       // 09:30

    /** Does continuous capture want the realtime stream armed at [nowMinuteOfDay] (0..1439) under [mode]?
     *  [windowStartMin]/[windowEndMin] bound the OVERNIGHT window (minutes-of-day, may wrap midnight);
     *  they default to the built-in window and are overridden by the user's Settings picker. */
    fun wantsStreamNow(
        mode: ContinuousCaptureMode,
        nowMinuteOfDay: Int,
        windowStartMin: Int = DEFAULT_WINDOW_START_MIN,
        windowEndMin: Int = DEFAULT_WINDOW_END_MIN,
    ): Boolean = when (mode) {
        ContinuousCaptureMode.OFF -> false
        ContinuousCaptureMode.ALWAYS -> true
        ContinuousCaptureMode.OVERNIGHT -> inWindow(nowMinuteOfDay, windowStartMin, windowEndMin)
    }

    /** True when [min] falls in the wrap-around window [startMin, endMin). A zero-width window
     *  (startMin == endMin) is never open, matching the app's quiet-hours convention. */
    fun inWindow(min: Int, startMin: Int, endMin: Int): Boolean =
        if (startMin <= endMin) min in startMin until endMin
        else min >= startMin || min < endMin
}
