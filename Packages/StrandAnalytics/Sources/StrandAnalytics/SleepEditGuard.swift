import Foundation

/// Pure guards for the hand-edit sleep-time pickers (#940). The reporter corrected a late-tracked
/// night's bed time from 01:06 back to 23:00; the picker kept the calendar DATE, so the "corrected"
/// bed landed on the COMING evening: a future-dated night whose staged window came back all-awake,
/// and the display merge then blanked the whole Sleep tab. Three layered rules, shared by
/// macOS/iOS (SleepTimeEditor) and Android (com.noop.analytics.SleepEditGuard, byte-for-byte twin),
/// all pure and unit-tested:
///   1. `autoCorrectedBed`: a time-only roll that lands the bed in the future, or at/after the
///      night's wake, almost always means the PREVIOUS evening; auto-decrement the date.
///   2. `isDisjoint`: a corrected window with no overlap of the night's recorded coverage needs an
///      explicit confirm ("this moves the night to a time with no recorded data"), never silent
///      acceptance.
///   3. `clampedEditWindow`: the repository belt-and-braces; no code path may persist a future or
///      inverted window even if a client UI misbehaves.
public enum SleepEditGuard {

    /// Rule 1: the cross-midnight bed auto-correct. `candidateBed` is what the picker just produced,
    /// `previousBed` is the value it held before this change (so a DELIBERATE date change, where the
    /// two sit on different calendar days, is always respected verbatim). When the change was
    /// time-only (same calendar day) and the candidate is impossible for a bed time (in the future,
    /// or at/after `originalWake`, the night's CURRENT wake), the user almost always meant the
    /// previous evening: return the candidate moved one day back, provided that lands in the past.
    /// `originalWake` is nil for the "Add a nap" picker, whose seed deliberately sits after the
    /// night's wake, so only the future test applies there.
    public static func autoCorrectedBed(previousBed: Date, candidateBed: Date, originalWake: Date?,
                                        now: Date, calendar: Calendar = .current) -> Date {
        guard calendar.isDate(candidateBed, inSameDayAs: previousBed) else { return candidateBed }
        let violates = candidateBed > now || originalWake.map { candidateBed >= $0 } == true
        guard violates,
              let decremented = calendar.date(byAdding: .day, value: -1, to: candidateBed),
              decremented <= now else { return candidateBed }
        return decremented
    }

    /// Rule 2: true when the corrected window `[newStart, newEnd)` shares NOTHING with the night's
    /// recorded coverage `[coverageStart, coverageEnd)` (unix seconds). A disjoint window has no
    /// data to stage from, so accepting it silently fabricates an all-awake phantom night; the UI
    /// must confirm the move instead.
    public static func isDisjoint(newStart: Int, newEnd: Int,
                                  coverageStart: Int, coverageEnd: Int) -> Bool {
        newEnd <= coverageStart || newStart >= coverageEnd
    }

    /// Rule 3: the persistence belt-and-braces. Caps the corrected wake at `now + slackSec` (a sleep
    /// cannot END in the future; the slack absorbs clock skew) and refuses (nil) any window that is
    /// inverted or entirely in the future once capped. The editor's own guards should make this
    /// unreachable; it exists so NO client code path can write a phantom night the display merge
    /// cannot render.
    public static func clampedEditWindow(start: Int, end: Int, now: Int,
                                         slackSec: Int = 300) -> (start: Int, end: Int)? {
        let cappedEnd = min(end, now + slackSec)
        guard cappedEnd > start else { return nil }
        return (start, cappedEnd)
    }
}
