package com.riffle.core.domain.launcher.split

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

/**
 * What Home and the Stream show on an expanded posture (ADR 0003, Folded and unfolded): Home alone, both side by side
 * (or one over the other) with a movable divider, or the Stream alone. Folded and phone postures only ever use
 * [HOME_ONLY] and [STREAM_ONLY], the full switch.
 *
 * The shell knows only "Stream shown or not"; which of [SPLIT] and [STREAM_ONLY] a shown Stream is, is the user's
 * last choice on that posture (`StreamSplit.lastShown`).
 */
enum class SplitMode(val storedId: String) {
    HOME_ONLY("home"),
    SPLIT("split"),
    STREAM_ONLY("stream"),
    ;

    /** Whether the Stream is on screen at all. */
    val showsStream: Boolean get() = this != HOME_ONLY

    /** Whether Home is on screen at all. */
    val showsHome: Boolean get() = this != STREAM_ONLY

    companion object {
        fun fromStoredId(id: String?): SplitMode? = entries.firstOrNull { it.storedId == id }
    }
}

/**
 * The pure transitions between [SplitMode]s. The Dock press toggles Home only and the last-used "Stream shown" state
 * (split or Stream only); the Home press always returns to Home only; a divider or pull that ends in a shown state
 * becomes the new last-used one.
 */
object SplitModes {
    /** The mode a shown Stream takes: the last-used one, or Stream only where there is no room for a split. */
    fun shownMode(
        lastShown: SplitMode,
        splitAvailable: Boolean,
    ): SplitMode = if (lastShown == SplitMode.SPLIT && splitAvailable) SplitMode.SPLIT else SplitMode.STREAM_ONLY

    /** The mode the shell's "Stream shown" answer means on an expanded posture. */
    fun modeOf(
        streamShown: Boolean,
        lastShown: SplitMode,
        splitAvailable: Boolean,
    ): SplitMode = if (streamShown) shownMode(lastShown, splitAvailable) else SplitMode.HOME_ONLY

    /** Pressing the Dock from [current]: Home only goes to the last-used shown mode, any other to Home only. */
    fun afterStreamButton(
        current: SplitMode,
        lastShown: SplitMode,
        splitAvailable: Boolean,
    ): SplitMode = if (current == SplitMode.HOME_ONLY) shownMode(lastShown, splitAvailable) else SplitMode.HOME_ONLY

    /** Pressing Home. */
    @Suppress("UnusedParameter")
    fun afterHomePress(current: SplitMode): SplitMode = SplitMode.HOME_ONLY

    /** The last-used shown mode after the user ended in [mode]: Home only keeps whatever was remembered. */
    fun lastShownAfter(
        mode: SplitMode,
        previous: SplitMode,
    ): SplitMode = if (mode == SplitMode.HOME_ONLY) previous.asShownMode() else mode

    /** Home only is not a shown mode; a stored or computed value of it reads as the default, a split. */
    fun SplitMode.asShownMode(): SplitMode = if (this == SplitMode.HOME_ONLY) SplitMode.SPLIT else this
}

/** Whether a posture is wide enough for Home and the Stream to share the screen: unfolded, tablet and desktop. */
fun HomeLayoutDeviceClass.supportsSplit(): Boolean =
    when (this) {
        HomeLayoutDeviceClass.FOLDABLE,
        HomeLayoutDeviceClass.TABLET,
        HomeLayoutDeviceClass.DESKTOP,
        -> true
        HomeLayoutDeviceClass.PHONE,
        HomeLayoutDeviceClass.PHONE_LANDSCAPE,
        -> false
    }
