package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.riffle.core.domain.launcher.home.DockPosition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure placement math behind Cards' dock-edge companion pills (see
 * DockEdgeCompanionPlacement.kt's own doc): where they land relative to the dock's own measured
 * strip on each of the four edges, and the root-coordinate translation that feeds it. The
 * [Layout][androidx.compose.ui.layout.Layout]-based composable itself is Compose plumbing, not
 * independently testable without a device.
 */
class DockEdgeCompanionPlacementTest {
    private val container = IntSize(width = 400, height = 800)
    private val companion = IntSize(width = 120, height = 40)

    @Test
    fun aTopDockPutsItsCompanionJustBelowTheStripCenteredOnIt() {
        val dockBounds = Rect(left = 100f, top = 0f, right = 300f, bottom = 60f)

        val offset = dockEdgeCompanionOffset(container, dockBounds, DockPosition.TOP, companion, gapPx = 8)

        // Centered on the dock's own horizontal midpoint (200), less half the companion's width.
        assertEquals(IntOffset(x = 140, y = 68), offset)
    }

    @Test
    fun aBottomDockPutsItsCompanionJustAboveTheStrip() {
        val dockBounds = Rect(left = 100f, top = 740f, right = 300f, bottom = 800f)

        val offset = dockEdgeCompanionOffset(container, dockBounds, DockPosition.BOTTOM, companion, gapPx = 8)

        assertEquals(IntOffset(x = 140, y = 692), offset)
    }

    @Test
    fun aLeftDockPutsItsCompanionBesideTheStripCenteredOnItsVerticalMidpoint() {
        val dockBounds = Rect(left = 0f, top = 300f, right = 72f, bottom = 500f)

        val offset = dockEdgeCompanionOffset(container, dockBounds, DockPosition.LEFT, companion, gapPx = 8)

        // Centered on the dock's own vertical midpoint (400), less half the companion's height.
        assertEquals(IntOffset(x = 80, y = 380), offset)
    }

    @Test
    fun aRightDockPutsItsCompanionBesideTheStripOnItsOtherSide() {
        val dockBounds = Rect(left = 328f, top = 300f, right = 400f, bottom = 500f)

        val offset = dockEdgeCompanionOffset(container, dockBounds, DockPosition.RIGHT, companion, gapPx = 8)

        assertEquals(IntOffset(x = 200, y = 380), offset)
    }

    @Test
    fun neverPlacesTheCompanionPastTheContainersLeadingEdge() {
        // A dock strip right at the corner would otherwise centre the companion into negative x.
        val dockBounds = Rect(left = 0f, top = 0f, right = 40f, bottom = 60f)

        val offset = dockEdgeCompanionOffset(container, dockBounds, DockPosition.TOP, companion, gapPx = 8)

        assertEquals(0, offset.x)
    }

    @Test
    fun neverPlacesTheCompanionPastTheContainersTrailingEdge() {
        val dockBounds = Rect(left = 380f, top = 0f, right = 400f, bottom = 60f)

        val offset = dockEdgeCompanionOffset(container, dockBounds, DockPosition.TOP, companion, gapPx = 8)

        assertEquals(container.width - companion.width, offset.x)
    }

    @Test
    fun relativeToShiftsARectByTheGivenOrigin() {
        val rect = Rect(left = 100f, top = 200f, right = 148f, bottom = 248f)

        val relative = rect.relativeTo(Offset(x = 20f, y = 30f))

        assertEquals(Rect(left = 80f, top = 170f, right = 128f, bottom = 218f), relative)
    }

    @Test
    fun relativeToAZeroOriginIsUnchanged() {
        val rect = Rect(left = 10f, top = 10f, right = 50f, bottom = 50f)

        assertEquals(rect, rect.relativeTo(Offset.Zero))
    }
}
