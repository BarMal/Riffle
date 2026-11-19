package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeArrangementTest {
    private val grid = GridDimensions(columns = 4, rows = 2)

    private fun app(
        name: String,
        profile: AppProfile = AppProfile.personal(),
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ) = InstalledApp(
        identity = AppIdentity(AppPackageName("pkg.${name.lowercase()}"), AppActivityName("Main"), profile),
        label = name,
        visibility = visibility,
    )

    private fun item(
        app: InstalledApp,
        column: Int,
        row: Int,
    ) = AppShortcutItem(
        id = LauncherItemId("i-${app.label}"),
        appIdentity = app.identity,
        label = app.label,
        placement = GridPlacement(GridCell(column, row)),
    )

    private fun folder(
        name: String,
        column: Int,
        row: Int,
        vararg apps: InstalledApp,
    ) = FolderItem(
        id = LauncherItemId("f-$name"),
        label = name,
        items = apps.map { item(it, 0, 0) },
        placement = GridPlacement(GridCell(column, row)),
    )

    private fun widget(
        column: Int,
        row: Int,
        columns: Int = 2,
        rows: Int = 1,
    ) = WidgetItem(
        id = LauncherItemId("w-$column-$row"),
        appWidgetId = HostedWidgetId(column * 10 + row),
        label = "Widget",
        placement = GridPlacement(GridCell(column, row), GridSpan(columns, rows)),
    )

    private fun page(
        id: String,
        vararg items: LauncherItem,
        dims: GridDimensions = grid,
    ) = LauncherPage(LauncherPageId(id), grid = dims, items = items.toList())

    private fun layout(
        vararg pages: LauncherPage,
        dock: List<LauncherItem> = emptyList(),
    ) = HomeLayoutDefaults.standard().let {
        it.copy(
            pages = pages.toList(),
            selectedPageId = pages.first().id,
            dock = it.dock.copy(items = dock),
        )
    }

    private fun HomeLayout.labels(pageIndex: Int): List<String?> =
        pages[pageIndex].items.sortedBy { it.placement!!.cell.row * 100 + it.placement!!.cell.column }
            .map { (it as? AppShortcutItem)?.label ?: (it as? FolderItem)?.label }

    /** [apps] placed in reading order from the first cell of a page with [dims]. */
    private fun packed(
        apps: List<InstalledApp>,
        dims: GridDimensions = grid,
    ): Array<LauncherItem> = apps.mapIndexed { i, a -> item(a, i % dims.columns, i / dims.columns) }.toTypedArray()

    private fun names(count: Int) = (1..count).map { app("App%02d".format(it)) }

    // Add all apps

    @Test
    fun addAllPlacesEveryAppNotOnHomeAfterTheLastOccupiedCellInAToZOrder() {
        val a = app("Alpha")
        val b = app("Bravo")
        val before = layout(page("p", item(b, 0, 0)))

        val result = HomeArrangement.addAll(before, listOf(app("Delta"), b, a, app("Charlie")))

        assertEquals(3, result.added)
        assertEquals(listOf("Bravo", "Alpha", "Charlie", "Delta"), result.layout.labels(0))
    }

    @Test
    fun addAllSkipsAppsInFoldersTheDockAndHiddenOnes() {
        val inFolder = app("Folded")
        val docked = app("Docked")
        val hidden = app("Hidden", visibility = AppVisibility.HIDDEN)
        val dockItem = item(docked, 0, 0).copy(placement = null)
        val before = layout(page("p", folder("Group", 0, 0, inFolder)), dock = listOf(dockItem))

        val missing = HomeArrangement.missingApps(before, listOf(inFolder, docked, hidden, app("Plain")))

        assertEquals(listOf("Plain"), missing.map { it.label })
    }

    @Test
    fun addAllOrdersWorkAndPrivateAppsLikeTheLibraryAndPlacesAnAppOnce() {
        val work = app("Mail", AppProfile.work())
        val personal = app("Mail")
        val twice = app("Zed")

        val missing = HomeArrangement.missingApps(layout(page("p")), listOf(work, personal, twice, twice))

        assertEquals(3, missing.size)
        assertEquals(listOf("Mail", "Mail", "Zed"), missing.map { it.label })
    }

    @Test
    fun addAllAddsPagesWhenTheLastOneIsFullAndMovesNothingThatIsPlaced() {
        val placed = names(8).mapIndexed { i, a -> item(a, i % 4, i / 4) }
        val before = layout(page("p", *placed.toTypedArray()))

        val result = HomeArrangement.addAll(before, names(8) + listOf(app("Zulu"), app("Yankee")))

        assertEquals(2, result.added)
        assertEquals(2, result.layout.pages.size)
        assertEquals(placed, result.layout.pages[0].items)
        assertEquals(listOf("Yankee", "Zulu"), result.layout.labels(1))
    }

    @Test
    fun addAllWithNothingMissingChangesNothing() {
        val a = app("Alpha")
        val before = layout(page("p", item(a, 0, 0)))

        val result = HomeArrangement.addAll(before, listOf(a))

        assertEquals(0, result.added)
        assertEquals(before, result.layout)
    }

    @Test
    fun addAllRunsAfterTheLastOccupiedCellNotIntoEarlierHoles() {
        val b = app("Bravo")
        val before = layout(page("p", item(b, 2, 0)))

        val result = HomeArrangement.addAll(before, listOf(b, app("Alpha")))

        assertEquals(GridCell(3, 0), result.layout.pages[0].items.last().placement!!.cell)
    }

    // Arrange

    @Test
    fun arrangeSortsAppsAndFoldersTogetherCaseInsensitively() {
        val before =
            layout(
                page(
                    "p",
                    item(app("zebra"), 0, 0),
                    folder("Games", 1, 0, app("Chess")),
                    item(app("Apple"), 2, 0),
                    item(app("banana"), 3, 0),
                ),
            )

        val after = HomeArrangement.arrange(before, compact = true)

        assertEquals(listOf("Apple", "banana", "Games", "zebra"), after.labels(0))
    }

    @Test
    fun arrangeFillsPagesFullyWhenCompactAndAddsAndRemovesPages() {
        val apps = names(10)
        val spread =
            layout(
                page("a", *apps.take(3).mapIndexed { i, a -> item(a, i, 0) }.toTypedArray()),
                page("b"),
                page("c", *apps.drop(3).mapIndexed { i, a -> item(a, i % 4, i / 4) }.toTypedArray()),
                page("d"),
                page("e"),
            )

        val after = HomeArrangement.arrange(spread, compact = true)

        assertEquals(listOf(8, 2), after.pages.map { it.items.size })
        assertEquals(listOf("a", "b"), after.pages.map { it.id.value })
        assertEquals(apps.map { it.label }, after.labels(0) + after.labels(1))
    }

    @Test
    fun arrangeAddsPagesWhenThereAreMoreItemsThanPages() {
        val before = layout(page("a", *packed(names(8))), page("b", item(app("Zz"), 0, 0)))

        val after = HomeArrangement.arrange(before, compact = true)

        assertEquals(listOf(8, 1), after.pages.map { it.items.size })
        assertEquals("Zz", after.labels(1).single())
    }

    @Test
    fun theRoomyFillStopsAtAboutThreeQuartersOfEachPage() {
        assertEquals(15, HomeArrangement.fillLimit(20, compact = false))
        assertEquals(6, HomeArrangement.fillLimit(8, compact = false))
        assertEquals(1, HomeArrangement.fillLimit(1, compact = false))
        assertEquals(1, HomeArrangement.fillLimit(0, compact = false))
        assertEquals(36, HomeArrangement.fillLimit(48, compact = false))
        assertEquals(20, HomeArrangement.fillLimit(20, compact = true))
        assertEquals(3, HomeArrangement.fillLimit(5, compact = false))
    }

    @Test
    fun arrangeWithRoomLeavesAboutAQuarterOfEachPageFree() {
        val before = layout(page("a", *packed(names(8))))

        val after = HomeArrangement.arrange(before, compact = false)

        assertEquals(listOf(6, 2), after.pages.map { it.items.size })
    }

    @Test
    fun arrangeUsesEachPagesOwnGridForTheRoomyLimit() {
        val small = GridDimensions(columns = 2, rows = 2)
        val big = GridDimensions(columns = 5, rows = 4)
        val before =
            layout(
                page("a", *packed(names(3), small), dims = small),
                page("b", *packed(names(20).drop(3), big), dims = big),
            )

        val after = HomeArrangement.arrange(before, compact = false)

        // 2x2 holds floor(0.75 * 4) = 3; 5x4 holds 15; 17 apps need a third page of the 5x4 grid.
        assertEquals(listOf(3, 15, 2), after.pages.map { it.items.size })
        assertEquals(big, after.pages[2].grid)
    }

    @Test
    fun widgetsKeepTheirPageAndCellsAndIconsFlowAroundThem() {
        val wide = widget(column = 0, row = 0)
        val a = app("Alpha")
        val b = app("Bravo")
        val c = app("Charlie")
        val before = layout(page("p", item(c, 3, 1), wide, item(a, 2, 0), item(b, 3, 0)))

        val after = HomeArrangement.arrange(before, compact = true)

        val page = after.pages[0]
        assertTrue(wide in page.items)
        assertEquals(GridCell(2, 0), page.items.first { (it as? AppShortcutItem)?.label == "Alpha" }.placement!!.cell)
        assertEquals(GridCell(3, 0), page.items.first { (it as? AppShortcutItem)?.label == "Bravo" }.placement!!.cell)
        assertEquals(GridCell(0, 1), page.items.first { (it as? AppShortcutItem)?.label == "Charlie" }.placement!!.cell)
    }

    @Test
    fun aPageWithAWidgetThatWouldBeEmptyIsKeptAndEmptyPagesAfterItGo() {
        val w = widget(column = 0, row = 0)
        val before = layout(page("a", item(app("Alpha"), 0, 0)), page("b", w), page("c"))

        val after = HomeArrangement.arrange(before, compact = true)

        assertEquals(listOf("a", "b"), after.pages.map { it.id.value })
        assertEquals(listOf(w), after.pages[1].items)
    }

    @Test
    fun aStackIsOneWidgetThatStaysWhereItIsWhileIconsFlowAroundIt() {
        val stack =
            widget(column = 0, row = 0)
                .restacked(
                    listOf(
                        StackedWidget(HostedWidgetId(1), "First"),
                        StackedWidget(HostedWidgetId(2), "Second"),
                        StackedWidget(HostedWidgetId(3), "Third"),
                    ),
                    shown = 2,
                )
        val before = layout(page("a", stack, item(app("Zulu"), 3, 1), item(app("Alpha"), 2, 1)))

        val after = HomeArrangement.arrange(before, compact = true)

        val kept = after.pages.single().items.filterIsInstance<WidgetItem>().single()
        assertEquals(stack, kept)
        assertEquals(3, kept.members.size)
        assertEquals(2, kept.shownIndex)
        assertEquals(GridCell(0, 0), kept.placement!!.cell)
    }

    @Test
    fun aWidgetPageIsNotFilledPastTheRoomyBudgetAndTheNextPageTakesTheRest() {
        // 8 cells, a widget on 2 of them, a budget of 6 cells: 4 icons fit; the other 3 go on the next page.
        val apps = names(7)
        val onFirst = listOf(item(apps[0], 2, 1), item(apps[1], 3, 1), item(apps[2], 0, 1))
        val before = layout(page("a", widget(0, 0), *onFirst.toTypedArray()), page("b", *packed(apps.drop(3))))

        val after = HomeArrangement.arrange(before, compact = false)

        assertEquals(1 + 4, after.pages[0].items.size)
        assertEquals(3, after.pages[1].items.size)
    }

    @Test
    fun arrangeDoesNotTouchTheDockOrOtherPageTypes() {
        val docked = app("Docked")
        val dockItem = item(docked, 0, 0).copy(placement = null)
        val other = LauncherPage(LauncherPageId("all"), type = LauncherPageType.AllApps, grid = grid)
        val before = layout(page("a", item(app("Beta"), 0, 0), item(app("Alpha"), 1, 0)), dock = listOf(dockItem))
        val withOther = before.copy(pages = before.pages + other)

        val after = HomeArrangement.arrange(withOther, compact = true)

        assertEquals(before.dock, after.dock)
        assertEquals(other, after.pages.last())
    }

    @Test
    fun arrangeIsIdempotent() {
        val before =
            layout(
                page(
                    "a",
                    item(app("Mango"), 3, 1),
                    widget(0, 0),
                    item(app("apple"), 1, 1),
                    folder("Zoo", 2, 0, app("Ape")),
                ),
                page("b", *names(9).mapIndexed { i, a -> item(a, i % 4, i / 4) }.take(8).toTypedArray()),
                page("c"),
            )
        for (compact in listOf(true, false)) {
            val once = HomeArrangement.arrange(before, compact)
            assertEquals(once, HomeArrangement.arrange(once, compact), "compact=$compact")
        }
    }

    @Test
    fun arrangeKeepsOneEmptyPageWhenThereAreNoItemsAndKeepsPinnedPages() {
        val empty = layout(page("a"), page("b"))
        assertEquals(listOf("a"), HomeArrangement.arrange(empty, compact = true).pages.map { it.id.value })

        val pinned = empty.copy(pages = listOf(empty.pages[0], empty.pages[1].copy(isPinned = true)))
        assertEquals(listOf("a", "b"), HomeArrangement.arrange(pinned, compact = true).pages.map { it.id.value })
    }

    @Test
    fun arrangeKeepsItemIdsAndTheSelectedPageWhenItStillExists() {
        val a = item(app("Alpha"), 3, 1)
        val before = layout(page("a", a), page("b", item(app("Bravo"), 0, 0)))

        val after = HomeArrangement.arrange(before, compact = true)

        val ids = after.pages.flatMap { it.items }.map { it.id }.toSet()
        assertEquals(setOf(LauncherItemId("i-Alpha"), LauncherItemId("i-Bravo")), ids)
        assertEquals(LauncherPageId("a"), after.selectedPageId)
    }

    @Test
    fun theSelectedPageMovesWhenItsPageWasRemoved() {
        val before =
            layout(page("a", item(app("Alpha"), 0, 0)), page("b")).copy(selectedPageId = LauncherPageId("b"))

        val after = HomeArrangement.arrange(before, compact = true)

        assertEquals(LauncherPageId("a"), after.selectedPageId)
    }

    @Test
    fun undoIsTheLayoutBeforeAndItIsNotMutated() {
        val before = layout(page("a", item(app("Beta"), 0, 0), item(app("Alpha"), 1, 0)))
        val snapshot = before.copy()

        val after = HomeArrangement.arrange(before, compact = true)

        assertEquals(snapshot, before)
        assertFalse(after == before)
    }
}
