package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoolHomeEditingTest {
    private val base =
        emptyPool()
            .add(poolApp("a"), cell = at(0, 0))
            .add(poolApp("b"), cell = at(1, 0))
            .add(poolWidget("w"), cell = at(0, 1, 2, 2))

    private fun cellOf(
        pool: PlacedItemPool,
        id: String,
    ) = PoolHomeEditing.siteOf(pool, W1, PoolItemId(id))?.placement?.at

    @Test
    fun moveToCellKeepsTheSpanAndUsesTheEngineRules() {
        val moved = PoolHomeEditing.moveToCell(base, W1, PoolItemId("w"), P1, GridCell(2, 2)).done().pool
        assertEquals(at(2, 2, 2, 2), cellOf(moved, "w"))
        assertEquals(
            PoolRejection.COLLISION,
            PoolHomeEditing.moveToCell(base, W1, PoolItemId("a"), P1, GridCell(1, 0)).rejection(),
        )
        assertEquals(
            PoolRejection.OUT_OF_BOUNDS,
            PoolHomeEditing.moveToCell(base, W1, PoolItemId("w"), P1, GridCell(3, 3)).rejection(),
        )
    }

    @Test
    fun moveToCellAcrossPagesMovesTheSinglePlacement() {
        val moved = PoolHomeEditing.moveToCell(base, W1, PoolItemId("a"), P2, GridCell(3, 3)).done().pool
        assertEquals(P2, PoolHomeEditing.siteOf(moved, W1, PoolItemId("a"))?.page?.id)
        assertEquals(1, moved.refs("a"))
        assertInvariants(moved)
    }

    @Test
    fun nudgeMovesByCellsAndRefusesLeavingTheGridOrACollision() {
        val right = PoolHomeEditing.nudge(base, W1, PoolItemId("b"), 1, 0).done().pool
        assertEquals(GridCell(2, 0), cellOf(right, "b")?.cell)
        assertEquals(PoolRejection.OUT_OF_BOUNDS, PoolHomeEditing.nudge(base, W1, PoolItemId("a"), -1, 0).rejection())
        assertEquals(PoolRejection.COLLISION, PoolHomeEditing.nudge(base, W1, PoolItemId("a"), 1, 0).rejection())
        assertEquals(PoolRejection.NOT_PLACED_HERE, PoolHomeEditing.nudge(base, W2, PoolItemId("a"), 1, 0).rejection())
    }

    @Test
    fun adjacentPageKeepsTheCellWhenFreeElseTheFirstFreeCell() {
        val toSecond = PoolHomeEditing.moveToAdjacentPage(base, W1, PoolItemId("a"), 1).done().pool
        assertEquals(GridCell(0, 0), cellOf(toSecond, "a")?.cell)
        val blocked = toSecond.add(poolApp("c"), page = P1, cell = at(0, 0))
        val back = PoolHomeEditing.moveToAdjacentPage(blocked, W1, PoolItemId("a"), -1).done().pool
        assertEquals(P1, PoolHomeEditing.siteOf(back, W1, PoolItemId("a"))?.page?.id)
        assertTrue(cellOf(back, "a")?.cell != GridCell(0, 0))
        assertEquals(
            PoolRejection.UNKNOWN_PAGE,
            PoolHomeEditing.moveToAdjacentPage(base, W1, PoolItemId("a"), -1).rejection(),
        )
        assertEquals(
            PoolRejection.UNKNOWN_PAGE,
            PoolHomeEditing.moveToAdjacentPage(base, W1, PoolItemId("a"), 2).rejection(),
        )
    }

    @Test
    fun adjacentPageIsRefusedWhenTheTargetHasNoRoom() {
        val full = filled(base, P2, 4)
        assertEquals(
            PoolRejection.NO_AVAILABLE_CELL,
            PoolHomeEditing.moveToAdjacentPage(full, W1, PoolItemId("a"), 1).rejection(),
        )
    }

    @Test
    fun removeKeepsTheItemWhenAnotherWorkspaceShowsItAndEverywhereDoesNot() {
        val shared = PoolPlacement.placeExisting(base, W2, P1, PoolItemId("a")).done().pool
        val removed = PoolRemoval.remove(shared, W1, PoolItemId("a")).done()
        assertTrue(PoolItemId("a") in removed.pool.items)
        assertTrue(removed.removedItems.isEmpty())
        val impact = PoolHomeEditing.everywhereImpact(shared, W1, PoolItemId("a"))
        assertEquals(2, impact.references)
        assertEquals(listOf(W2), impact.otherWorkspaces)
        val gone = PoolRemoval.removeEverywhere(shared, PoolItemId("a")).done()
        assertNull(gone.pool.items[PoolItemId("a")])
    }

    @Test
    fun addAppUsesTheFirstFreeCellReusesAPoolAppAndRefusesADuplicate() {
        val ids = counterIds("x")
        val added = PoolHomeEditing.addApp(base, W1, P1, appIdentity("new"), "New", ids).done().pool
        assertEquals(1, added.items.values.count { it is PoolApp && it.label == "New" })
        assertEquals(
            PoolRejection.ALREADY_IN_ARRANGEMENT,
            PoolHomeEditing.addApp(added, W1, P1, appIdentity("new"), "New", ids).rejection(),
        )
        val shared = PoolHomeEditing.addApp(added, W2, P1, appIdentity("new"), "New", ids).done().pool
        assertEquals(1, shared.items.values.count { it is PoolApp && it.label == "New" })
        assertInvariants(shared)
    }

    private fun filled(
        pool: PlacedItemPool,
        page: LauncherPageId,
        rows: Int,
    ): PlacedItemPool {
        val cells = (0 until rows).flatMap { row -> (0 until 4).map { column -> column to row } }
        return cells.fold(pool) { acc, (column, row) ->
            acc.add(poolApp("f${page.value}$column$row"), page = page, cell = at(column, row))
        }
    }

    @Test
    fun addAppFallsThroughToALaterPageAndFailsWhenEveryPageIsFull() {
        val full = filled(filled(emptyPool(), P1, 4), P2, 3)
        val ids = counterIds("x")
        val onSecond = PoolHomeEditing.addApp(full, W1, P1, appIdentity("new"), "New", ids).done().pool
        assertEquals(P2, PoolHomeEditing.siteOf(onSecond, W1, PoolItemId("x1"))?.page?.id)
        val none = PoolHomeEditing.addApp(filled(filled(emptyPool(), P1, 4), P2, 4), W1, P1, appIdentity("o"), "O", ids)
        assertEquals(PoolRejection.NO_AVAILABLE_CELL, none.rejection())
    }

    @Test
    fun addAndRemovePage() {
        val ids = counterIds("pg")
        val added = PoolHomeEditing.addPage(base, W1, ids).done().pool
        val pages = added.arrangements.getValue(W1).pages
        assertEquals(3, pages.size)
        assertEquals(pages[1].grid, pages[2].grid)
        val removed = PoolHomeEditing.removeEmptyPage(added, W1, pages[2].id).done().pool
        assertEquals(base, removed)
        assertEquals(PoolRejection.PAGE_NOT_EMPTY, PoolHomeEditing.removeEmptyPage(base, W1, P1).rejection())
        val arrangement = base.arrangements.getValue(W1)
        val single =
            base.copy(
                arrangements = base.arrangements + (W1 to arrangement.copy(pages = listOf(arrangement.pages[1]))),
            )
        assertEquals(PoolRejection.LAST_PAGE, PoolHomeEditing.removeEmptyPage(single, W1, P2).rejection())
        assertEquals(
            PoolRejection.UNKNOWN_PAGE,
            PoolHomeEditing.removeEmptyPage(base, W1, LauncherPageId("zz")).rejection(),
        )
    }

    @Test
    fun separateCopyOfAWidgetIsANewPlaceholderAndASecondPlacementIsRefused() {
        assertEquals(
            PoolRejection.WIDGET_ALREADY_PLACED,
            PoolPlacement.placeExisting(base, W2, P1, PoolItemId("w")).rejection(),
        )
        val copied = PoolWidgets.separateCopy(base, PoolItemId("w"), W2, P1, counterIds("c"), null).done().pool
        val widgets = copied.items.values.filterIsInstance<PoolWidget>()
        assertEquals(2, widgets.size)
        assertEquals(1, widgets.count { it.hostedId == null })
        assertInvariants(copied)
    }

    @Test
    fun hostIdsOfTheStandardHomeAreNeverDeletable() {
        val released = setOf(HostedWidgetId(1), HostedWidgetId(2))
        assertEquals(setOf(HostedWidgetId(2)), PoolHostIds.deletable(released, setOf(HostedWidgetId(1))))
    }
}
