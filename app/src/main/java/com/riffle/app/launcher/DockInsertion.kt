package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge

/**
 * The slot a finger at [pointInRoot] over the Dock ([dockBounds]) hovers, as an insertion index among the pinned items
 * lying at [slots] (in the Dock's order, in the root): the number of items whose centre the finger has passed along the
 * run, so the item lands between its neighbours where it hovers. Null when the finger is not over the Dock.
 */
internal fun dockInsertionIndexAmong(
    pointInRoot: Offset,
    dockBounds: Rect?,
    slots: List<Rect>,
    isRtl: Boolean,
    position: DockPosition,
): Int? {
    if (dockBounds == null || !dockBounds.contains(pointInRoot)) return null
    val horizontal = position.isHorizontalEdge
    val along = if (horizontal) pointInRoot.x else pointInRoot.y
    val mirrored = isRtl && horizontal
    return slots.count { slot ->
        val centre = if (horizontal) slot.center.x else slot.center.y
        if (mirrored) centre > along else centre < along
    }
}

/**
 * The slot a finger at [pointInRoot] over the Dock ([dockBounds]) hovers, as an insertion index among [itemCount]
 * pinned
 * items (0..itemCount): how far along the run the finger is, as a fraction of the Dock, across the slots one more than
 * there are items. Null when the finger is not over the Dock. Slots run down a side Dock, so the fraction comes from y
 * there and from x on a horizontal edge; only the horizontal run mirrors in right-to-left.
 */
internal fun dockInsertionIndex(
    pointInRoot: Offset,
    dockBounds: Rect?,
    itemCount: Int,
    isRtl: Boolean,
    position: DockPosition,
): Int? {
    val bounds = dockBounds?.takeIf { it.width > 0f && it.height > 0f && it.contains(pointInRoot) } ?: return null
    val physicalFraction =
        if (position.isHorizontalEdge) {
            ((pointInRoot.x - bounds.left) / bounds.width).coerceIn(0f, 1f)
        } else {
            ((pointInRoot.y - bounds.top) / bounds.height).coerceIn(0f, 1f)
        }
    val logicalFraction = if (isRtl && position.isHorizontalEdge) 1f - physicalFraction else physicalFraction
    return (logicalFraction * (itemCount + 1)).toInt().coerceIn(0, itemCount)
}
