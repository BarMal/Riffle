package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockPinnedDropRulesTest {
    private val dock = Rect(0f, 900f, 1000f, 1000f)
    private val slots = listOf(0, 1, 2).map { Rect(300f + it * 120f, 920f, 400f + it * 120f, 980f) }

    private fun index(
        x: Float,
        isRtl: Boolean = false,
        position: DockPosition = DockPosition.BOTTOM,
        y: Float = 950f,
    ) = dockInsertionIndexAmong(Offset(x, y), dock, slots, isRtl, position)

    @Test
    fun aHomeItemHoveringBetweenPinnedItemsIsPinnedBetweenThem() {
        assertEquals(0, index(10f))
        assertEquals(0, index(340f))
        assertEquals(1, index(370f))
        assertEquals(2, index(500f))
        assertEquals(3, index(700f))
    }

    @Test
    fun rightToLeftMirrorsTheRunOfAHorizontalDock() {
        assertEquals(3, index(10f, isRtl = true))
        assertEquals(0, index(990f, isRtl = true))
    }

    @Test
    fun aSideDockReadsTheRunFromY() {
        val side = Rect(900f, 0f, 1000f, 1000f)
        val column = listOf(0, 1).map { Rect(920f, 300f + it * 120f, 980f, 400f + it * 120f) }
        assertEquals(0, dockInsertionIndexAmong(Offset(950f, 100f), side, column, false, DockPosition.RIGHT))
        assertEquals(2, dockInsertionIndexAmong(Offset(950f, 900f), side, column, false, DockPosition.RIGHT))
    }

    @Test
    fun nothingIsHoveredOffTheDock() {
        assertNull(index(500f, y = 500f))
    }

    private val out = LauncherShellAction.MoveDockItemToHome(LauncherItemId("a"))
    private val page = LauncherPageId("p")

    @Test
    fun aDockItemDroppedOnAFreeCellIsPlacedThere() {
        val placed =
            dockDropOnHome(out, { HomeDropTarget(page, GridCell(2, 3), free = true) }, Offset.Zero)
        assertEquals(LauncherShellAction.MoveDockItemToHome(LauncherItemId("a"), GridCell(2, 3), page), placed)
    }

    @Test
    fun aDockItemDroppedOnAnOccupiedCellTakesThePagesFirstFreeCell() {
        val placed =
            dockDropOnHome(out, { HomeDropTarget(page, GridCell(2, 3), free = false) }, Offset.Zero)
        assertEquals(LauncherShellAction.MoveDockItemToHome(LauncherItemId("a"), null, page), placed)
    }

    @Test
    fun aDockItemDroppedWhereHomeHasNothingReturns() {
        assertNull(dockDropOnHome(out, { null }, Offset.Zero))
    }

    @Test
    fun withoutAHomeSurfaceTheOlderRuleApplies() {
        assertEquals(out, dockDropOnHome(out, null, Offset.Zero))
        val reorder = LauncherShellAction.MoveDockShortcutToIndex(LauncherItemId("a"), 1)
        assertEquals(reorder, dockDropOnHome(reorder, { null }, Offset.Zero))
    }
}
