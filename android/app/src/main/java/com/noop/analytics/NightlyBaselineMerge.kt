package com.noop.analytics

/**
 * Seed-merge policy for the recovery baselines (#393). Kotlin twin of the Swift NightlyBaselineMerge,
 * kept byte-identical (same rule, same three drivers).
 *
 * A REAL imported (cloud) nightly value WINS per day, so a WHOOP-cloud user whose days carry genuine
 * HRV/RHR is unchanged. But a day the import left BLANK must take the on-device value: for a strap user
 * whose daily rows exist only from a Health-Connect SLEEP import (HC never carries WHOOP HRV, so every
 * one of those days is present-but-null), the on-device HRV/RHR the strap computes from raw HR has to be
 * allowed to seed the baseline, or nValid starves below Baselines.minNightsSeed and Charge never
 * calibrates. That was #393: the fill was keyed on the KEY being absent, so a present-but-null day was
 * treated as "covered" and blocked forever.
 *
 * Pure + side-effect-free (no clock, no I/O), so a fixture pins the exact merge on both platforms. The
 * HRV, RHR and resp merges all route through this so the three dominant Charge drivers stay aligned.
 */
object NightlyBaselineMerge {

    /**
     * Fill [hist] in place: any day whose current value is null — key ABSENT or present-but-null — takes
     * the matching [nightly] value; a non-null [hist] value (a real imported reading) is left untouched.
     */
    fun fillBlankNights(hist: MutableMap<String, Double?>, nightly: Map<String, Double?>) {
        for ((day, v) in nightly) if (hist[day] == null && v != null) hist[day] = v
    }
}
