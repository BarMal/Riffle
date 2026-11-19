package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockScrubTest {
    private val items =
        listOf(
            DockChoiceItem("tile:a", 0f, 48f),
            DockChoiceItem("tile:b", 56f, 104f),
            DockChoiceItem("pair:p", 112f, 160f),
            DockChoiceItem("tile:c", 168f, 216f),
        )
    private val selectable = setOf("tile:a", "tile:b", "tile:c")

    private fun at(
        x: Float,
        current: String?,
    ) = DockScrub.tileAt(x, items, selectable, current, hysteresisPx = 6f)

    @Test
    fun aSlideAlongABottomDockFromTheHoldIsAScrubAndOneAcrossItIsNot() {
        assertTrue(DockScrub.claims(DockPullDirection.UP, 30f, 5f, touchSlopPx = 8f))
        assertFalse(DockScrub.claims(DockPullDirection.UP, 5f, -30f, touchSlopPx = 8f))
        assertFalse(DockScrub.claims(DockPullDirection.UP, 4f, 2f, touchSlopPx = 8f))
    }

    @Test
    fun aSlideAlongASideDockRunsVertically() {
        assertTrue(DockScrub.claims(DockPullDirection.RIGHT, 3f, 30f, touchSlopPx = 8f))
        assertFalse(DockScrub.claims(DockPullDirection.LEFT, 30f, 3f, touchSlopPx = 8f))
    }

    @Test
    fun aPressArmsTheScrubOnlyOnceHeldForTheTimeoutAndStillWithinSlop() {
        fun armed(
            held: Long,
            travel: Float,
        ) = DockScrub.armed(heldMillis = held, longPressMillis = 400, travelPx = travel, touchSlopPx = 8f)
        assertFalse(armed(200, 0f), "a quick press")
        assertTrue(armed(400, 3f), "held")
        assertFalse(armed(900, 20f), "moved first")
    }

    @Test
    fun aPlainDragIsNeverAScrubWithoutTheHold() {
        // A fast drag along the run, a press that never waited: not armed, so the row scrolls.
        assertFalse(DockScrub.armed(heldMillis = 60, longPressMillis = 400, travelPx = 40f, touchSlopPx = 8f))
    }

    @Test
    fun theTileUnderTheFingerIsCurrent() {
        assertEquals("tile:b", at(80f, "tile:a"))
        assertEquals("tile:c", at(200f, "tile:b"))
    }

    @Test
    fun theCurrentTileKeepsTheFingerWithinTheHysteresis() {
        // Inside b but within the margin of a's end the finger is still a's; once past a's margin it is b's.
        val tight = listOf(DockChoiceItem("tile:a", 0f, 48f), DockChoiceItem("tile:b", 48f, 96f))
        val keys = setOf("tile:a", "tile:b")
        assertEquals("tile:a", DockScrub.tileAt(52f, tight, keys, "tile:a", hysteresisPx = 6f))
        assertEquals("tile:b", DockScrub.tileAt(55f, tight, keys, "tile:a", hysteresisPx = 6f))
        assertEquals("tile:b", DockScrub.tileAt(44f, tight, keys, "tile:b", hysteresisPx = 6f))
        assertEquals("tile:a", DockScrub.tileAt(41f, tight, keys, "tile:b", hysteresisPx = 6f))
    }

    @Test
    fun movingBackAndForthAcrossABoundaryChangesNothing() {
        val path = listOf(46f, 50f, 53f, 47f, 51f, 45f)
        val seen = path.runningFold("tile:a") { current, x -> at(x, current)!! }
        assertEquals(setOf("tile:a"), seen.toSet())
    }

    @Test
    fun anUnselectableTileAndTheGapsArePassedOver() {
        assertEquals("tile:b", at(130f, "tile:b"))
        assertEquals("tile:c", at(200f, "tile:b"))
        assertEquals("tile:b", at(108f, "tile:b"))
    }

    @Test
    fun theFirstTileIsFoundFromNothing() {
        assertEquals("tile:a", at(10f, null))
        assertEquals(null, at(130f, null))
    }

    @Test
    fun theEdgesScrollOnlyWhileTilesAreHiddenThatWay() {
        fun dir(
            x: Float,
            start: Boolean,
            end: Boolean,
        ) = DockScrub.edgeDirection(x, 0f, 300f, 24f, start, end)
        assertEquals(1, dir(290f, start = false, end = true))
        assertEquals(0, dir(290f, start = true, end = false))
        assertEquals(-1, dir(10f, start = true, end = true))
        assertEquals(0, dir(10f, start = false, end = true))
        assertEquals(0, dir(150f, start = true, end = true))
        assertEquals(1, dir(320f, start = true, end = true))
    }
}
