package com.noop.ui

import com.noop.ui.ProfileAvatarStore.exifTransform
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The EXIF-orientation mapping behind the profile-avatar rotation fix. BitmapFactory drops the EXIF tag
 * on decode, so a portrait phone photo would render sideways; `exifTransform` re-derives the clockwise
 * rotation + horizontal flip that displays it upright. Pure (the `when` inlines the Java `static final`
 * orientation constants, so this needs no Robolectric/Android runtime).
 *
 * Constant values per android.media.ExifInterface: UNDEFINED=0, NORMAL=1, FLIP_HORIZONTAL=2,
 * ROTATE_180=3, FLIP_VERTICAL=4, TRANSPOSE=5, ROTATE_90=6, TRANSVERSE=7, ROTATE_270=8.
 */
class ProfileAvatarHelpersTest {
    @Test
    fun exifTransformMapsEveryOrientation() {
        assertEquals(0f to false, exifTransform(0))    // UNDEFINED → no change
        assertEquals(0f to false, exifTransform(1))    // NORMAL → no change
        assertEquals(0f to true, exifTransform(2))     // FLIP_HORIZONTAL
        assertEquals(180f to false, exifTransform(3))  // ROTATE_180
        assertEquals(180f to true, exifTransform(4))   // FLIP_VERTICAL (= rotate180 + flipH)
        assertEquals(90f to true, exifTransform(5))    // TRANSPOSE
        assertEquals(90f to false, exifTransform(6))   // ROTATE_90 — the common portrait-photo case
        assertEquals(270f to true, exifTransform(7))   // TRANSVERSE
        assertEquals(270f to false, exifTransform(8))  // ROTATE_270
        assertEquals(0f to false, exifTransform(42))   // unknown/garbage → no change
    }
}
