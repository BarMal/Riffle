package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeAppPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HomeLayoutSettings
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LibraryViewRetirement
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.HomeShownWorkspace
import com.riffle.core.domain.launcher.workspace.HomeShownWorkspaces
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The library view mode is retired (ADR 0003, Amendment 10), through the same pool path the shell runs: a layout whose
 * view mode is the library mode becomes the Standard Home as ordinary placed items, once, and then nothing fills,
 * sorts or compacts it again. Realistic stored states: the library layout the old auto-layout filled (apps on the
 * Home pages and on generated "library:n" pages), a Standard arrangement beside it, a widget, a folder.
 */
class LibraryViewRetirementPoolTest {
    private val phoneLibrary = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)
    private val phoneStandard = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
    private val ids = counterIds("new")
    private val grid = GridDimensions(4, 2)
    private val libraryWorkspace = WorkspaceId("ws:phone:home_screen_library")
    private val standardWorkspace = WorkspaceId("ws:phone:standard_app_drawer")

    private fun libraryApp(
        name: String,
        index: Int,
    ) = AppShortcutItem(
        LauncherItemId("library-app:0:com.example.$name/.Main"),
        appIdentity(name),
        name,
        placement = at(index % grid.columns, index / grid.columns),
    )

    private val widget = WidgetItem(LauncherItemId("clock"), HostedWidgetId(42), "Clock", placement = at(0, 1, 2, 1))
    private val folder =
        FolderItem(
            LauncherItemId("folder"),
            "Games",
            listOf(AppShortcutItem(LauncherItemId("entry"), appIdentity("chess"), "chess")),
            at(3, 0),
        )

    /** Home page: a manual folder, a widget and three library apps; two more generated pages of library apps. */
    private val libraryLayout =
        HomeLayout(
            viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
            pages =
                listOf(
                    LauncherPage(
                        LauncherPageId("home"),
                        grid = grid,
                        items =
                            listOf(
                                libraryApp("alpha", 0),
                                libraryApp("bravo", 1),
                                libraryApp("charlie", 2),
                                folder,
                            ),
                    ),
                    LauncherPage(
                        LauncherPageId("library:1"),
                        LauncherPageType.AllApps,
                        grid,
                        listOf(libraryApp("delta", 0), libraryApp("echo", 1), widget),
                    ),
                    LauncherPage(
                        LauncherPageId("library:2"),
                        LauncherPageType.AllApps,
                        grid,
                        listOf(libraryApp("foxtrot", 0)),
                    ),
                ),
            selectedPageId = LauncherPageId("library:1"),
            dock = DockModel(capacity = 5, items = listOf(libraryApp("pinned", 0).copy(placement = null))),
            settings = HomeLayoutSettings.standardPhone().copy(grid = GridSettings(grid, compactLibraryPages = true)),
        )

    private val standardLayout =
        HomeLayout(
            viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
            pages =
                listOf(
                    LauncherPage(LauncherPageId("home"), grid = grid, items = listOf(libraryApp("kilo", 0))),
                ),
            selectedPageId = LauncherPageId("home"),
            dock = DockModel(capacity = 5),
        )

    /** A phone that used Standard, then the library mode, and is in the library mode now. */
    private val legacy =
        HomeLayoutSet(
            activeKey = phoneLibrary,
            layouts = mapOf(phoneLibrary to libraryLayout, phoneStandard to standardLayout),
        )

    private val workspaces: WorkspaceSet = WorkspaceMigration.migrate(legacy)
    private val shown = WorkspaceHome.shownWorkspaces(workspaces)
    private val derived = setOf(HomeLayoutDeviceClass.PHONE)

    private val LauncherItem.name: String
        get() =
            when (this) {
                is AppShortcutItem -> label
                is FolderItem -> label
                is WidgetItem -> label
            }

    private fun HomeLayout.homeItems(): List<LauncherItem> =
        pages.filter { it.type == LauncherPageType.Home }.flatMap { it.items }

    private fun HomeLayout.appNames(): List<String> = homeItems().filterIsInstance<AppShortcutItem>().map { it.label }

    /** The shell's startup: the pool holds the home, the set is projected from it. */
    private fun open(): PoolHomeCommit =
        PoolHomeSync.commit(PoolCutover.resync(null, legacy).state, null, legacy, ids, shown)

    /** [open] with the whole retirement run, as `LauncherShellViewModel.retireLibraryViewMode` runs it. */
    private fun retired(start: PoolHomeCommit = open()): PoolHomeCommit {
        val set = start.shown
        val placed =
            PoolHomeSync.commit(
                start.state,
                set,
                LibraryViewRetirement.withPlacedPages(set, derived),
                ids,
                shown,
            )
        val moved = LibraryViewRetirement.withStandardKeys(placed.shown, derived)
        return if (moved == placed.shown) placed else PoolHomeSync.reproject(placed.state, moved, ids, shown)
    }

    private fun PoolHomeCommit.layout() = shown.activeLayout

    @Test
    fun theLibraryLayoutBecomesTheStandardHomeWithTheSameItemsInTheSameOrderOnTheSamePages() {
        val before = open().layout()
        val after = retired().layout()

        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, after.viewMode)
        assertEquals(listOf("home", "library:1", "library:2"), after.pages.map { it.id.value })
        assertTrue(after.pages.all { it.type == LauncherPageType.Home })
        assertEquals(
            before.pages.map { page -> page.items.map { item -> item.name } },
            after.pages.map { page ->
                page.items.map { item -> item.name }
            },
        )
        assertEquals(
            before.pages.map { page -> page.items.map { it.placement } },
            after.pages.map { page ->
                page.items.map { it.placement }
            },
        )
        assertEquals(LauncherPageId("library:1"), after.selectedPageId)
        assertFalse(after.settings.grid.compactLibraryPages)
    }

    @Test
    fun theSetHoldsOnlyTheStandardKeyAndTheWidgetKeepsItsHostId() {
        val commit = retired()

        assertEquals(phoneStandard, commit.shown.activeKey)
        assertNull(commit.shown.layouts[phoneLibrary])
        assertEquals(
            LauncherViewMode.STANDARD_APP_DRAWER,
            commit.shown.preferredModesByDeviceClass.getValue(
                HomeLayoutDeviceClass.PHONE,
            ),
        )
        assertTrue(commit.releasedHostIds.isEmpty())
        // The widget sat on a generated page, which the pool never held: now the pool references its host id.
        assertTrue(HostedWidgetId(42) in PoolHostIds.referencedHostIds(commit.state))
        val shownWidget = commit.layout().homeItems().filterIsInstance<WidgetItem>().single()
        assertEquals(HostedWidgetId(42), shownWidget.appWidgetId)
    }

    @Test
    fun theConvertedItemsAreInTheLibraryArrangementAndTheStandardArrangementIsKept() {
        val state = retired().state.pools.getValue(HomeLayoutDeviceClass.PHONE)

        val arrangement = state.arrangements.getValue(libraryWorkspace)
        assertEquals(3, arrangement.pages.size)
        assertEquals(
            listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot"),
            arrangement.pages.flatMap { it.placements }.mapNotNull { (state.items[it.item] as? PoolApp)?.label },
        )
        val standard = state.arrangements.getValue(standardWorkspace)
        assertEquals(
            listOf("kilo"),
            standard.pages.flatMap { it.placements }.mapNotNull { (state.items[it.item] as? PoolApp)?.label },
        )
    }

    @Test
    fun runningItAgainChangesNothing() {
        val once = retired()
        assertFalse(LibraryViewRetirement.isPending(once.shown, derived))
        val twice = retired(once)

        assertEquals(once.shown, twice.shown)
        assertEquals(once.state, twice.state)
    }

    @Test
    fun aRestartShowsTheSameHomeFromTheStoredSetAndThePool() {
        val once = retired()
        val stored = PoolHomeSync.stored(once.shown, once.state.pools.keys)
        val restarted = PoolHomeSync.commit(once.state, null, stored, ids, shown)

        assertEquals(
            once.layout().pages.map { page -> page.items.map { it.id } },
            restarted.layout().pages.map { page -> page.items.map { it.id } },
        )
        assertEquals(once.layout().viewMode, restarted.layout().viewMode)
        assertFalse(LibraryViewRetirement.isPending(restarted.shown, derived))
    }

    @Test
    fun aRemovedItemStaysRemovedAcrossARefreshAndARestart() {
        val once = retired()
        val removed = once.layout().homeItems().filterIsInstance<AppShortcutItem>().first { it.label == "bravo" }
        val edited =
            once.layout().copy(
                pages =
                    once.layout().pages.map {
                            page ->
                        page.copy(items = page.items.filterNot { it.id == removed.id })
                    },
            )

        val afterEdit = PoolHomeSync.commit(once.state, once.shown, once.shown.withActiveLayout(edited), ids, shown)
        // A refresh: the whole retirement pass runs again on what the shell holds.
        val refreshed = retired(afterEdit)
        // A restart: the stored set and the pool.
        val stored = PoolHomeSync.stored(refreshed.shown, refreshed.state.pools.keys)
        val restarted = retired(PoolHomeSync.commit(refreshed.state, null, stored, ids, shown))

        assertFalse("bravo" in afterEdit.layout().appNames())
        assertFalse("bravo" in refreshed.layout().appNames())
        assertFalse("bravo" in restarted.layout().appNames())
        assertEquals(
            listOf("alpha", "charlie", "delta", "echo", "foxtrot"),
            restarted.layout().appNames(),
        )
    }

    @Test
    fun aMovedItemStaysWhereItWasPut() {
        val once = retired()
        val first = once.layout().pages.first()
        val charlie = first.items.filterIsInstance<AppShortcutItem>().first { it.label == "charlie" }
        val moved = charlie.copy(placement = at(3, 1))
        val edited =
            once.layout().copy(
                pages =
                    once.layout().pages.map { page ->
                        if (page.id == first.id) {
                            page.copy(
                                items = page.items.map { if (it.id == charlie.id) moved else it },
                            )
                        } else {
                            page
                        }
                    },
            )

        val afterEdit = PoolHomeSync.commit(once.state, once.shown, once.shown.withActiveLayout(edited), ids, shown)
        val refreshed = retired(afterEdit)

        val placement = refreshed.layout().pages.first().items.first { it.id == charlie.id }.placement
        assertEquals(at(3, 1), placement)
    }

    @Test
    fun theSettingsLevelMigrationAfterwardsDoesNotPlaceAnAppTwice() {
        val once = retired()
        val apps =
            listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf").map { name ->
                InstalledApp(appIdentity(name), name)
            }

        val filled = HomeAppPlacement.filledWith(once.layout(), apps)

        val placed =
            filled.pages.filter { it.type == LauncherPageType.Home }
                .flatMap { it.items }.filterIsInstance<AppShortcutItem>().map { it.appIdentity }
        assertEquals(apps.map { it.identity }, placed)
        assertEquals(placed.distinct(), placed)
    }

    @Test
    fun aNewInstallIsAppendedAfterTheLastItemAndNothingElseMoves() {
        val once = retired()
        val app = InstalledApp(appIdentity("golf"), "golf")

        val appended = checkNotNull(HomeAppPlacement.appendApp(once.layout(), app))
        val afterEdit = PoolHomeSync.commit(once.state, once.shown, once.shown.withActiveLayout(appended), ids, shown)
        val refreshed = retired(afterEdit)

        val beforeItems = once.layout().pages.map { page -> page.items.map { it.id to it.placement } }
        val afterPages = refreshed.layout().pages
        assertEquals(beforeItems.size, afterPages.size)
        beforeItems.forEachIndexed { index, items ->
            assertEquals(items, afterPages[index].items.take(items.size).map { it.id to it.placement })
        }
        assertEquals("golf", refreshed.layout().appNames().last())
        assertEquals(7, refreshed.layout().appNames().size)
    }

    @Test
    fun aWorkspaceThatForkedFromTheLibraryArrangementIsConvertedInItsOwnArrangement() {
        // The active workspace is a preset, forked from the migrated library one: its own arrangement is the Home.
        val preset = WorkspaceId("preset:nova")
        val state = PoolCutover.resync(null, legacy).state
        val phonePool = state.pools.getValue(HomeLayoutDeviceClass.PHONE)
        val library = phonePool.arrangements.getValue(libraryWorkspace)
        val forkedState =
            state.copy(
                pools =
                    state.pools + (
                        HomeLayoutDeviceClass.PHONE to
                            phonePool.copy(
                                arrangements =
                                    phonePool.arrangements + (preset to library.copy(forkedFrom = libraryWorkspace)),
                            )
                    ),
            )
        val presetShown =
            HomeShownWorkspaces { _, _ ->
                HomeShownWorkspace(preset)
            }

        val start = PoolHomeSync.commit(forkedState, null, legacy, ids, presetShown)
        val placed =
            PoolHomeSync.commit(
                start.state,
                start.shown,
                LibraryViewRetirement.withPlacedPages(start.shown, derived),
                ids,
                presetShown,
            )
        val moved = LibraryViewRetirement.withStandardKeys(placed.shown, derived)
        val final = PoolHomeSync.reproject(placed.state, moved, ids, presetShown)

        assertEquals(listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot"), final.layout().appNames())
        val pool = final.state.pools.getValue(HomeLayoutDeviceClass.PHONE)
        assertEquals(3, pool.arrangements.getValue(preset).pages.size)
        // The migrated library arrangement was not touched.
        assertEquals(library, pool.arrangements.getValue(libraryWorkspace))
    }
}
