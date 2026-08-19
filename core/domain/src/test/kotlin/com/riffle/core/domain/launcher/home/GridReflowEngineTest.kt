package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Re-fitting a layout to a grid that has lost a column, which is what moving the dock to a side
 * does to the workspace.
 */
class GridReflowEngineTest {
    private val engine = GridReflowEngine()

    @Test
    fun leavesItemsThatStillFitExactlyWhereTheyAre() {
        val kept = shortcut(id = "kept", column = 0, row = 0)
        val layout = layoutWith(kept)

        val result = engine.reflowToGrid(layout, GridDimensions(columns = 3, rows = 4))

        val page = assertIs<GridReflowResult.Updated>(result).layout.pages.single()
        assertEquals(GridCell(column = 0, row = 0), page.items.single().placement?.cell)
    }

    @Test
    fun movesAnItemOutOfTheColumnThatWasTakenAway() {
        val stranded = shortcut(id = "stranded", column = 3, row = 2)
        val layout = layoutWith(stranded)

        val result = engine.reflowToGrid(layout, GridDimensions(columns = 3, rows = 4))

        val page = assertIs<GridReflowResult.Updated>(result).layout.pages.single()
        val placement = page.items.single { item -> item.id == stranded.id }.placement
        assertEquals(GridCell(column = 0, row = 0), placement?.cell)
    }

    @Test
    fun keepsEveryItemAndTheNewGridOnThePage() {
        val layout = layoutWith(shortcut("a", 0, 0), shortcut("b", 3, 0), shortcut("c", 3, 1))

        val result = engine.reflowToGrid(layout, GridDimensions(columns = 3, rows = 4))

        val page = assertIs<GridReflowResult.Updated>(result).layout.pages.single()
        assertEquals(setOf("a", "b", "c"), page.items.map { item -> item.id.value }.toSet())
        assertEquals(GridDimensions(columns = 3, rows = 4), page.grid)
        assertTrue(page.items.all { item -> page.grid.holds(item.placement) })
    }

    @Test
    fun spillsOntoALaterPageWhenItsOwnIsFull() {
        // The reflow prefers the page an item was already on, so this only happens when it must.
        val full = (0 until 12).map { index -> shortcut("full$index", index % 3, index / 3) }
        val stranded = shortcut(id = "stranded", column = 3, row = 0)
        val layout = layoutWithPages(full + stranded, emptyList())

        val result = engine.reflowToGrid(layout, GridDimensions(columns = 3, rows = 4))

        val pages = assertIs<GridReflowResult.Updated>(result).layout.pages
        assertEquals(emptyList(), pages[0].items.filter { item -> item.id == stranded.id })
        assertEquals(listOf(stranded.id), pages[1].items.map { item -> item.id })
    }

    @Test
    fun shrinksAWidgetTooWideForTheNewGridRatherThanStrandingIt() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:clock"),
                appWidgetId = HostedWidgetId(42),
                label = "Clock",
                placement =
                    GridPlacement(
                        cell = GridCell(column = 0, row = 0),
                        span = GridSpan(columns = 4, rows = 1),
                    ),
            )
        val layout = layoutWith(widget)

        val result = engine.reflowToGrid(layout, GridDimensions(columns = 3, rows = 4))

        val placed = assertIs<GridReflowResult.Updated>(result).layout.pages.single().items.single()
        assertEquals(GridSpan(columns = 3, rows = 1), placed.placement?.span)
    }

    @Test
    fun rejectsALayoutWithNowhereToPutADisplacedItem() {
        // Every cell of the narrower grid is already spoken for, so nothing can be reflowed.
        val filling = (0 until 12).map { index -> shortcut("full$index", index % 3, index / 3) }
        val stranded = shortcut(id = "stranded", column = 3, row = 0)
        val layout = layoutWith(*(filling + stranded).toTypedArray())

        val result = engine.reflowToGrid(layout, GridDimensions(columns = 3, rows = 4))

        assertEquals(
            PlacementRejectionReason.NO_AVAILABLE_CELL,
            assertIs<GridReflowResult.Rejected>(result).reason,
        )
    }

    @Test
    fun rejectsAGridWithNoColumnsAtAll() {
        val result = engine.reflowToGrid(layoutWith(), GridDimensions(columns = 0, rows = 4))

        assertEquals(
            PlacementRejectionReason.OUT_OF_BOUNDS,
            assertIs<GridReflowResult.Rejected>(result).reason,
        )
    }

    private fun layoutWith(vararg items: LauncherItem): HomeLayout = layoutWithPages(items.toList())

    private fun layoutWithPages(vararg pageItems: List<LauncherItem>): HomeLayout =
        HomeLayoutDefaults.standard().let { defaults ->
            defaults.copy(
                settings =
                    defaults.settings.copy(
                        grid = defaults.settings.grid.copy(dimensions = FULL_GRID),
                    ),
                pages =
                    pageItems.mapIndexed { index, items ->
                        LauncherPage(
                            id = LauncherPageId("page:$index"),
                            grid = FULL_GRID,
                            items = items,
                        )
                    },
                selectedPageId = LauncherPageId("page:0"),
            )
        }

    private fun shortcut(
        id: String,
        column: Int,
        row: Int,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
            placement = GridPlacement(cell = GridCell(column = column, row = row)),
        )

    private companion object {
        private val FULL_GRID = GridDimensions(columns = 4, rows = 4)
    }
}
