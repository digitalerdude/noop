import Foundation

/// Seed-merge policy for the recovery baselines (#393). Swift twin of the Kotlin NightlyBaselineMerge,
/// kept byte-identical (same rule, same three drivers).
///
/// A REAL imported (cloud) nightly value WINS per day, so a WHOOP-cloud user whose days carry genuine
/// HRV/RHR is unchanged. But a day the import left BLANK must take the on-device value: for a strap user
/// whose daily rows exist only from a Health-Connect SLEEP import (HC never carries WHOOP HRV, so every one
/// of those days is present-but-nil), the on-device HRV/RHR the strap computes from raw HR has to be allowed
/// to seed the baseline, or nValid starves below `Baselines.minNightsSeed` and Charge never calibrates. That
/// was #393: the fill keyed on the KEY being absent (`dict[day] == nil` is true only for a missing key on a
/// `[String: Double?]`), so a present-but-nil day was treated as "covered" and blocked forever.
///
/// Pure + side-effect-free (no clock, no I/O), so a fixture pins the exact merge on both platforms. The HRV,
/// RHR and resp merges all route through this so the three dominant Charge drivers stay aligned.
public enum NightlyBaselineMerge {

    /// Fill `hist` in place: any day whose current value is nil — key ABSENT or present-but-nil — takes the
    /// matching `nightly` value; a non-nil `hist` value (a real imported reading) is left untouched.
    public static func fillBlankNights(_ hist: inout [String: Double?], _ nightly: [String: Double?]) {
        for (day, v) in nightly {
            guard (hist[day] ?? nil) == nil, let value = v else { continue }
            hist[day] = value
        }
    }
}
