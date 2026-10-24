package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.HomeShownWorkspaces
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceNewApps
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** S9: the standard home shows and edits the ACTIVE workspace's arrangement (borrowing, then forking on first edit). */
class PoolHomeActiveWorkspaceTest {
    private val phone = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)
    private val landscape = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE_LANDSCAPE)
    private val standard = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
    private val home = LauncherPageId("home")
    private val second = LauncherPageId("second")
    private val ids = counterIds("new")
    private val migrated = WorkspaceHome.migratedId(phone)

    private fun app(
        id: String,
        column: Int = 0,
        row: Int = 0,
    ) = AppShortcutItem(LauncherItemId(id), appIdentity(id), id, placement = at(column, row))

    private val folder =
        FolderItem(
            LauncherItemId("folder"),
            "Games",
            listOf(AppShortcutItem(LauncherItemId("e1"), appIdentity("g1"), "g1")),
            at(1, 0),
        )
    private val widget = WidgetItem(LauncherItemId("clock"), HostedWidgetId(42), "Clock", placement = at(0, 1, 2, 2))
    private val layout =
        HomeLayout(
            viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
            pages =
                listOf(
                    LauncherPage(home, grid = GridDimensions(4, 5), items = listOf(app("mail"), folder, widget)),
                    LauncherPage(second, grid = GridDimensions(4, 5), items = listOf(app("maps", column = 3, row = 4))),
                ),
            selectedPageId = home,
            dock = DockModel(capacity = 5, items = listOf(app("dock-phone"))),
        )
    private val standardLayout =
        HomeLayout(
            viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
            pages = listOf(LauncherPage(home, grid = GridDimensions(4, 5), items = listOf(app("std")))),
            selectedPageId = home,
            dock = DockModel(capacity = 5),
        )
    private val legacy =
        HomeLayoutSet(
            activeKey = phone,
            layouts = mapOf(phone to layout, landscape to layout, standard to standardLayout),
        )

    private val nova: Workspace = WorkspacePresets.defaultFor(HomeLayoutDeviceClass.PHONE)

    /** A workspace whose only page is a lens page (no placed items), like TimeScape or Kvaesitso. */
    private val lensOnly: Workspace =
        nova.copy(id = WorkspaceId("lens-only"), pages = nova.pages.filterNot { it.id == nova.pages.first().id })

    private fun setWith(
        vararg workspaces: Workspace,
        active: Workspace = workspaces.first(),
    ): WorkspaceSet =
        WorkspaceSet(
            mapOf(
                HomeLayoutDeviceClass.PHONE to LayoutWorkspaces(workspaces.toList(), active.id, workspaces.first().id),
            ),
        )

    private fun cutOver(): PoolStoreState = PoolCutover.resync(null, legacy).state

    private fun open(shown: HomeShownWorkspaces): PoolHomeCommit =
        PoolHomeSync.commit(cutOver(), null, legacy, ids, shown)

    private fun PoolHomeCommit.edit(
        shown: HomeShownWorkspaces,
        change: (HomeLayout) -> HomeLayout,
    ): PoolHomeCommit =
        PoolHomeSync.commit(
            state,
            this.shown,
            this.shown.withActiveLayout(change(this.shown.activeLayout)),
            ids,
            shown,
        )

    private fun HomeLayout.homeItems(): List<LauncherItem> =
        pages.filter { it.type == LauncherPageType.Home }.flatMap { it.items }

    private fun PoolHomeCommit.phonePool(): PlacedItemPool = state.pools.getValue(HomeLayoutDeviceClass.PHONE)

    @Test
    fun theNovaDefaultBorrowingTheMigratedHomeRendersItIdentically() {
        assertTrue(WorkspaceHome.hasHomeGrid(nova))
        val before = open(HomeShownWorkspaces.Migrated)
        val after = open(WorkspaceHome.shownWorkspaces(setWith(nova)))

        assertEquals(before.shown, after.shown)
        assertEquals(before.state, after.state)
        assertFalse(PoolArrangements.ownsArrangement(after.phonePool(), nova.id))
    }

    @Test
    fun theFirstEditForksIntoTheActiveWorkspaceAndLeavesTheMigratedHomeAsItWas() {
        val shown = WorkspaceHome.shownWorkspaces(setWith(nova))
        val opened = open(shown)
        val migratedBefore = opened.phonePool().arrangements.getValue(migrated)

        val moved =
            opened.edit(shown) { current ->
                val page = current.pages.first { it.id == home }
                val mail = page.items.first { it.id.value.endsWith("mail") } as AppShortcutItem
                current.copy(
                    pages =
                        current.pages.map {
                            if (it.id == home) {
                                it.copy(
                                    items =
                                        it.items.map {
                                                item ->
                                            if (item == mail) mail.copy(placement = at(3, 0)) else item
                                        },
                                )
                            } else {
                                it
                            }
                        },
                )
            }

        val pool = moved.phonePool()
        assertTrue(PoolArrangements.ownsArrangement(pool, nova.id))
        assertEquals(migratedBefore, pool.arrangements.getValue(migrated))
        val shownMail = moved.shown.activeLayout.homeItems().first { it.id.value.endsWith("mail") }
        assertEquals(at(3, 0), shownMail.placement)
        // Everything else is what the migrated home had: the fork copied it, nothing was lost.
        assertEquals(opened.shown.activeLayout.homeItems().size, moved.shown.activeLayout.homeItems().size)
        // The widget instance is shared, never duplicated: one pool widget holds host id 42.
        assertEquals(1, pool.items.values.count { it is PoolWidget && it.hostedId == HostedWidgetId(42) })
    }

    @Test
    fun aFolderEditOnTheFirstEditRenamesTheForksFolderOnly() {
        val shown = WorkspaceHome.shownWorkspaces(setWith(nova))
        val opened = open(shown)
        val renamed =
            opened.edit(shown) { current ->
                current.copy(
                    pages =
                        current.pages.map { page ->
                            page.copy(items = page.items.map { if (it is FolderItem) it.copy(label = "Play") else it })
                        },
                )
            }

        val shownFolder = renamed.shown.activeLayout.homeItems().filterIsInstance<FolderItem>().single()
        assertEquals("Play", shownFolder.label)
        assertEquals(opened.shown.activeLayout.homeItems().size, renamed.shown.activeLayout.homeItems().size)
        val pool = renamed.phonePool()
        val migratedFolder =
            pool.arrangements.getValue(migrated).pages.flatMap { it.placements }
                .mapNotNull { pool.items[it.item] as? PoolFolder }
                .single()
        assertEquals("Games", migratedFolder.label)
    }

    @Test
    fun aRestartAfterTheForkShowsTheEditedHome() {
        val shown = WorkspaceHome.shownWorkspaces(setWith(nova))
        val edited =
            open(shown).edit(shown) { current ->
                current.copy(
                    pages =
                        current.pages.map {
                            if (it.id == second) it.copy(items = it.items + app("added", column = 0, row = 0)) else it
                        },
                )
            }
        val decoded = PoolStoreCodec.decode(PoolStoreCodec.encode(edited.state)).state!!
        val restarted =
            PoolHomeSync.commit(
                decoded,
                null,
                PoolHomeSync.stored(edited.shown, decoded.pools.keys),
                ids,
                shown,
            )

        assertEquals(edited.shown.activeLayout.homeItems(), restarted.shown.activeLayout.homeItems())
        assertTrue(restarted.shown.activeLayout.homeItems().any { it.id.value == "added" })
    }

    @Test
    fun removingASharedWidgetFromTheForkReleasesNothing() {
        val shown = WorkspaceHome.shownWorkspaces(setWith(nova))
        val removed =
            open(shown).edit(shown) { current ->
                current.copy(
                    pages =
                        current.pages.map {
                            it.copy(
                                items =
                                    it.items.filterNot {
                                            item ->
                                        item is WidgetItem
                                    },
                            )
                        },
                )
            }

        assertTrue(removed.releasedHostIds.isEmpty())
        assertTrue(removed.shown.activeLayout.homeItems().none { it is WidgetItem })
        // The migrated home still holds the instance.
        assertTrue(PoolHostIds.referencedHostIds(removed.state).contains(HostedWidgetId(42)))
    }

    @Test
    fun aMigratedActiveWorkspaceIsTheS4HomeExactly() {
        val migratedWorkspace = Workspace(migrated, "Library", nova.pages)
        val shown = WorkspaceHome.shownWorkspaces(setWith(migratedWorkspace))

        assertEquals(open(HomeShownWorkspaces.Migrated).shown, open(shown).shown)
    }

    @Test
    fun otherModesKeepTheirMigratedArrangements() {
        val shown = WorkspaceHome.shownWorkspaces(setWith(nova))
        val opened = open(shown)

        val standardItems = opened.shown.layoutFor(standard).homeItems().map { it.id.value }
        assertEquals(listOf("std"), standardItems.map { it.substringAfterLast(':') })
        assertEquals(
            WorkspaceHome.migratedId(standard),
            WorkspaceHome.shown(setWith(nova), standard, phone.viewMode).workspaceId,
        )
    }

    @Test
    fun theLandscapePhoneShowsTheActiveWorkspaceToo() {
        val shown = WorkspaceHome.shownWorkspaces(setWith(nova))
        val target = WorkspaceHome.shown(setWith(nova), landscape, phone.viewMode)

        assertEquals(nova.id, target.workspaceId)
        assertEquals(
            open(shown).shown.layoutFor(phone).homeItems(),
            open(shown).shown.layoutFor(landscape).homeItems(),
        )
    }

    @Test
    fun aWorkspaceWithoutPlacedPagesBorrowsNothingAndAdoptsNoItems() {
        assertFalse(WorkspaceHome.hasHomeGrid(lensOnly))
        val shown = WorkspaceHome.shownWorkspaces(setWith(lensOnly, nova))
        val base = cutOver()
        // Switching to it re-projects from what the store holds (no placed items), as the shell does.
        val switched = PoolHomeSync.reproject(base, PoolHomeSync.stored(legacy, base.pools.keys), ids, shown)

        assertTrue(switched.shown.activeLayout.homeItems().isEmpty())
        assertEquals(base.pools.getValue(HomeLayoutDeviceClass.PHONE).items, switched.phonePool().items)
        assertEquals(
            base.pools.getValue(HomeLayoutDeviceClass.PHONE).arrangements.getValue(migrated),
            switched.phonePool().arrangements.getValue(migrated),
        )
    }

    @Test
    fun anInstalledAppIsPlacedOnlyInOptedInWorkspaces() {
        val ios = nova.copy(newApps = WorkspaceNewApps(NewAppPlacement.HOME_AND_FINDER, autoPages = true))
        val set = setWith(ios)
        val shown = WorkspaceHome.shownWorkspaces(set)
        val opened = open(shown)

        val placed =
            PoolHomeSync.placeNewApp(
                opened.state,
                opened.shown,
                appIdentity("new.app"),
                "New",
                set,
                AppArrival.INSTALLED,
                ids,
                shown,
            )
        val icon =
            placed.shown.activeLayout.homeItems().filterIsInstance<AppShortcutItem>().firstOrNull {
                it.appIdentity == appIdentity("new.app")
            }
        assertNotNull(icon)
        assertTrue(PoolArrangements.ownsArrangement(placed.phonePool(), ios.id))
        // The migrated home (the recovery copy) did not get the icon.
        val migratedRefs =
            placed.phonePool().arrangements.getValue(migrated).pages.flatMap { page -> page.placements.map { it.item } }
        assertFalse(
            migratedRefs.any { (placed.phonePool().items[it] as? PoolApp)?.appIdentity == appIdentity("new.app") },
        )

        for (arrival in listOf(AppArrival.PROFILE_AVAILABLE, AppArrival.UPDATED, AppArrival.RESTORED)) {
            val ignored =
                PoolHomeSync.placeNewApp(
                    opened.state,
                    opened.shown,
                    appIdentity("new.app"),
                    "New",
                    set,
                    arrival,
                    ids,
                    shown,
                )
            assertEquals(opened.state, ignored.state)
        }
        val finderOnly = setWith(nova)
        val notPlaced =
            PoolHomeSync.placeNewApp(
                opened.state,
                opened.shown,
                appIdentity("new.app"),
                "New",
                finderOnly,
                AppArrival.INSTALLED,
                ids,
                WorkspaceHome.shownWorkspaces(finderOnly),
            )
        assertEquals(opened.state, notPlaced.state)
    }
}
