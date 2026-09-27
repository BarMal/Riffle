package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import kotlin.math.roundToInt

/** [this] translated from root coordinates into a space whose origin is [originInRoot]. */
internal fun Rect.relativeTo(originInRoot: Offset): Rect = translate(-originInRoot)

/**
 * Where a mode's dock-edge companion -- Cards' stage-identity pill and pin/overflow capsule
 * (replacing the floating header the design-review pass flagged as a redundant, disconnected copy
 * of what the dock already shows) -- sits relative to the dock's own measured strip: just outside
 * it along whichever edge the dock runs, never over its own icon run and never past the container
 * it is laid out in.
 *
 * A horizontal dock (top/bottom) reserves height across the whole width it is given, so its
 * companion sits above or below the strip, centred on the strip's own horizontal midpoint. A
 * vertical dock (left/right) reserves width but is only ever as tall as it needs to be -- it is
 * vertically centred within the room [HomeDockHost] gives it, not stretched full-height (see
 * `DockPosition.dockHostAlignment`) -- so its companion sits beside the strip instead of above or
 * below it, where it can never land over the strip's own icon run regardless of how tall that run
 * is: vertically centred on the strip's own vertical midpoint.
 */
internal fun dockEdgeCompanionOffset(
    containerSize: IntSize,
    dockBoundsLocal: Rect,
    position: DockPosition,
    companionSize: IntSize,
    gapPx: Int,
): IntOffset {
    val x: Float
    val y: Float
    if (position.isHorizontalEdge) {
        x = dockBoundsLocal.center.x - companionSize.width / 2f
        y =
            if (position == DockPosition.TOP) {
                dockBoundsLocal.bottom + gapPx
            } else {
                dockBoundsLocal.top - gapPx - companionSize.height
            }
    } else {
        y = dockBoundsLocal.center.y - companionSize.height / 2f
        x =
            if (position == DockPosition.LEFT) {
                dockBoundsLocal.right + gapPx
            } else {
                dockBoundsLocal.left - gapPx - companionSize.width
            }
    }
    val maxX = (containerSize.width - companionSize.width).toFloat().coerceAtLeast(0f)
    val maxY = (containerSize.height - companionSize.height).toFloat().coerceAtLeast(0f)
    return IntOffset(x.coerceIn(0f, maxX).roundToInt(), y.coerceIn(0f, maxY).roundToInt())
}

/**
 * Hosts a mode's dock-edge companion content (see [dockEdgeCompanionOffset]) inside
 * [HomeDockHost]'s own edge-aligned box: measured freely (its own wrap size, not the container's),
 * then placed with the pure offset math above. [content] must emit exactly one top-level
 * composable -- Cards' companion is a single Row (horizontal edge) or Column (vertical edge)
 * wrapping its two pills -- since only the first measured child is placed.
 */
@Composable
internal fun DockEdgeCompanionSlot(
    position: DockPosition,
    dockBoundsLocal: Rect,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeable = measurables.firstOrNull()?.measure(loose)
        val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val gapPx = DOCK_EDGE_COMPANION_GAP_DP.dp.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable?.let {
                val companionSize = IntSize(it.width, it.height)
                val offset = dockEdgeCompanionOffset(containerSize, dockBoundsLocal, position, companionSize, gapPx)
                it.place(offset)
            }
        }
    }
}

/** The gap left between the dock strip and its companion pills, so they read as two things, not one. */
private const val DOCK_EDGE_COMPANION_GAP_DP = 8
