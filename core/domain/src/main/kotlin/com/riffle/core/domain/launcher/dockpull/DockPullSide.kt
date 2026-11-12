package com.riffle.core.domain.launcher.dockpull

/** Which side of the Dock a touch landed on: the pinned apps, or the dynamic side (live apps, now playing, Stream). */
enum class DockPullSide {
    PINNED,
    DYNAMIC,
}

/** Where a Dock pull leads. */
enum class DockPullDestination {
    /** The Library, from the pinned side (from Home or from the Stream). */
    LIBRARY,

    /** The Stream, from the dynamic side while Home is showing. */
    STREAM,

    /** Home, from the dynamic side while the Stream is showing (the same pull that opened it). */
    HOME,
}

/**
 * The side a touch landed on, from where it landed and where the dynamic section lies, all in one coordinate along the
 * Dock's run (px, any origin: the caller measures them in the same space). [dynamicStart] and [dynamicEnd] are the
 * dynamic section's extent; null means the Dock has none, so everything is the pinned side.
 *
 * The dynamic section sits at one end of the run. The touch is on its side when it is past the section's near edge
 * toward that end, so the padding at the Dock's end and the gap around the entries count as dynamic, and the divider
 * counts as pinned. Which end the section is at is read from where it lies in [dockStart]..[dockEnd], so a mirrored
 * layout needs no special case.
 */
fun dockPullSideAt(
    position: Float,
    dockStart: Float,
    dockEnd: Float,
    dynamicStart: Float?,
    dynamicEnd: Float?,
): DockPullSide {
    if (dynamicStart == null || dynamicEnd == null) return DockPullSide.PINNED
    val atEnd = (dynamicStart - dockStart) >= (dockEnd - dynamicEnd)
    val onDynamic = if (atEnd) position >= dynamicStart else position <= dynamicEnd
    return if (onDynamic) DockPullSide.DYNAMIC else DockPullSide.PINNED
}

/**
 * What a pull that started on [side] opens. The pinned side always opens the Library; the dynamic side opens the
 * Stream, or from the Stream returns to Home. No Stream (no dynamic side to start on) never gets here, but a pull
 * from the dynamic side without one leads nowhere useful, so it opens the Library like the pinned side does.
 */
fun dockPullDestinationFor(
    side: DockPullSide,
    streamShown: Boolean,
    streamAvailable: Boolean,
): DockPullDestination =
    when {
        side == DockPullSide.PINNED || !streamAvailable -> DockPullDestination.LIBRARY
        streamShown -> DockPullDestination.HOME
        else -> DockPullDestination.STREAM
    }
