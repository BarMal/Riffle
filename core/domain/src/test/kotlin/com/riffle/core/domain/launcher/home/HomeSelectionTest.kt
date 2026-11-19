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

    private fun tick(
        layout: HomeLayout,
        vararg ids: String,
    ) = ids.fold(layout) { acc, id -> HomeSelection.toggle(acc, LauncherItemId(id)) }

    @Test
    fun theGroupDroppedOnAnAppMakesOneFolderOfThatAppAndTheGroupWhereTheAppStood() {
        val layout = tick(editing(app("a", 0, 0), app("b", 1, 0), app("t", 2, 1)), "a", "b")

        val result =
            HomeSelection.dropGroupOnApp(layout, LauncherPageId("p1"), LauncherItemId("t"), LauncherItemId("fo"), "Mix")

        val updated = assertIs<HomeShortcutResult.Updated>(result).layout
        val folder = updated.pages.single().items.single() as FolderItem
        assertEquals(LauncherItemId("fo"), folder.id)
        assertEquals("Mix", folder.label)
        assertEquals(listOf("t", "a", "b"), folder.items.map { it.id.value })
        assertEquals(GridPlacement(GridCell(2, 1)), folder.placement)
        assertTrue(HomeSelection.selectedIds(updated).isEmpty())
    }

    @Test
    fun aTickedTargetMakesTheFolderAroundItWithTheRestOfTheGroup() {
        val layout = tick(editing(app("a", 0, 0), app("b", 1, 0), app("c", 2, 0)), "a", "b", "c")

        val result =
            HomeSelection.dropGroupOnApp(layout, LauncherPageId("p1"), LauncherItemId("b"), LauncherItemId("fo"), "F")

        val folder = assertIs<HomeShortcutResult.Updated>(result).layout.pages.single().items.single() as FolderItem
        assertEquals(listOf("b", "a", "c"), folder.items.map { it.id.value })
        assertEquals(GridPlacement(GridCell(1, 0)), folder.placement)
    }

    @Test
    fun theGroupLeavesItsOwnPageWhenTheAppIsOnAnother() {
        val other = LauncherPage(LauncherPageId("p2"), grid = grid, items = listOf(app("t", 1, 1)))
        val layout = tick(editing(app("a", 0, 0), app("b", 1, 0), extraPages = listOf(other)), "a", "b")

        val result =
            HomeSelection.dropGroupOnApp(layout, LauncherPageId("p2"), LauncherItemId("t"), LauncherItemId("fo"), "F")
        val updated = assertIs<HomeShortcutResult.Updated>(result).layout

        assertTrue(updated.pages[0].items.isEmpty())
        assertEquals(listOf("t", "a", "b"), (updated.pages[1].items.single() as FolderItem).items.map { it.id.value })
    }

    @Test
    fun aGroupHoldingAFolderOrDroppedOnNothingMakesNoFolder() {
        val folder = FolderItem(LauncherItemId("f"), "F", emptyList(), GridPlacement(GridCell(1, 0)))
        val withFolder = tick(editing(app("a", 0, 0), folder, app("t", 2, 1)), "a", "f")
        val plain = tick(editing(app("a", 0, 0), app("t", 2, 1)), "a")

        fun drop(
            layout: HomeLayout,
            on: String,
        ) = HomeSelection.dropGroupOnApp(layout, LauncherPageId("p1"), LauncherItemId(on), LauncherItemId("fo"), "F")

        assertIs<HomeShortcutResult.Rejected>(drop(withFolder, "t"))
        assertIs<HomeShortcutResult.Rejected>(drop(plain, "nowhere"))
        assertIs<HomeShortcutResult.Rejected>(drop(plain, "a"))
        assertIs<HomeShortcutResult.Rejected>(drop(tick(editing(app("t", 0, 0)), "t"), "t"))
    }
}
