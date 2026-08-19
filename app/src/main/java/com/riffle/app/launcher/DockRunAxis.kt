package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge

/**
 * A drag on the dock, read in the dock's own terms rather than the screen's.
 *
 * Two things can happen when an item is dragged: it moves to another slot, which happens *along*
 * the dock's run, or it leaves the dock for the home screen, which happens *across* it. Which
 * screen axis each of those is depends on the edge the dock sits on, and which direction counts as
 * "off the dock" differs between the two edges of the same axis -- and flips again in RTL, where
 * the leading edge is the right one.
 *
 * Resolving that here means the drag code downstream works in two numbers and never asks where the
 * dock is.
 */
internal fun DockPosition.dragAlongRunPx(
    dragXPx: Float,
    dragYPx: Float,
): Float = if (isHorizontalEdge) dragXPx else dragYPx

/**
 * How far the drag has moved off the dock and toward the rest of the home screen.
 *
 * Positive is away from the dock's edge, on every edge and in either layout direction, so the
 * threshold for pulling an item out is one comparison rather than four.
 */
internal fun DockPosition.dragAwayFromEdgePx(
    dragXPx: Float,
    dragYPx: Float,
    isRtl: Boolean = false,
): Float =
    when (this) {
        DockPosition.BOTTOM -> -dragYPx
        DockPosition.TOP -> dragYPx
        DockPosition.LEADING -> if (isRtl) -dragXPx else dragXPx
        DockPosition.TRAILING -> if (isRtl) dragXPx else -dragXPx
    }
