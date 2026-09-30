package com.riffle.core.domain.launcher.gestures

import com.riffle.core.domain.launcher.workspace.GestureAxis
import kotlin.math.abs

/** The screen axis a drag mostly travels along. */
enum class DragAxis {
    HORIZONTAL,
    VERTICAL,
}

/**
 * What the home gesture layer derives from the axes the containers under it declare
 * (`ExpressionCatalog` descriptors, combined by `AxisDeclarations`), replacing per-surface special cases
 * such as the hand-written `overscrollHandOff = true` of Cards mode.
 *
 * Declarations say what a container *can* consume, not whether it has room right now (a stack with one
 * card scrolls nowhere: gestures.md rule 4). The host therefore passes an empty set for a container that
 * is not currently scrollable.
 */
data class ContainerGestureAxes(val declared: Set<GestureAxis> = emptySet()) {
    /** A child scrolls or pages vertically, so its leftover drag should reach home bindings (rule 3). */
    val handsOffVerticalOverscroll: Boolean get() = GestureAxis.VERTICAL_SCROLL in declared

    /** A child owns horizontal travel (an icon row or a pager). Its overscroll is not handed off (limitation). */
    val ownsHorizontal: Boolean
        get() = GestureAxis.HORIZONTAL_SCROLL in declared || GestureAxis.HORIZONTAL_PAGER in declared

    /** The hand-off switch: the surface's own opt-in, or implied by a declared vertical axis. */
    fun resolveHandOff(surfaceOptIn: Boolean): Boolean = surfaceOptIn || handsOffVerticalOverscroll

    /** Whether a child declared the axis a drag of ([dx], [dy]) mostly travels along. */
    fun declaresDragAlong(
        dx: Float,
        dy: Float,
    ): Boolean =
        when (dragAxisOf(dx, dy)) {
            DragAxis.HORIZONTAL -> ownsHorizontal
            DragAxis.VERTICAL -> handsOffVerticalOverscroll
            null -> false
        }

    companion object {
        /** Null for a drag that has not committed to an axis (equal travel, or none). */
        fun dragAxisOf(
            dx: Float,
            dy: Float,
        ): DragAxis? =
            when {
                abs(dx) > abs(dy) -> DragAxis.HORIZONTAL
                abs(dy) > abs(dx) -> DragAxis.VERTICAL
                else -> null
            }
    }
}
