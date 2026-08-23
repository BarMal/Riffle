package com.riffle.app.launcher

import androidx.compose.ui.unit.LayoutDirection
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge

/**
 * A drag on the dock, read in the dock's own terms rather than the screen's.
 *
 * Two things can happen when an item is dragged: it moves to another slot, which happens *along*
 * the dock's run, or it leaves the dock for the home screen, which happens *across* it. Which
 * screen axis each of those is depends on the edge the dock sits on.
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
 * Positive is away from the dock's edge. The edges are physical and the drag deltas are physical
 * screen pixels, so this is the same on every edge regardless of layout direction -- pulling off a
 * left dock is always a move to the right, off a right dock always a move to the left.
 */
internal fun DockPosition.dragAwayFromEdgePx(
    dragXPx: Float,
    dragYPx: Float,
): Float =
    when (this) {
        DockPosition.BOTTOM -> -dragYPx
        DockPosition.TOP -> dragYPx
        DockPosition.LEFT -> dragXPx
        DockPosition.RIGHT -> -dragXPx
    }

/**
 * Whether the dock's strip renders before the workspace in a start-to-end [androidx.compose.foundation.layout.Row]
 * or [androidx.compose.foundation.layout.Column], given the layout direction.
 *
 * The edges are physical but a Row lays its children out start-to-end, which flips with the layout
 * direction; a Column does not. So a left dock leads the row in LTR and trails it in RTL, while a
 * top dock always leads its column. This keeps [DockPosition.LEFT] on the physical left either way.
 */
internal fun DockPosition.placedBeforeContent(direction: LayoutDirection): Boolean =
    when (this) {
        DockPosition.TOP -> true
        DockPosition.BOTTOM -> false
        DockPosition.LEFT -> direction == LayoutDirection.Ltr
        DockPosition.RIGHT -> direction == LayoutDirection.Rtl
    }
