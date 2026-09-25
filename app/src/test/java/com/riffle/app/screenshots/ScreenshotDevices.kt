package com.riffle.app.screenshots

/**
 * Robolectric device qualifiers for the screenshot matrix (#1197).
 *
 * Plain strings rather than a device catalogue so each posture's size is visible where it is used,
 * and so a new posture is one line here plus a `@Config(qualifiers = ...)` on a test.
 *
 * Robolectric cannot report a folding feature, so the foldable and tabletop entries only size the
 * window; the tests that need a hinge or posture pass an explicit
 * [com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout] alongside, the way MainActivity
 * does from Jetpack WindowManager on a real device.
 */
internal object ScreenshotDevices {
    /** A current mid-size phone held upright (Pixel 7 class). */
    const val COMPACT_PHONE = "w411dp-h914dp-normal-long-notround-any-420dpi-keyshidden-nonav"

    /** A book-style foldable opened flat, e.g. a Pixel Fold / Galaxy Z Fold inner display. */
    const val UNFOLDED_FOLDABLE = "w840dp-h900dp-large-notlong-notround-any-xhdpi-keyshidden-nonav"

    /** The same foldable turned so its fold runs across the screen, half-open on a table. */
    const val TABLETOP_FOLDABLE = "w900dp-h840dp-large-notlong-notround-any-xhdpi-keyshidden-nonav"

    /** A 10-11 inch tablet in landscape. */
    const val TABLET_LANDSCAPE = "w1280dp-h800dp-xlarge-notlong-notround-any-xhdpi-keyshidden-nonav"

    /** Appended to the class-level [COMPACT_PHONE] qualifiers for the dark-theme variant. */
    const val NIGHT = "+night"

    /** Font scale for the large-text variant: Android's largest built-in setting on API 34. */
    const val LARGE_FONT_SCALE = 2.0f

    /**
     * Pinned rather than taken from targetSdk, so the rendering (and the dynamic Material colours
     * Robolectric's framework resources resolve to) does not move when targetSdk is bumped, and so
     * the tests run on a JDK 17 toolchain (Robolectric's SDK 35 needs JDK 21).
     */
    const val SDK = 34
}
