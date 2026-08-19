package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reading a drag in the dock's own terms. A dock is the same thing on every edge, so the drag code
 * downstream works in "along the run" and "away from the edge" rather than in x and y.
 */
class DockRunAxisTest {
    @Test
    fun aDockOnAHorizontalEdgeReordersAlongX() {
        assertEquals(30f, DockPosition.BOTTOM.dragAlongRunPx(dragXPx = 30f, dragYPx = -80f), TOLERANCE)
        assertEquals(30f, DockPosition.TOP.dragAlongRunPx(dragXPx = 30f, dragYPx = -80f), TOLERANCE)
    }

    @Test
    fun aDockOnASideEdgeReordersAlongY() {
        assertEquals(-80f, DockPosition.LEADING.dragAlongRunPx(dragXPx = 30f, dragYPx = -80f), TOLERANCE)
        assertEquals(-80f, DockPosition.TRAILING.dragAlongRunPx(dragXPx = 30f, dragYPx = -80f), TOLERANCE)
    }

    @Test
    fun pullingAwayFromAHorizontalEdgeIsPositiveOnBothOfThem() {
        // Off a bottom dock is up the screen; off a top dock is down it. Both read as "away".
        assertEquals(80f, DockPosition.BOTTOM.dragAwayFromEdgePx(dragXPx = 0f, dragYPx = -80f), TOLERANCE)
        assertEquals(80f, DockPosition.TOP.dragAwayFromEdgePx(dragXPx = 0f, dragYPx = 80f), TOLERANCE)
    }

    @Test
    fun pushingIntoAHorizontalEdgeIsNegative() {
        assertEquals(-80f, DockPosition.BOTTOM.dragAwayFromEdgePx(dragXPx = 0f, dragYPx = 80f), TOLERANCE)
        assertEquals(-80f, DockPosition.TOP.dragAwayFromEdgePx(dragXPx = 0f, dragYPx = -80f), TOLERANCE)
    }

    @Test
    fun pullingAwayFromASideEdgeFollowsTheLayoutDirection() {
        // The leading edge is the left one in LTR and the right one in RTL, so the same rightward
        // drag leaves the dock in one and pushes into it in the other.
        assertEquals(
            80f,
            DockPosition.LEADING.dragAwayFromEdgePx(dragXPx = 80f, dragYPx = 0f, isRtl = false),
            TOLERANCE,
        )
        assertEquals(
            -80f,
            DockPosition.LEADING.dragAwayFromEdgePx(dragXPx = 80f, dragYPx = 0f, isRtl = true),
            TOLERANCE,
        )
        assertEquals(
            80f,
            DockPosition.TRAILING.dragAwayFromEdgePx(dragXPx = -80f, dragYPx = 0f, isRtl = false),
            TOLERANCE,
        )
        assertEquals(
            80f,
            DockPosition.TRAILING.dragAwayFromEdgePx(dragXPx = 80f, dragYPx = 0f, isRtl = true),
            TOLERANCE,
        )
    }

    private companion object {
        private const val TOLERANCE = 0.001f
    }
}
