package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeShortcutEngineDropWithPushTest {
    private val engine = HomeShortcutEngine()
    private val grid = GridDimensions(4, 2)

    private fun app(
        name: String,
        column: Int,
        row: Int,
    ) = AppShortcutItem(
        id = LauncherItemId(name),
        appIdentity = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("Main")),
        label = name,
        placement = GridPlacement(GridCell(column, row)),
    )

    private fun layout(
        first: List<LauncherItem>,
        second: List<LauncherItem> = emptyList(),
    ): HomeLayout {
        val a = LauncherPage(LauncherPageId("a"), grid = grid, items = first)
        val b = LauncherPage(LauncherPageId("b"), grid = grid, items = second)
        return HomeLayoutDefaults.standard().copy(pages = listOf(a, b), selectedPageId = a.id)
    }

    private fun HomeLayout.cell(name: String): Pair<String, GridCell>? =
        pages.firstNotNullOfOrNull { page ->
            page.items.firstOrNull { it.id.value == name }?.let { page.id.value to it.placement!!.cell }
        }

    private fun drop(
        layout: HomeLayout,
        item: String,
        from: String,
        to: String,
        cell: GridCell,
        pushed: Map<LauncherItemId, GridCell> = emptyMap(),
    ) = engine.dropWithPush(layout, LauncherItemId(item), LauncherPageId(from), LauncherPageId(to), cell, pushed)

    @Test
    fun theDroppedItemLandsAndTheNeighbourMovesOutOfTheWay() {
        val before = layout(listOf(app("x", 0, 0), app("n", 2, 0)))
        val result =
            assertIs<HomeShortcutResult.Updated>(
                drop(before, "x", "a", "a", GridCell(2, 0), mapOf(LauncherItemId("n") to GridCell(3, 0))),
            )
        assertEquals("a" to GridCell(2, 0), result.layout.cell("x"))
        assertEquals("a" to GridCell(3, 0), result.layout.cell("n"))
    }

    @Test
    fun aDropOnAnotherPageMovesTheItemThereAndLeavesTheSourceWithout() {
        val before = layout(listOf(app("x", 0, 0)), listOf(app("n", 1, 1)))
        val result =
            assertIs<HomeShortcutResult.Updated>(
                drop(before, "x", "a", "b", GridCell(1, 1), mapOf(LauncherItemId("n") to GridCell(2, 1))),
            )
        assertEquals("b" to GridCell(1, 1), result.layout.cell("x"))
        assertEquals("b" to GridCell(2, 1), result.layout.cell("n"))
        assertEquals(0, result.layout.pages.first { it.id.value == "a" }.items.size)
    }

    @Test
    fun aCollisionRejectsTheWholeDropAndChangesNothing() {
        val before = layout(listOf(app("x", 0, 0), app("n", 2, 0), app("m", 3, 0)))
        val result = drop(before, "x", "a", "a", GridCell(2, 0), mapOf(LauncherItemId("n") to GridCell(3, 0)))
        assertEquals(
            PlacementRejectionReason.COLLISION,
            assertIs<HomeShortcutResult.Rejected>(result).reason,
        )
        val unknownPush = drop(before, "x", "a", "a", GridCell(1, 1), mapOf(LauncherItemId("ghost") to GridCell(0, 1)))
        assertIs<HomeShortcutResult.Rejected>(unknownPush)
    }

    @Test
    fun anUnknownItemOrPageIsRejected() {
        val before = layout(listOf(app("x", 0, 0)))
        assertIs<HomeShortcutResult.Rejected>(drop(before, "nope", "a", "a", GridCell(1, 1)))
        assertIs<HomeShortcutResult.Rejected>(drop(before, "x", "a", "zzz", GridCell(1, 1)))
    }

    @Test
    fun anItemMovedToAnotherPageTakesItsFirstFreeCell() {
        val before = layout(listOf(app("x", 3, 1)), listOf(app("p", 0, 0), app("q", 1, 0)))
        val result =
            assertIs<HomeShortcutResult.Updated>(
                engine.moveItemToFirstFreeCell(before, LauncherItemId("x"), LauncherPageId("a"), LauncherPageId("b")),
            )
        assertEquals("b" to GridCell(2, 0), result.layout.cell("x"))
    }

    @Test
    fun aFullPageRefusesAnItemMovedToIt() {
        val full = (0 until 2).flatMap { row -> (0 until 4).map { column -> app("f$column$row", column, row) } }
        val before = layout(listOf(app("x", 0, 0)), full)
        assertIs<HomeShortcutResult.Rejected>(
            engine.moveItemToFirstFreeCell(before, LauncherItemId("x"), LauncherPageId("a"), LauncherPageId("b")),
        )
    }
}
