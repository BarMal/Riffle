package com.riffle.app.launcher

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the Stream handle stands and which way it points (pure logic; the drawing is in the screenshots package). */
class DockStreamHandleTest {
    private val root = IntSize(1000, 2000)
    private val size = IntSize(120, 120)

    @Test
    fun aBottomDockPutsItAboveTheDocksFarEndClearOfTheHomeHandle() {
        val dock = Rect(100f, 1800f, 900f, 1960f)

        val offset = dockStreamHandleOffset(dock, size, DockPullDirection.UP, root, gap = 8, margin = 16, clearance = 40)

        assertEquals(IntOffset(900 - 120, 1800 - 8 - 40 - 120), offset)
    }

    @Test
    fun aTopDockPutsItBelowTheDock() {
        val dock = Rect(100f, 40f, 900f, 200f)

        val offset = dockStreamHandleOffset(dock, size, DockPullDirection.DOWN, root, gap = 8, margin = 16)

        assertEquals(IntOffset(900 - 120, 208), offset)
    }

    @Test
    fun aSideDockPutsItBesideTheDocksLowerEndTowardTheInterior() {
        val left = Rect(0f, 600f, 160f, 1400f)
        val right = Rect(840f, 600f, 1000f, 1400f)

        val atLeft = dockStreamHandleOffset(left, size, DockPullDirection.RIGHT, root, gap = 8, margin = 16)
        val atRight = dockStreamHandleOffset(right, size, DockPullDirection.LEFT, root, gap = 8, margin = 16)

        assertEquals(IntOffset(168, 1400 - 120), atLeft)
        assertEquals(IntOffset(840 - 8 - 120, 1400 - 120), atRight)
    }

    @Test
    fun itIsNeverPlacedOffScreen() {
        val dock = Rect(0f, 1990f, 1000f, 2000f)

        val offset = dockStreamHandleOffset(dock, size, DockPullDirection.DOWN, root, gap = 8, margin = 16)

        assertEquals(2000 - 120 - 16, offset.y)
    }

    @Test
    fun theChevronPointsTheWayTheDockIsPulled() {
        assertEquals(0f, DockPullDirection.UP.chevronDegrees())
        assertEquals(90f, DockPullDirection.RIGHT.chevronDegrees())
        assertEquals(180f, DockPullDirection.DOWN.chevronDegrees())
        assertEquals(270f, DockPullDirection.LEFT.chevronDegrees())
    }
}
