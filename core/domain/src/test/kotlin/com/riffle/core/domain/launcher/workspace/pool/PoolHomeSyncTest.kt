package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeEditMode
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
import com.riffle.core.domain.launcher.home.allItems
import com.riffle.core.domain.launcher.home.dockFor
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.backup.WorkspaceBackup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PoolHomeSyncTest {
    private val phone = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)
    private val landscape = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE_LANDSCAPE)
    private val home = LauncherPageId("home")
    private val second = LauncherPageId("second")
    private val library = LauncherPageId("library:1")
    private val ids = counterIds("new")
    private val notes = WidgetItem(LauncherItemId("widget-7"), HostedWidgetId(77), "Notes", placement = at(0, 0, 2, 2))

    private fun fork(pool: PlacedItemPool): PlacedItemPool =
        PoolArrangements.fork(pool, WorkspaceId("mine"), listOf(PoolHomeSync.workspaceOf(phone)), ids).done().pool

    private fun app(
        id: String,
        pkg: String = id,
        column: Int = 0,
        row: Int = 0,
    ) = AppShortcutItem(LauncherItemId(id), appIdentity(pkg), pkg, placement = at(column, row))

    private val land = AppShortcutItem(LauncherItemId("land"), appIdentity("land"), "land", placement = at(0, 0))

    private val folder =
        FolderItem(
            LauncherItemId("folder"),
            "Games",
            listOf(AppShortcutItem(LauncherItemId("e1"), appIdentity("g1"), "g1")),
            at(1, 0),
        )
    private val widget = WidgetItem(LauncherItemId("clock"), HostedWidgetId(42), "Clock", placement = at(0, 1, 2, 2))
    private val panelWidget =
        WidgetItem(LauncherItemId("weather"), HostedWidgetId(43), "Weather", placement = at(0, 0, 2, 1))

    /** The owner's shape: Library mode, two Home pages, an All-apps page, a dock with pins and a panel widget. */
    private val dock =
        DockModel(
            capacity = 5,
            items = listOf(AppShortcutItem(LauncherItemId("dock-phone"), appIdentity("phone"), "Phone")),
            panel = LauncherPage(LauncherPageId("panel"), grid = GridDimensions(4, 2), items = listOf(panelWidget)),
        )
    private val layout =
        HomeLayout(
            viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
            pages =
                listOf(
                    LauncherPage(home, grid = GridDimensions(4, 5), items = listOf(app("mail"), folder, widget)),
                    LauncherPage(second, grid = GridDimensions(4, 5), items = listOf(app("maps", column = 3, row = 4))),
                    LauncherPage(
                        library,
                        LauncherPageType.AllApps,
                        GridDimensions(4, 5),
                        listOf(app("library-app:x", "x")),
                    ),
                ),
            selectedPageId = second,
            dock = dock,
        )
    private val legacy =
        HomeLayoutSet(
            activeKey = phone,
            layouts =
                mapOf(
                    phone to layout,
                    landscape to
                        layout.copy(
                            pages = listOf(LauncherPage(home, grid = GridDimensions(6, 3), items = listOf(land))),
                            selectedPageId = home,
                        ),
                ),
            libraryDockEdgesByDeviceClass = mapOf(HomeLayoutDeviceClass.PHONE to DockPosition.RIGHT),
        )

    private fun open(set: HomeLayoutSet = legacy): PoolHomeCommit =
        PoolHomeSync.commit(PoolCutover.resync(null, set).state, null, set, ids)

    private fun PoolHomeCommit.edit(change: (HomeLayout) -> HomeLayout): PoolHomeCommit =
        PoolHomeSync.commit(state, shown, shown.withActiveLayout(change(shown.activeLayout)), ids)

    /** A process restart: the pool through its codec, the `HomeLayoutSet` store as [PoolHomeSync.stored] left it. */
    private fun PoolHomeCommit.restart(): PoolHomeCommit {
        val decoded = PoolStoreCodec.decode(PoolStoreCodec.encode(state)).state!!
        return PoolHomeSync.commit(decoded, null, PoolHomeSync.stored(shown, decoded.pools.keys), ids)
    }

    private fun HomeLayout.page(id: LauncherPageId) = pages.first { it.id == id }

    private fun HomeLayout.withPage(page: LauncherPage) = copy(pages = pages.map { if (it.id == page.id) page else it })

    private fun HomeLayout.homeItems(): List<LauncherItem> =
        pages.filter { it.type == LauncherPageType.Home }.flatMap { it.items }

    /** Ids as the legacy set had them: the deterministic migration prefixes stripped. */
    private fun String.legacyId() = replace(Regex("^pi:[a-z_]+:[a-z_]+:"), "")

    private fun LauncherItem.legacy(): LauncherItem =
        when (this) {
            is AppShortcutItem -> copy(id = LauncherItemId(id.value.legacyId()))
            is FolderItem -> copy(id = LauncherItemId(id.value.legacyId()))
            is WidgetItem -> copy(id = LauncherItemId(id.value.legacyId()))
        }

    private fun HomeLayout.legacy(): HomeLayout =
        copy(
            pages = pages.map { page -> page.copy(items = page.items.map { it.legacy() }) },
            dock =
                dock.copy(
                    items = dock.items.map { it.legacy() },
                    panel = dock.panel?.let { panel -> panel.copy(items = panel.items.map { it.legacy() }) },
                ),
        )

    @Test
    fun theProjectionAtStartupIsTheLegacyLayout() {
        val opened = open()
        assertEquals(layout, opened.shown.layoutFor(phone).legacy())
        assertEquals(layout.dock, opened.shown.dockFor(HomeLayoutDeviceClass.PHONE).legacyDock())
        assertEquals(legacy.libraryDockEdgesByDeviceClass, opened.shown.libraryDockEdgesByDeviceClass)
        assertEquals(
            DockPosition.RIGHT,
            opened.state.pools.getValue(HomeLayoutDeviceClass.PHONE).dock!!.settings.libraryPosition,
        )
        assertEquals(emptySet(), opened.releasedHostIds)
        opened.state.pools.values.forEach(::assertInvariants)
    }

    private fun DockModel.legacyDock() =
        HomeLayout(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            layout.pages,
            second,
            this,
        ).legacy().dock

    @Test
    fun aLandscapePhoneShowsThePhoneArrangementInItsOwnPageSlots() {
        val shown = open().shown.layoutFor(landscape)
        assertEquals(
            listOf("mail", "folder", "clock", "maps"),
            shown.homeItems().map { it.id.value.legacyId() },
            "N10: the phone's items, not the landscape-only layout's",
        )
        assertEquals(open().shown.dockFor(HomeLayoutDeviceClass.PHONE), shown.dock)
    }

    @Test
    fun theStoredSetHoldsNoPlacedItemsAndKeepsEverythingElse() {
        val opened = open()
        val stored = PoolHomeSync.stored(opened.shown, opened.state.pools.keys)
        stored.layouts.values.forEach { layout ->
            assertTrue(layout.homeItems().isEmpty())
            assertTrue(layout.dock.items.isEmpty() && layout.dock.panel!!.items.isEmpty())
        }
        stored.docks.values.forEach { assertTrue(it.items.isEmpty()) }
        val storedPhone = stored.layoutFor(phone)
        assertEquals(layout.pages.map { it.id }, storedPhone.pages.map { it.id }, "page slots and order kept")
        assertEquals(layout.page(library), storedPhone.page(library), "All-apps page is not the pool's")
        assertEquals(second, storedPhone.selectedPageId)
        assertEquals(
            layout.dock.copy(items = emptyList(), panel = layout.dock.panel!!.copy(items = emptyList())),
            storedPhone.dock,
        )
        assertEquals(opened.shown, opened.restart().shown, "a restart projects the same home")
    }

    @Test
    fun movingAnAppLandsInThePoolSurvivesARestartAndLeavesTheStoredSetAlone() {
        val opened = open()
        val mailId = opened.shown.activeLayout.homeItems().first().id
        val moved =
            opened.edit { l ->
                l.withPage(
                    l.page(home).copy(
                        items =
                            l.page(home).items.map {
                                if (it.id == mailId) it.withPlacement(at(3, 0)) else it
                            },
                    ),
                )
            }
        val placements =
            moved.state.pools.getValue(
                HomeLayoutDeviceClass.PHONE,
            ).arrangements.getValue(PoolHomeSync.workspaceOf(phone))
        assertEquals(at(3, 0), placements.pages.first().placements.first { it.item.value == mailId.value }.at)
        assertEquals(moved.shown, moved.restart().shown)
        assertEquals(
            PoolHomeSync.stored(opened.shown, opened.state.pools.keys),
            PoolHomeSync.stored(moved.shown, moved.state.pools.keys),
            "HomeLayoutSet is not written for placed items",
        )
        assertEquals(
            listOf(mailId),
            moved.shown.layoutFor(landscape).homeItems().filter { it.placement == at(3, 0) }.map { it.id },
            "the landscape key sharing the arrangement is projected again",
        )
    }

    @Test
    fun addingAnAppKeepsTheEngineIdAndSurvivesARestart() {
        val added =
            open().edit {
                    l ->
                l.withPage(l.page(second).copy(items = l.page(second).items + app("shortcut-9", "cam", 1, 1)))
            }
        assertTrue(added.shown.activeLayout.page(second).items.any { it.id.value == "shortcut-9" })
        assertEquals(added.shown, added.restart().shown)
    }

    @Test
    fun folderCreateRenameAndEntryRemovalEditTheOnePoolFolder() {
        val opened = open()
        val folderId = opened.shown.activeLayout.homeItems().first { it is FolderItem }.id
        val renamed =
            opened.edit { l ->
                l.withPage(
                    l.page(home).copy(
                        items =
                            l.page(home).items.map {
                                if (it is FolderItem) it.copy(label = "Play", items = emptyList()) else it
                            },
                    ),
                )
            }
        val stored =
            renamed.state.pools.getValue(
                HomeLayoutDeviceClass.PHONE,
            ).items.getValue(PoolItemId(folderId.value))
        assertEquals(PoolFolder(PoolItemId(folderId.value), "Play", emptyList()), stored)
        val created =
            renamed.edit { l ->
                val newFolder = FolderItem(LauncherItemId("folder-2"), "Work", listOf(app("e9", "slack")), at(2, 2))
                l.withPage(l.page(second).copy(items = l.page(second).items + newFolder))
            }
        assertTrue(PoolItemId("folder-2") in created.state.pools.getValue(HomeLayoutDeviceClass.PHONE).items)
        assertEquals(created.shown, created.restart().shown)
    }

    @Test
    fun widgetAddResizeAndRemoveReleaseTheHostIdOnlyWhenTheLastReferenceGoes() {
        val opened = open()
        val added =
            opened.edit { l ->
                l.withPage(
                    l.page(
                        second,
                    ).copy(
                        items = l.page(second).items + notes,
                    ),
                )
            }
        val resized =
            added.edit { l ->
                l.withPage(
                    l.page(second).copy(
                        items =
                            l.page(
                                second,
                            ).items.map { if (it.id.value == "widget-7") it.withPlacement(at(0, 0, 3, 2)) else it },
                    ),
                )
            }
        assertEquals(resized.shown, resized.restart().shown)
        assertEquals(
            at(0, 0, 3, 2),
            resized.shown.activeLayout.page(second).items.first {
                it.id.value == "widget-7"
            }.placement,
        )
        val removed =
            resized.edit {
                    l ->
                l.withPage(l.page(second).copy(items = l.page(second).items.filterNot { it.id.value == "widget-7" }))
            }
        assertEquals(setOf(HostedWidgetId(77)), removed.releasedHostIds)
        assertFalse(PoolHostIds.isReferenced(HostedWidgetId(77), removed.state, removed.shown))
        assertTrue(PoolHostIds.isReferenced(HostedWidgetId(42), removed.state, removed.shown))
    }

    @Test
    fun removingAWidgetAnotherWorkspaceStillShowsReleasesNothing() {
        val opened = open()
        val pool = opened.state.pools.getValue(HomeLayoutDeviceClass.PHONE)
        val fork =
            PoolArrangements.fork(
                pool,
                WorkspaceId("mine"),
                listOf(PoolHomeSync.workspaceOf(phone)),
                ids,
            ).done().pool
        val forked = opened.state.copy(pools = opened.state.pools + (HomeLayoutDeviceClass.PHONE to fork))
        val start = PoolHomeSync.reproject(forked, opened.shown, ids)
        val removed =
            start.edit {
                    l ->
                l.withPage(l.page(home).copy(items = l.page(home).items.filterNot { it is WidgetItem }))
            }
        assertEquals(emptySet(), removed.releasedHostIds)
        assertTrue(PoolHostIds.isReferenced(HostedWidgetId(42), removed.state, removed.shown), "never delete a live id")
        assertTrue(WorkspaceId("mine") in removed.state.pools.getValue(HomeLayoutDeviceClass.PHONE).arrangements)
    }

    @Test
    fun pageAddDeleteAndReorderBecomeTheArrangementsPages() {
        val third = LauncherPageId("page-3")
        val added =
            open().edit {
                    l ->
                l.copy(pages = l.pages.take(2) + LauncherPage(third, grid = GridDimensions(4, 5)) + l.pages.drop(2))
            }
        val ws = PoolHomeSync.workspaceOf(phone)

        fun pages(c: PoolHomeCommit) =
            c.state.pools.getValue(
                HomeLayoutDeviceClass.PHONE,
            ).arrangements.getValue(ws).pages.map {
                it.id
            }
        assertEquals(listOf(home, second, third), pages(added))
        val reordered =
            added.edit {
                    l ->
                l.copy(pages = listOf(l.page(third), l.page(home), l.page(second), l.page(library)))
            }
        assertEquals(listOf(third, home, second), pages(reordered))
        val deleted =
            reordered.edit {
                    l ->
                l.copy(pages = l.pages.filterNot { it.id == second }, selectedPageId = home)
            }
        assertEquals(listOf(third, home), pages(deleted))
        assertEquals(listOf(third, home, library), deleted.restart().shown.activeLayout.pages.map { it.id })
    }

    @Test
    fun dockPinsOrderPanelAndSettingsGoToThePoolDock() {
        val opened = open()
        val pinned =
            opened.edit { l ->
                l.copy(
                    dock =
                        l.dock.copy(
                            items = listOf(app("dock-2", "camera").copy(placement = null)) + l.dock.items,
                            iconSizeDp = 52,
                        ),
                )
            }
        val poolDock = pinned.state.pools.getValue(HomeLayoutDeviceClass.PHONE).dock!!
        assertEquals("dock-2", poolDock.pins.first().value)
        assertEquals(52, poolDock.settings.iconSizeDp)
        assertEquals(DockPosition.RIGHT, poolDock.settings.libraryPosition, "Library's edge is kept")
        assertEquals(
            pinned.shown.dockFor(HomeLayoutDeviceClass.PHONE),
            pinned.shown.dockFor(HomeLayoutDeviceClass.PHONE_LANDSCAPE),
        )
        val panelOff = pinned.edit { l -> l.copy(dock = l.dock.copy(panel = null)) }
        assertEquals(setOf(HostedWidgetId(43)), panelOff.releasedHostIds)
        assertEquals(panelOff.shown, panelOff.restart().shown)
        val edge =
            PoolHomeSync.commit(
                panelOff.state,
                panelOff.shown,
                panelOff.shown.copy(
                    libraryDockEdgesByDeviceClass = mapOf(HomeLayoutDeviceClass.PHONE to DockPosition.LEFT),
                ),
                ids,
            )
        assertEquals(
            DockPosition.LEFT,
            edge.state.pools.getValue(HomeLayoutDeviceClass.PHONE).dock!!.settings.libraryPosition,
        )
    }

    @Test
    fun aLibraryRelayoutThatReMintsTheSameIdsIsStable() {
        val relayout: (HomeLayout) -> HomeLayout = { l ->
            // Library compaction strips its own apps and places them again with `library-app:` ids.
            val stripped = l.page(second).items.filterNot { it.id.value.contains("library-app:") }
            l.withPage(l.page(second).copy(items = stripped + app("library-app:y", "y", 2, 2)))
        }
        val first = open().edit(relayout)
        val second = first.edit(relayout)
        assertEquals(first.state, second.state)
        assertEquals(first.shown, second.shown)
        assertTrue(first.shown.activeLayout.homeItems().any { it.id.value == "library-app:y" })
    }

    @Test
    fun selectionAndEditModeNeverTouchThePool() {
        val opened = open()
        val browsing = opened.edit { it.copy(selectedPageId = home, editMode = HomeEditMode.ManagingPages) }
        assertEquals(opened.state, browsing.state)
        assertEquals(home, browsing.shown.activeLayout.selectedPageId)
    }

    @Test
    fun theActiveKeysEditWinsOverAStaleSiblingOfTheSameArrangement() {
        val opened = open()
        val stale = opened.shown
        val edited =
            stale.withActiveLayout(
                stale.activeLayout.let {
                        l ->
                    l.withPage(l.page(second).copy(items = emptyList()))
                },
            )
        val sibling =
            edited.withLayout(
                landscape,
                edited.layoutFor(landscape).let {
                        l ->
                    l.copy(pages = l.pages.map { it.copy(items = emptyList()) })
                },
            )
        val result = PoolHomeSync.commit(opened.state, stale, sibling, ids)
        assertTrue(result.shown.activeLayout.page(home).items.isNotEmpty(), "the landscape sibling did not overwrite")
        assertTrue(result.shown.activeLayout.page(second).items.isEmpty())
    }

    @Test
    fun aNewDeviceClassIsAdoptedWithItsEngineIds() {
        val opened = open()
        val tablet = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.TABLET)
        val tabletLayout =
            layout.copy(
                pages = listOf(LauncherPage(home, grid = GridDimensions(6, 6), items = listOf(app("t")))),
                selectedPageId = home,
            )
        val result = PoolHomeSync.commit(opened.state, opened.shown, opened.shown.withLayout(tablet, tabletLayout), ids)
        val pool = result.state.pools.getValue(HomeLayoutDeviceClass.TABLET)
        assertTrue(PoolItemId("t") in pool.items)
        assertTrue(pool.dock != null)
        assertEquals(listOf("t"), result.shown.layoutFor(tablet).homeItems().map { it.id.value })
        assertEquals(result.shown, result.restart().shown)
    }

    @Test
    fun uninstallPruningDropsThePackageEverywhereButLeavesWidgets() {
        val opened = open()
        val pool = opened.state.pools.getValue(HomeLayoutDeviceClass.PHONE)
        val fork =
            PoolArrangements.fork(
                pool,
                WorkspaceId("mine"),
                listOf(PoolHomeSync.workspaceOf(phone)),
                ids,
            ).done().pool
        val start =
            PoolHomeSync.reproject(
                opened.state.copy(pools = opened.state.pools + (HomeLayoutDeviceClass.PHONE to fork)),
                opened.shown,
                ids,
            )
        val pruned =
            listOf("mail", "phone", "g1").fold(start) { c, pkg ->
                PoolHomeSync.pruneUninstalled(
                    c.state,
                    c.shown,
                    appIdentity(pkg).packageName,
                    AppProfile.personal(),
                    ids,
                )
            }
        val shownItems = pruned.shown.activeLayout.allItems()
        assertTrue(
            shownItems.none { it is AppShortcutItem && it.appIdentity.packageName.value in setOf("mail", "phone") },
        )
        assertTrue(
            shownItems.filterIsInstance<FolderItem>().all {
                    f ->
                f.items.none { it.appIdentity.packageName.value == "g1" }
            },
        )
        assertTrue(shownItems.any { it is WidgetItem })
        val prunedPool = pruned.state.pools.getValue(HomeLayoutDeviceClass.PHONE)
        assertTrue(
            prunedPool.items.values.none { it is PoolApp && it.appIdentity.packageName.value == "mail" },
            "the fork too",
        )
        val otherProfile =
            PoolHomeSync.pruneUninstalled(
                start.state,
                start.shown,
                appIdentity("maps").packageName,
                AppProfile.work(),
                ids,
            )
        assertEquals(start.state, otherProfile.state, "another profile's removal changes nothing")
    }

    @Test
    fun aPreCutoverBackupRebuildsTheHomeFromItsLayoutsAndKeepsOtherArrangements() {
        val opened = open()
        val pool = opened.state.pools.getValue(HomeLayoutDeviceClass.PHONE)
        val withFork =
            opened.state.copy(
                pools =
                    opened.state.pools +
                        (HomeLayoutDeviceClass.PHONE to fork(pool)),
            )
        val older = legacy.withActiveLayout(layout.withPage(layout.page(second).copy(items = listOf(app("old")))))
        val restored = PoolHomeSync.restore(withFork, null, older, homeFromPool = false, ids)
        assertEquals(listOf("old"), restored.shown.activeLayout.page(second).items.map { it.id.value.legacyId() })
        assertTrue(WorkspaceId("mine") in restored.state.pools.getValue(HomeLayoutDeviceClass.PHONE).arrangements)
        restored.state.pools.values.forEach(::assertInvariants)
    }

    @Test
    fun aCutOverBackupRestoresItsPoolAndTakesWidgetHostIdsBackFromItsLayouts() {
        val edited =
            open().edit {
                    l ->
                l.withPage(l.page(second).copy(items = l.page(second).items + app("shortcut-5", "cam", 1, 1)))
            }
        val exported = WorkspaceBackup.restorePool(WorkspaceBackup.exportPool(edited.state))!!.state
        val exportedWidgets = exported.pools.values.flatMap { it.items.values }.filterIsInstance<PoolWidget>()
        assertTrue(exportedWidgets.none { it.hostedId != null }, "a backup's pool carries no host ids")
        val restored = PoolHomeSync.restore(PoolStoreState(), exported, edited.shown, homeFromPool = true, ids)
        assertEquals(edited.shown, restored.shown)
        assertTrue(PoolHostIds.isReferenced(HostedWidgetId(42), restored.state, null))
        assertTrue(PoolHostIds.isReferenced(HostedWidgetId(43), restored.state, null))
    }

    @Test
    fun resyncReplacesAStaleMigratedArrangementAndIsIdempotent() {
        val stale = PoolCutover.ensureMigrated(null, legacy).state
        val edited = legacy.withActiveLayout(layout.withPage(layout.page(second).copy(items = listOf(app("later")))))
        val once = PoolCutover.resync(stale, edited)
        val twice = PoolCutover.resync(once.state, edited)
        assertEquals(once.state, twice.state)
        assertFalse(twice.changed)
        val shown = PoolHomeSync.commit(once.state, null, edited, ids).shown
        assertEquals(listOf("later"), shown.activeLayout.page(second).items.map { it.id.value.legacyId() })
        assertNotEquals(stale, once.state)
    }

    @Test
    fun resyncNeverReplacesAPoolThatHoldsTheHomeWithASetWithoutPlacedItems() {
        val opened = open()
        val stripped = PoolHomeSync.stored(opened.shown, opened.state.pools.keys)
        assertTrue(PoolCutover.holdsNoPlacedItems(stripped))
        assertTrue(PoolCutover.holdsHome(opened.state))
        val result = PoolCutover.resync(opened.state, stripped)
        assertEquals(opened.state, result.state)
        assertFalse(result.changed)
        assertFalse(PoolCutover.holdsNoPlacedItems(legacy))
        assertNotEquals(
            opened.state,
            PoolCutover.resync(
                opened.state,
                legacy.withActiveLayout(layout.withPage(layout.page(home).copy(items = listOf(land)))),
            ).state,
        )
    }

    @Test
    fun anEditOnAStaleProjectionDoesNotReCreateAnItemRemovedElsewhere() {
        val opened = open()
        val pool = opened.state.pools.getValue(HomeLayoutDeviceClass.PHONE)
        val ws = PoolHomeSync.workspaceOf(phone)
        val mail = pool.items.keys.first { it.value.endsWith(":mail") }
        val removed = (PoolRemoval.remove(pool, ws, mail) as PoolResult.Done).edit.pool
        val elsewhere = opened.state.copy(pools = opened.state.pools + (HomeLayoutDeviceClass.PHONE to removed))
        // The shell still shows `mail` and moves `maps`.
        val stale = opened.shown
        val moved =
            stale.withActiveLayout(
                stale.activeLayout.withPage(
                    stale.activeLayout.page(second).copy(
                        items = stale.activeLayout.page(second).items.map { it.withPlacement(at(0, 0)) },
                    ),
                ),
            )
        val result = PoolHomeSync.commit(elsewhere, stale, moved, ids)
        assertTrue(result.shown.activeLayout.homeItems().none { it.id.value == mail.value }, "not re-created")
        assertEquals(at(0, 0), result.shown.activeLayout.page(second).items.single().placement)
    }

    @Test
    fun aRestoreReportsTheHostIdsOfWidgetsTheRestoredPoolNoLongerHolds() {
        val opened = open()
        val older = legacy.withActiveLayout(layout.withPage(layout.page(home).copy(items = listOf(land))))
        val restored = PoolHomeSync.restore(opened.state, null, older, homeFromPool = false, ids)
        assertEquals(setOf(HostedWidgetId(42)), restored.releasedHostIds, "the dock panel widget is still restored")
    }

    @Test
    fun rebuildingThePoolFromItsOwnProjectionKeepsTheIds() {
        val opened = open()
        val snapshot = opened.shown
        val once = PoolCutover.ensureMigrated(null, snapshot).state
        val twice = PoolCutover.ensureMigrated(null, snapshot).state
        assertEquals(once, twice)
        val phone = HomeLayoutDeviceClass.PHONE
        assertEquals(
            opened.state.pools.getValue(phone).items.keys,
            once.pools.getValue(phone).items.keys,
            "no stacked prefix",
        )
        assertEquals(snapshot, PoolHomeSync.commit(once, null, snapshot, ids).shown)
    }

    @Test
    fun aPoolMigratedFromAStrippedSetDoesNotHoldAHome() {
        val opened = open()
        val stripped = PoolHomeSync.stored(opened.shown, opened.state.pools.keys)
        val empty = PoolCutover.ensureMigrated(null, stripped).state
        assertFalse(PoolCutover.holdsHome(empty), "arrangements and a dock, but nothing in them")
        assertTrue(PoolCutover.holdsHome(opened.state))
    }
}
