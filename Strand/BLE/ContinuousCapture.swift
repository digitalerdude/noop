import Foundation

/// How NOOP holds the dense realtime R-R ("Continuous HRV capture") stream open with no Live screen
/// visible. The realtime flood is the single biggest phone-radio + strap-battery cost NOOP incurs, and
/// its whole rationale is *overnight* HRV/recovery/sleep — so `.overnight` limits the always-on stream to
/// the sleep window, roughly halving the realtime duty cycle while keeping the benefit the feature exists
/// for. `.always` preserves the original 24/7 behaviour; `.off` never holds it open. Behaviour twin of the
/// Android `ContinuousCaptureMode`.
public enum ContinuousCaptureMode: Equatable, Sendable { case off, overnight, always }

/// Pure decision for whether continuous capture wants the realtime stream armed right now. Clock-free:
/// the caller passes local minutes-of-day, so this stays unit-testable and is the twin of the Android
/// `ContinuousCapture`.
public enum ContinuousCapture {
    /// Default `.overnight` window: 21:30 -> 09:30 local, as minutes-of-day, wrapping past midnight.
    /// Generous on both edges so a late night / long lie-in isn't clipped. Used when the user hasn't set
    /// their own window; the picker in Settings overrides it (persisted per platform).
    public static let defaultWindowStartMin = 21 * 60 + 30   // 21:30
    public static let defaultWindowEndMin = 9 * 60 + 30       // 09:30

    /// Does continuous capture want the realtime stream armed at `nowMinuteOfDay` (0..1439) under `mode`?
    /// `windowStartMin`/`windowEndMin` bound the `.overnight` window (minutes-of-day, may wrap midnight);
    /// they default to the built-in window and are overridden by the user's Settings picker.
    public static func wantsStreamNow(_ mode: ContinuousCaptureMode,
                                      nowMinuteOfDay: Int,
                                      windowStartMin: Int = defaultWindowStartMin,
                                      windowEndMin: Int = defaultWindowEndMin) -> Bool {
        switch mode {
        case .off: return false
        case .always: return true
        case .overnight: return inWindow(nowMinuteOfDay, startMin: windowStartMin, endMin: windowEndMin)
        }
    }

    /// True when `min` falls in the wrap-around window [startMin, endMin). A zero-width window
    /// (startMin == endMin) is never open, matching the app's quiet-hours convention.
    public static func inWindow(_ min: Int, startMin: Int, endMin: Int) -> Bool {
        startMin <= endMin
            ? (min >= startMin && min < endMin)
            : (min >= startMin || min < endMin)
    }
}
