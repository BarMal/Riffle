package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeDragPlan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.restacked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drops of a Home drag that began outside the pages, and the removal a drop on Remove makes. */
class ExternalDropTest {
    private fun identity(name: String) = AppIdentity(AppPackageName("com.example.$name"), AppActivityName("Main"))

    private fun installed(name: String) = InstalledApp(identity = identity(name), label = name)

    private fun app(
        name: String,
        column: Int,
        row: Int,
    ) = AppShortcutItem(
        id = LauncherItemId(name),
        appIdentity = identity(name),
        label = name,
        placement = GridPlacement(GridCell(column, row)),
    )

    private val grid = GridDimensions(2, 2)

    private fun layout(
        pages: List<LauncherPage>,
        dock: List<AppShortcutItem> = emptyList(),
    ): HomeLayout =
        HomeLayoutDefaults.standard().let { base ->
            base.copy(pages = pages, selectedPageId = pages.first().id, dock = base.dock.copy(items = dock))
        }

    private fun page(
        id: String,
        vararg items: AppShortcutItem,
    ) = LauncherPage(LauncherPageId(id), grid = grid, items = items.toList())

    private fun drop(
        action: LauncherShellAction.DropExternalHomeItem,
        layout: HomeLayout,
    ) = applyShortcutOrFolderDropEdit(action, layout, HomeShortcutEngine(), FolderEngine())

    @Test
    fun aLibraryAppDroppedOnAFreeCellIsAddedAsANewItem() {
        val before = layout(listOf(page("a", app("x", 0, 0))))
        val result =
            drop(
                LauncherShellAction.DropExternalHomeItem(
                    ExternalHomeDragSource.Library(installed("fresh")),
                    LauncherPageId("a"),
                    GridCell(1, 1),
                ),
                before,
            ) as ShortcutOrFolderDropEditResult.Updated
        val added = result.layout.pages[0].items.first { it.id.value != "x" } as AppShortcutItem
        assertEquals(identity("fresh"), added.appIdentity)
        assertEquals(GridCell(1, 1), added.placement!!.cell)
    }

    @Test
    fun aLibraryAppHomeShowsAlreadyIsRejectedLikeAddToHome() {
        val before = layout(listOf(page("a", app("fresh", 0, 0))))
        val result =
            drop(
                LauncherShellAction.DropExternalHomeItem(
                    ExternalHomeDragSource.Library(installed("fresh")),
                    LauncherPageId("a"),
                    GridCell(1, 1),
                ),
                before,
            )
        assertEquals(ShortcutOrFolderDropEditResult.Rejected, result)
    }

    @Test
    fun aLibraryAppDroppedOnAFullPageSpillsTheDisplacedAppToANewPage() {
        val full = page("a", app("a0", 0, 0), app("a1", 1, 0), app("a2", 0, 1), app("a3", 1, 1))
        val result =
            drop(
                LauncherShellAction.DropExternalHomeItem(
                    ExternalHomeDragSource.Library(installed("fresh")),
                    LauncherPageId("a"),
                    GridCell(0, 0),
                ),
                layout(listOf(full)),
            ) as ShortcutOrFolderDropEditResult.Updated
        assertEquals(2, result.layout.pages.size)
        assertEquals(listOf("a0"), result.layout.pages[1].items.map { it.id.value })
    }

    @Test
    fun aLibraryAppDroppedOnTopOfAnAppMakesAFolderOfTheTwo() {
        val before = layout(listOf(page("a", app("target", 1, 0))))
        val result =
            drop(
                LauncherShellAction.DropExternalHomeItem(
                    ExternalHomeDragSource.Library(installed("fresh")),
                    LauncherPageId("a"),
                    GridCell(1, 0),
                    dropOnItemId = LauncherItemId("target"),
                    folderLabel = "Dossier",
                ),
                before,
            ) as ShortcutOrFolderDropEditResult.Updated
        val folder = result.layout.pages[0].items.filterIsInstance<FolderItem>().single()
        assertEquals(setOf(identity("fresh"), identity("target")), folder.items.map { it.appIdentity }.toSet())
    }

    @Test
    fun aDockItemDroppedOnHomeLeavesTheDockAndLandsWhereDropped() {
        val pinned = app("pinned", 0, 0).copy(placement = null)
        val before = layout(listOf(page("a", app("x", 0, 0))), dock = listOf(pinned))
        val result =
            drop(
                LauncherShellAction.DropExternalHomeItem(
                    ExternalHomeDragSource.Dock(pinned.id),
                    LauncherPageId("a"),
                    GridCell(1, 1),
                ),
                before,
            ) as ShortcutOrFolderDropEditResult.Updated
        assertTrue(result.layout.dock.items.isEmpty())
        assertEquals(GridCell(1, 1), result.layout.pages[0].items.first { it.id == pinned.id }.placement!!.cell)
    }

    @Test
    fun removingByTheActionNamesThePageEvenWhenItIsNotTheSelectedOne() {
        val before = layout(listOf(page("a", app("x", 0, 0)), page("b", app("y", 0, 0))))
        val removed =
            HomeShortcutEngine().applyEdit(
                LauncherShellAction.RemoveHomeShortcut(LauncherItemId("y"), LauncherPageId("b")),
                before,
            ) as com.riffle.core.domain.launcher.home.HomeShortcutResult.Updated
        assertTrue(removed.layout.pages[1].items.isEmpty())
        assertEquals(1, removed.layout.pages[0].items.size)
        assertEquals(before.selectedPageId, removed.layout.selectedPageId)
    }

    @Test
    fun aStackAsksFirstAndEverythingElseGoesAtOnce() {
        val stack =
            WidgetItem(
                id = LauncherItemId("stack"),
                appWidgetId = HostedWidgetId(1),
                label = "One",
                placement = GridPlacement(GridCell(0, 0), com.riffle.core.domain.launcher.home.GridSpan(2, 2)),
            ).restacked(listOf(StackedWidget(HostedWidgetId(1), "One"), StackedWidget(HostedWidgetId(2), "Two")), 0)
        assertEquals(
            LauncherShellAction.ConfirmRemoveWidgetStack(stack.id),
            removeFromHomeAction(stack, LauncherPageId("a")),
        )
        assertEquals(
            LauncherShellAction.RemoveHomeShortcut(LauncherItemId("x"), LauncherPageId("a")),
            removeFromHomeAction(app("x", 0, 0), LauncherPageId("a")),
        )
    }

    @Test
    fun releasingAnExternalDragDoesWhatItsPlanSays() {
        val library = ExternalHomeDragSource.Library(installed("fresh"))
        val session =
            externalDragSession(app("fresh", 0, 0), library, LauncherPageId("a"), Offset(10f, 10f))
        val planned = session.copy(plan = HomeDragPlan(landing = GridCell(1, 0), pushed = emptyMap()))
        val drop = externalDropAction(planned, null) as LauncherShellAction.DropExternalHomeItem
        assertEquals(GridCell(1, 0), drop.cell)
        assertEquals(
            LauncherShellAction.AddAppToDock(installed("fresh")),
            externalDropAction(planned.copy(isOverDock = true), null),
        )
        assertNull(externalDropAction(session, null))
        // A Library app is not on Home, so there is nothing to remove; a Dock item can be.
        assertTrue(!session.canBeRemoved())
        val dock = session.copy(external = ExternalHomeDragSource.Dock(LauncherItemId("pinned")), isOverRemove = true)
        assertEquals(
            LauncherShellAction.RemoveDockShortcut(LauncherItemId("pinned")),
            externalDropAction(dock, null),
        )
    }
}
