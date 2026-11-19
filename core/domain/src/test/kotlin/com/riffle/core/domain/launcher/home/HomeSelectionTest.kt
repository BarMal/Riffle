package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeSelectionTest {
    private val grid = GridDimensions(columns = 3, rows = 2)

    private fun app(
        name: String,
        column: Int,
        row: Int,
    ) = AppShortcutItem(
        id = LauncherItemId(name),
        appIdentity = AppIdentity(AppPackageName("com.example.$name"), AppActivityName("Main")),
        label = name,
        placement = GridPlacement(GridCell(column, row)),
    )

    private val widget =
        WidgetItem(
            id = LauncherItemId("w"),
            appWidgetId = HostedWidgetId(1),
            label = "w",
            placement = GridPlacement(GridCell(2, 1), GridSpan(1, 1)),
        )

    private fun editing(
        vararg items: LauncherItem,
        extraPages: List<LauncherPage> = emptyList(),
    ): HomeLayout {
        val first = LauncherPage(LauncherPageId("p1"), grid = grid, items = items.toList())
        return HomeLayoutDefaults.standard().copy(
            pages = listOf(first) + extraPages,
            selectedPageId = first.id,
            editMode = HomeEditMode.EditingPage(first.id),
        )
    }

    private fun HomeLayout.ids(pageIndex: Int = 0) = pages[pageIndex].items.map { it.id.value }.sorted()

    @Test
    fun tappingTogglesAnAppOrFolderAndNeverAWidget() {
        val folder = FolderItem(LauncherItemId("f"), "F", emptyList(), GridPlacement(GridCell(1, 0)))
        var layout = editing(app("a", 0, 0), folder, widget)
        layout = HomeSelection.toggle(layout, LauncherItemId("a"))
        layout = HomeSelection.toggle(layout, LauncherItemId("f"))
        layout = HomeSelection.toggle(layout, LauncherItemId("w"))
        assertEquals(setOf(LauncherItemId("a"), LauncherItemId("f")), HomeSelection.selectedIds(layout))

        layout = HomeSelection.toggle(layout, LauncherItemId("a"))
        assertEquals(setOf(LauncherItemId("f")), HomeSelection.selectedIds(layout))
    }

    @Test
    fun selectAllTakesEveryAppAndFolderOfThePageButNoWidgetAndClearEmptiesIt() {
        val layout = editing(app("a", 0, 0), app("b", 1, 0), widget)
        val all = HomeSelection.selectAll(layout)
        assertEquals(setOf(LauncherItemId("a"), LauncherItemId("b")), HomeSelection.selectedIds(all))
        assertTrue(HomeSelection.selectedIds(HomeSelection.clear(all)).isEmpty())
    }

    @Test
    fun theSelectionOnlyCountsOnTheEditedPageAndGoesWithTheMode() {
        val other = LauncherPage(LauncherPageId("p2"), grid = grid, items = listOf(app("x", 0, 0)))
        val selected = HomeSelection.toggle(editing(app("a", 0, 0), extraPages = listOf(other)), LauncherItemId("a"))
        val moved = selected.copy(selectedPageId = LauncherPageId("p2"))
        assertTrue(HomeSelection.selectedItems(moved).isEmpty())
        // Outside the mode there is nothing to select.
        val browsing = selected.copy(editMode = HomeEditMode.Browsing)
        assertEquals(browsing, HomeSelection.toggle(browsing, LauncherItemId("a")))
    }

    @Test
    fun removingTakesEverySelectedItemOffAndClearsTheSelection() {
        val layout = HomeSelection.selectAll(editing(app("a", 0, 0), app("b", 1, 0), widget))
        val removed = HomeSelection.removeSelected(layout)
        assertEquals(listOf("w"), removed.ids())
        assertTrue(HomeSelection.selectedIds(removed).isEmpty())
    }

    @Test
    fun makeAFolderNeedsTwoAppsAndMakesOneAtTheFirstOne() {
        val ticked = HomeSelection.toggle(editing(app("b", 1, 0), app("a", 0, 0)), LauncherItemId("a"))
        val two = HomeSelection.toggle(ticked, LauncherItemId("b"))
        assertTrue(HomeSelection.canMakeFolder(two))
        val made = assertNotNull(HomeSelection.makeFolder(two, LauncherItemId("folder"), "Dossier"))
        val folder = made.pages[0].items.filterIsInstance<FolderItem>().single()
        assertEquals(listOf("a", "b"), folder.items.map { it.id.value })
        assertEquals(GridCell(0, 0), folder.placement!!.cell)

        val one = HomeSelection.toggle(editing(app("a", 0, 0)), LauncherItemId("a"))
        assertTrue(!HomeSelection.canMakeFolder(one))
        assertNull(HomeSelection.makeFolder(one, LauncherItemId("folder"), "Dossier"))
        val existing = FolderItem(LauncherItemId("f"), "F", emptyList(), GridPlacement(GridCell(1, 0)))
        val withFolder = HomeSelection.selectAll(editing(app("a", 0, 0), existing))
        assertTrue(!HomeSelection.canMakeFolder(withFolder))
    }

    @Test
    fun aGroupDropLandsFromTheDropCellOnwardAndKeepsTheirOrder() {
        val layout = HomeSelection.selectAll(editing(app("a", 0, 0), app("b", 1, 0), app("c", 2, 0)))
        val result = HomeSelection.dropGroup(layout, LauncherPageId("p1"), GridCell(1, 1))
        val dropped = assertIs<HomeShortcutResult.Updated>(result).layout
        val cells = dropped.pages[0].items.associate { it.id.value to it.placement!!.cell }
        assertEquals(GridCell(1, 1), cells["a"])
        assertEquals(GridCell(2, 1), cells["b"])
        // The page has no cell left after that, so the third spills to a new page.
        assertEquals(listOf("c"), dropped.ids(1))
        assertTrue(HomeSelection.selectedIds(dropped).isEmpty())
    }

    @Test
    fun aGroupDroppedOnAFolderAddsEveryAppAndNothingIsLost() {
        val folder = FolderItem(LauncherItemId("f"), "F", emptyList(), GridPlacement(GridCell(2, 1)))
        val layout =
            HomeSelection.toggle(
                HomeSelection.toggle(editing(app("a", 0, 0), app("b", 1, 0), folder), LauncherItemId("a")),
                LauncherItemId("b"),
            )
        val dropped =
            assertIs<HomeShortcutResult.Updated>(
                HomeSelection.dropGroupOnFolder(layout, LauncherPageId("p1"), LauncherItemId("f")),
            ).layout
        assertEquals(listOf("f"), dropped.ids())
        assertEquals(listOf("a", "b"), (dropped.pages[0].items.single() as FolderItem).items.map { it.id.value })

        val withFolderSelected = HomeSelection.selectAll(editing(app("a", 0, 0), folder))
        assertIs<HomeShortcutResult.Rejected>(
            HomeSelection.dropGroupOnFolder(withFolderSelected, LauncherPageId("p1"), LauncherItemId("f")),
        )
    }
}
