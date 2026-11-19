package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeSpillTest {
    private val grid = GridDimensions(columns = 2, rows = 2)

    private fun app(
        name: String,
        column: Int? = null,
        row: Int? = null,
    ) = AppShortcutItem(
        id = LauncherItemId(name),
        appIdentity = AppIdentity(AppPackageName("com.example.$name"), AppActivityName("Main")),
        label = name,
        placement = if (column == null || row == null) null else GridPlacement(GridCell(column, row)),
    )

    private fun widget(
        name: String,
        column: Int,
        row: Int,
        columns: Int,
        rows: Int,
    ) = WidgetItem(
        id = LauncherItemId(name),
        appWidgetId = HostedWidgetId(1),
        label = name,
        placement = GridPlacement(GridCell(column, row), GridSpan(columns, rows)),
    )

    private fun fullPage(
        id: String,
        prefix: String,
    ) = LauncherPage(
        LauncherPageId(id),
        grid = grid,
        items = (0 until 4).map { app("$prefix$it", it % 2, it / 2) },
    )

    private fun layout(vararg pages: LauncherPage) =
        HomeLayoutDefaults.standard().copy(pages = pages.toList(), selectedPageId = pages.first().id)

    private fun HomeLayout.ids(pageIndex: Int) = pages[pageIndex].items.map { it.id.value }.sorted()

    private fun HomeLayout.allIds() = pages.flatMap { it.items }.map { it.id.value }.sorted()

    private fun updated(result: HomeShortcutResult): HomeLayout = assertIs<HomeShortcutResult.Updated>(result).layout

    @Test
    fun aDropOnAFullPageSendsTheDisplacedAppToTheNextPagesFirstFreeCell() {
        val second = LauncherPage(LauncherPageId("p2"), grid = grid, items = listOf(app("b0", 1, 0)))
        val before = layout(fullPage("p1", "a"), second)
        val result =
            updated(HomeSpill.drop(before, app("new"), LauncherPageId("p1"), GridCell(1, 0)))

        assertEquals(listOf("a0", "a2", "a3", "new"), result.ids(0))
        assertEquals(listOf("a1", "b0"), result.ids(1))
        assertEquals(GridCell(0, 0), result.pages[1].items.first { it.id.value == "a1" }.placement!!.cell)
        assertEquals(GridCell(1, 0), result.pages[0].items.first { it.id.value == "new" }.placement!!.cell)
    }

    @Test
    fun aNewPageIsAddedAfterTheLastWhenEveryNextPageIsFull() {
        val before = layout(fullPage("p1", "a"), fullPage("p2", "b"))
        val result = updated(HomeSpill.drop(before, app("new"), LauncherPageId("p1"), GridCell(0, 0)))

        assertEquals(3, result.pages.size)
        assertEquals(listOf("a0"), result.pages[2].items.map { it.id.value })
        assertEquals(before.allIds() + "new", result.allIds())
    }

    @Test
    fun theLastPageSpillsToANewPageWithAFreshId() {
        val before = layout(fullPage("p1", "a"))
        val result = updated(HomeSpill.drop(before, app("new"), LauncherPageId("p1"), GridCell(1, 1)))

        assertEquals(2, result.pages.size)
        assertTrue(result.pages[1].id != result.pages[0].id)
        assertEquals(before.allIds() + "new", result.allIds())
    }

    @Test
    fun aWidgetOnTheLandingIsNeverMovedSoTheDroppedItemGoesToTheNextPage() {
        val page = LauncherPage(LauncherPageId("p1"), grid = grid, items = listOf(widget("w", 0, 0, 2, 2)))
        val before = layout(page)
        val result = updated(HomeSpill.drop(before, app("new"), LauncherPageId("p1"), GridCell(1, 1)))

        assertEquals(listOf("w"), result.ids(0))
        assertEquals(widget("w", 0, 0, 2, 2).placement, result.pages[0].items.single().placement)
        assertEquals(listOf("new"), result.ids(1))
    }

    @Test
    fun aDroppedWidgetDisplacesAppsAndKeepsItsSpan() {
        val page =
            LauncherPage(
                LauncherPageId("p1"),
                grid = grid,
                items = listOf(app("a0", 0, 0), app("a1", 1, 0), app("a2", 0, 1), app("a3", 1, 1)),
            )
        val before = layout(page)
        val dropped = widget("w", 0, 0, 2, 1)
        val result = updated(HomeSpill.drop(before, dropped, LauncherPageId("p1"), GridCell(0, 0)))

        assertEquals(listOf("a2", "a3", "w"), result.ids(0))
        assertEquals(GridSpan(2, 1), result.pages[0].items.first { it.id.value == "w" }.placement!!.span)
        assertEquals(listOf("a0", "a1"), result.ids(1))
    }

    @Test
    fun aDropOnAFreeCellOrWithAPushLandsOnTheTargetPageAndSpillsNothing() {
        val page = LauncherPage(LauncherPageId("p1"), grid = grid, items = listOf(app("a0", 0, 0)))
        val before = layout(page)
        val free = updated(HomeSpill.drop(before, app("new"), LauncherPageId("p1"), GridCell(1, 1)))
        assertEquals(1, free.pages.size)
        assertEquals(listOf("a0", "new"), free.ids(0))

        val pushed =
            updated(
                HomeSpill.drop(
                    before,
                    app("new"),
                    LauncherPageId("p1"),
                    GridCell(0, 0),
                    pushed = mapOf(LauncherItemId("a0") to GridCell(1, 0)),
                ),
            )
        assertEquals(1, pushed.pages.size)
        assertEquals(GridCell(1, 0), pushed.pages[0].items.first { it.id.value == "a0" }.placement!!.cell)
    }

    @Test
    fun aGeneratedOrMissingTargetIsRejectedAndNothingIsLost() {
        val before = layout(fullPage("p1", "a"))
        assertIs<HomeShortcutResult.Rejected>(HomeSpill.drop(before, app("n"), LauncherPageId("none"), GridCell(0, 0)))
    }

    @Test
    fun aGroupFillsFromTheDropCellInReadingOrderAndSpillsTheRest() {
        val page = LauncherPage(LauncherPageId("p1"), grid = grid, items = listOf(app("a1", 1, 0)))
        val before = layout(page)
        val group = listOf(app("g0"), app("g1"), app("g2"), app("g3"))
        val result = updated(HomeSpill.dropGroup(before, group, LauncherPageId("p1"), GridCell(0, 0)))

        assertEquals(listOf("a1", "g0", "g1", "g2"), result.ids(0))
        assertEquals(GridCell(0, 0), result.pages[0].items.first { it.id.value == "g0" }.placement!!.cell)
        assertEquals(GridCell(0, 1), result.pages[0].items.first { it.id.value == "g1" }.placement!!.cell)
        assertEquals(GridCell(1, 1), result.pages[0].items.first { it.id.value == "g2" }.placement!!.cell)
        assertEquals(listOf("g3"), result.ids(1))
    }

    @Test
    fun aGroupDroppedLateOnAPageSpillsOnwardWithoutLosingAnyone() {
        val before = layout(LauncherPage(LauncherPageId("p1"), grid = grid))
        val group = (0 until 6).map { app("g$it") }
        val result = updated(HomeSpill.dropGroup(before, group, LauncherPageId("p1"), GridCell(1, 1)))

        assertEquals(group.map { it.id.value }.sorted(), result.allIds())
        assertEquals(listOf("g0"), result.ids(0))
        assertTrue(result.pages.size >= 3)
    }

    @Test
    fun spillNeverMovesAWidgetOrStackItDidNotDrag() {
        val stack = widget("stack", 0, 0, 1, 1)
        val page = LauncherPage(LauncherPageId("p1"), grid = grid, items = listOf(stack, app("a1", 1, 0)))
        val before = layout(page)
        val result = updated(HomeSpill.drop(before, app("new"), LauncherPageId("p1"), GridCell(0, 0)))

        assertEquals(stack.placement, result.pages[0].items.first { it.id.value == "stack" }.placement)
        assertEquals(listOf("a1", "stack"), result.ids(0))
        assertEquals(listOf("new"), result.ids(1))
    }
}
