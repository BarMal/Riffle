package com.riffle.app.launcher

import androidx.compose.ui.unit.LayoutDirection
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
        assertEquals(-80f, DockPosition.LEFT.dragAlongRunPx(dragXPx = 30f, dragYPx = -80f), TOLERANCE)
        assertEquals(-80f, DockPosition.RIGHT.dragAlongRunPx(dragXPx = 30f, dragYPx = -80f), TOLERANCE)
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
    fun pullingAwayFromASideEdgeIsPhysicalAndDirectionIndependent() {
        // The edges are physical, so off a left dock is always rightward and off a right dock always
        // leftward -- the drag deltas are physical screen pixels, unaffected by layout direction.
        assertEquals(80f, DockPosition.LEFT.dragAwayFromEdgePx(dragXPx = 80f, dragYPx = 0f), TOLERANCE)
        assertEquals(-80f, DockPosition.LEFT.dragAwayFromEdgePx(dragXPx = -80f, dragYPx = 0f), TOLERANCE)
        assertEquals(80f, DockPosition.RIGHT.dragAwayFromEdgePx(dragXPx = -80f, dragYPx = 0f), TOLERANCE)
        assertEquals(-80f, DockPosition.RIGHT.dragAwayFromEdgePx(dragXPx = 80f, dragYPx = 0f), TOLERANCE)
    }

    @Test
    fun placementBeforeContentKeepsEachEdgePhysicalAcrossDirections() {
        // Top always leads its column, bottom always trails it; a Column is not mirrored. A Row is,
        // so a left dock leads it in LTR and trails it in RTL, keeping the dock on the physical left.
        assertEquals(true, DockPosition.TOP.placedBeforeContent(LayoutDirection.Ltr))
        assertEquals(true, DockPosition.TOP.placedBeforeContent(LayoutDirection.Rtl))
        assertEquals(false, DockPosition.BOTTOM.placedBeforeContent(LayoutDirection.Ltr))
        assertEquals(false, DockPosition.BOTTOM.placedBeforeContent(LayoutDirection.Rtl))
        assertEquals(true, DockPosition.LEFT.placedBeforeContent(LayoutDirection.Ltr))
        assertEquals(false, DockPosition.LEFT.placedBeforeContent(LayoutDirection.Rtl))
        assertEquals(false, DockPosition.RIGHT.placedBeforeContent(LayoutDirection.Ltr))
        assertEquals(true, DockPosition.RIGHT.placedBeforeContent(LayoutDirection.Rtl))
    }

    private companion object {
        private const val TOLERANCE = 0.001f
    }
}
