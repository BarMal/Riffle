package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.settings.AppPairsMode

/**
 * Where an app pair can be launched as a split ("App pairs are Dock items", ADR 0003; docs/product/dock-modes-plan.md,
 * App pairs). A launcher can only start a split from Android SDK 37 (on SDK 36 the same launch split nothing on the
 * device that was tested), and only when its own window is wide enough for two panes and is not itself one pane of a
 * split. Where it cannot, the pair is hidden from the Dock, not removed.
 */
object PairAvailability {
    const val MIN_SDK_INT = 37
    const val MIN_WINDOW_WIDTH_DP = 600

    fun canSplit(
        sdkInt: Int,
        windowWidthDp: Int,
        inMultiWindow: Boolean,
    ): Boolean = sdkInt >= MIN_SDK_INT && windowWidthDp >= MIN_WINDOW_WIDTH_DP && !inMultiWindow

    /** Whether pairs show (and may be made and launched) under [mode], given what [canSplit] said. */
    fun available(
        mode: AppPairsMode,
        canSplit: Boolean,
    ): Boolean =
        when (mode) {
            AppPairsMode.AUTOMATIC -> canSplit
            AppPairsMode.ALWAYS_SHOW -> true
            AppPairsMode.OFF -> false
        }
}
