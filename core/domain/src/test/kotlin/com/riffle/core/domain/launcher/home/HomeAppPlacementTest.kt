package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeAppPlacementTest {
    private val grid = GridDimensions(columns = 4, rows = 2)

    private fun app(name: String) =
        InstalledApp(
            identity = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("Main")),
            label = name,
        )

    private fun layout(vararg pages: LauncherPage) =
        HomeLayoutDefaults.standard().copy(
            pages = pages.toList(),
            selectedPageId = pages.first().id,
        )

    private fun item(
        name: String,
        column: Int,
        row: Int,
        span: GridSpan = GridSpan(),
    ) = AppShortcutItem(
        id = LauncherItemId("i-$name"),
        appIdentity = app(name).identity,
        label = name,
        placement = GridPlacement(GridCell(column, row), span),
    )

    private fun page(
        id: String,
        vararg items: LauncherItem,
    ) = LauncherPage(LauncherPageId(id), grid = grid, items = items.toList())

    private fun HomeLayout.cellOf(name: String): Pair<String, GridCell>? =
        pages.firstNotNullOfOrNull { page ->
            page.items.firstOrNull { (it as? AppShortcutItem)?.label == name }
                ?.let { page.id.value to it.placement!!.cell }
        }

    @Test
    fun appendGoesAfterTheLastOccupiedCellNotIntoAnEarlierHole() {
        val before = layout(page("a", item("one", 0, 0), item("three", 2, 0)))

        val after = assertNotNull(HomeAppPlacement.appendApp(before, app("new")))

        assertEquals("a" to GridCell(3, 0), after.cellOf("new"))
    }

    @Test
    fun appendOnAnEmptyHomeTakesTheFirstCell() {
        val after = assertNotNull(HomeAppPlacement.appendApp(layout(page("a")), app("new")))
        assertEquals("a" to GridCell(0, 0), after.cellOf("new"))
    }

    @Test
    fun appendWrapsToTheNextRowAndThenTheNextPageAndThenANewPage() {
        val full = (0 until 8).map { item("f$it", it % 4, it / 4) }
        val two = layout(page("a", *full.toTypedArray()), page("b"))
        // The last used page is full: the next existing page takes it.
        assertEquals("b" to GridCell(0, 0), HomeAppPlacement.appendApp(two, app("x"))!!.cellOf("x"))

        val onlyFull = layout(page("a", *full.toTypedArray()))
        val grown = assertNotNull(HomeAppPlacement.appendApp(onlyFull, app("x")))
        assertEquals(2, grown.pages.size)
        assertEquals("home:2" to GridCell(0, 0), grown.cellOf("x"))

        val row = layout(page("a", item("end0", 3, 0)))
        assertEquals("a" to GridCell(0, 1), HomeAppPlacement.appendApp(row, app("x"))!!.cellOf("x"))
    }

    @Test
    fun appendSkipsTheCellsOfAWidgetSpanAndStopsAfterItsLastCell() {
        val widget =
            WidgetItem(
                id = LauncherItemId("w"),
                appWidgetId = HostedWidgetId(1),
                label = "w",
                placement = GridPlacement(GridCell(0, 0), GridSpan(2, 2)),
            )
        val after = assertNotNull(HomeAppPlacement.appendApp(layout(page("a", widget)), app("x")))
        // The span's last cell in reading order is (1,1): the next cell is (2,1).
        assertEquals("a" to GridCell(2, 1), after.cellOf("x"))
    }

    @Test
    fun appendIsRefusedForAnAppHomeAlreadyShowsAndIdempotent() {
        val once = assertNotNull(HomeAppPlacement.appendApp(layout(page("a")), app("x")))
        assertNull(HomeAppPlacement.appendApp(once, app("x")))
    }

    @Test
    fun appendIgnoresGeneratedPages() {
        val generated =
            LauncherPage(
                LauncherPageId("g"),
                LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                grid,
            )
        val after = assertNotNull(HomeAppPlacement.appendApp(layout(generated, page("a")), app("x")))
        assertEquals("a" to GridCell(0, 0), after.cellOf("x"))
        assertNull(HomeAppPlacement.appendApp(layout(generated), app("x")))
    }

    @Test
    fun filledWithFillsEveryPageFullyInOrderAndDropsTheOldItems() {
        val apps = ('a'..'t').map { app("app$it") } // 20 apps, 8 cells a page: 3 pages (8, 8, 4)
        val old = layout(page("a", item("old", 1, 1)), page("b", item("old2", 0, 0)), page("c"), page("d"))

        val result = HomeAppPlacement.filledWith(old, apps)

        val homes = result.pages.filter { it.type == LauncherPageType.Home }
        assertEquals(listOf("a", "b", "c"), homes.map { it.id.value })
        assertEquals(listOf(8, 8, 4), homes.map { it.items.size })
        assertEquals(apps.map { it.label }, homes.flatMap { it.items }.map { (it as AppShortcutItem).label })
        assertEquals(GridCell(3, 1), homes[0].items.last().placement!!.cell)
        assertEquals(null, result.cellOf("old"))
        assertEquals(old.selectedPageId, result.selectedPageId)
    }

    @Test
    fun filledWithAddsPagesAndHandlesNoApps() {
        val apps = (1..9).map { app("n$it") }
        val result = HomeAppPlacement.filledWith(layout(page("a")), apps)
        assertEquals(2, result.pages.size)
        assertTrue(result.pages.map { it.id.value }.toSet().size == 2)
        val empty = HomeAppPlacement.filledWith(layout(page("a", item("old", 0, 0))), emptyList())
        assertEquals(1, empty.pages.size)
        assertTrue(empty.pages.single().items.isEmpty())
    }

    @Test
    fun filledWithIsIdempotentForTheSameApps() {
        val apps = (1..11).map { app("n$it") }
        val once = HomeAppPlacement.filledWith(layout(page("a")), apps)
        assertEquals(once, HomeAppPlacement.filledWith(once, apps))
    }
}
