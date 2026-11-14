package com.riffle.app.launcher

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the first-run hint shows and where it sits (pure logic; the drawing is in the screenshots package). */
class DockPressHintTest {
    private fun show(
        seen: Boolean = false,
        streamAvailable: Boolean = true,
        streamShown: Boolean = false,
        libraryShown: Boolean = false,
        motionRunning: Boolean = false,
        choosing: Boolean = false,
        simpleMode: Boolean = false,
    ) = shouldShowDockPressHint(seen, streamAvailable, streamShown, libraryShown, motionRunning, choosing, simpleMode)

    @Test
    fun itNeverShowsInSimpleMode() {
        assertFalse(show(simpleMode = true))
        assertTrue(show(simpleMode = false))
    }

    @Test
    fun itShowsOnHomeOnceThereIsAStreamAndItHasNotBeenSeen() {
        assertTrue(show())
    }

    @Test
    fun itNeverShowsAgainOnceSeen() {
        assertFalse(show(seen = true))
    }

    @Test
    fun itStaysOutOfTheWayOfTheStreamTheLibraryAMotionAndAChoice() {
        assertFalse(show(streamShown = true))
        assertFalse(show(libraryShown = true))
        assertFalse(show(motionRunning = true))
        assertFalse(show(choosing = true))
        assertFalse(show(streamAvailable = false))
    }

    private val root = IntSize(1000, 2000)
    private val size = IntSize(400, 120)

    @Test
    fun aBottomDockPutsItAboveTheDockCentredOnIt() {
        val dock = Rect(100f, 1800f, 900f, 1960f)
        val offset = dockPressHintOffset(dock, size, DockPullDirection.UP, root, gap = 20, margin = 16)

        assertEquals(IntOffset(300, 1800 - 20 - 120), offset)
    }

    @Test
    fun theHomeHandleAboveABottomDockIsLeftClear() {
        val dock = Rect(100f, 1800f, 900f, 1960f)
        val offset =
            dockPressHintOffset(dock, size, DockPullDirection.UP, root, gap = 20, margin = 16, clearance = 144)

        // The hint's bottom edge is the gap and the handle's room above the Dock's top.
        assertEquals(IntOffset(300, 1800 - 20 - 144 - 120), offset)
    }

    @Test
    fun aTopDockPutsItBelowTheDock() {
        val dock = Rect(100f, 40f, 900f, 200f)
        val offset = dockPressHintOffset(dock, size, DockPullDirection.DOWN, root, gap = 20, margin = 16)

        assertEquals(IntOffset(300, 220), offset)
    }

    @Test
    fun aSideDockPutsItBesideTheDockTowardTheInteriorAndNeverOffScreen() {
        val left = Rect(0f, 600f, 160f, 1400f)
        val right = Rect(840f, 600f, 1000f, 1400f)

        // Held inside the root with the margin, wherever the dock's centre would put it.
        val atLeft = dockPressHintOffset(left, size, DockPullDirection.RIGHT, root, gap = 20, margin = 16)
        val atRight = dockPressHintOffset(right, size, DockPullDirection.LEFT, root, gap = 20, margin = 16)

        assertEquals(IntOffset(180, 940), atLeft)
        assertEquals(IntOffset(1000 - 160 - 20 - 400, 940), atRight)
        assertTrue(atRight.x in 16..(1000 - 400 - 16))
    }
}
