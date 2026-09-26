package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** One dock per device class, shared by every mode (#1205), and the migration from per-mode docks. */
class HomeLayoutDockUnificationTest {
    @Test
    fun aPinMadeInLibraryShowsInCardsAndStandard() {
        val library = threeModes(activeMode = LIBRARY)
        val pinned =
            library.withActiveLayout(
                library.activeLayout.copy(dock = library.activeLayout.dock.copy(items = listOf(app("mail")))),
            )

        LauncherViewMode.entries.forEach { mode ->
            assertEquals(listOf(app("mail")), pinned.layoutFor(key(mode)).dock.items, "$mode")
            assertEquals(listOf(app("mail")), pinned.selectMode(mode).activeLayout.dock.items, "$mode")
        }
    }

    @Test
    fun aPinMadeInCardsShowsInLibraryAndStandard() {
        val cards = threeModes(activeMode = CARDS)
        val pinned =
            cards.withActiveLayout(
                cards.activeLayout.copy(dock = cards.activeLayout.dock.copy(items = listOf(app("camera")))),
            )

        assertEquals(listOf(app("camera")), pinned.selectMode(LIBRARY).activeLayout.dock.items)
        assertEquals(listOf(app("camera")), pinned.selectMode(STANDARD).activeLayout.dock.items)
    }

    @Test
    fun aDockSettingChangedInOneModeAppliesInEveryMode() {
        val standard = threeModes(activeMode = STANDARD)
        val edited =
            standard.withActiveLayout(
                standard.activeLayout.copy(
                    dock = standard.activeLayout.dock.copy(capacity = 7, iconSizeDp = 52, notificationSlotCount = 4),
                ),
            )

        LauncherViewMode.entries.forEach { mode ->
            val dock = edited.layoutFor(key(mode)).dock
            assertEquals(7, dock.capacity, "$mode")
            assertEquals(52, dock.iconSizeDp, "$mode")
            assertEquals(4, dock.notificationSlotCount, "$mode")
        }
    }

    @Test
    fun selectingAModeStoresTheSharedDockNotWhateverThatModeLastHeld() {
        val library = threeModes(activeMode = LIBRARY)
        val pinned =
            library.withActiveLayout(
                library.activeLayout.copy(dock = library.activeLayout.dock.copy(items = listOf(app("mail")))),
            )

        LauncherViewMode.entries.forEach { mode ->
            val selected = pinned.selectMode(mode)
            // Not just once read back through layoutFor/activeLayout (which normalizes either way):
            // the layout selectMode itself just stored already carries the shared dock too, so a
            // reader of [HomeLayoutSet.layouts] directly (persistence, a future caller) never sees a
            // mode's stale dock sitting out of step with every other mode's (#1206
            // dock-transition-consistency).
            assertEquals(listOf(app("mail")), selected.layouts.getValue(key(mode)).dock.items, "$mode")
        }
    }

    @Test
    fun aModeCreatedOnFirstVisitShowsTheSharedDock() {
        val dock = DockModel(capacity = 6, items = listOf(app("phone")), position = DockPosition.RIGHT)
        val standardOnly = HomeLayoutSet.standard().withActiveDock(dock)

        val cards = standardOnly.selectMode(CARDS)

        assertEquals(dock, cards.activeLayout.dock)
    }

    @Test
    fun deviceClassesKeepTheirOwnDock() {
        val phoneDock = DockModel(capacity = 4, items = listOf(app("phone")))
        val set =
            HomeLayoutSet.standard()
                .withActiveDock(phoneDock)
                .selectDeviceClass(HomeLayoutDeviceClass.TABLET)

        assertEquals(HomeLayoutDefaults.standard(HomeLayoutDeviceClass.TABLET).dock, set.activeLayout.dock)
        assertEquals(phoneDock, set.dockFor(HomeLayoutDeviceClass.PHONE))
    }

    @Test
    fun movingTheDockToASideInOneModeRefitsTheOtherModesPages() {
        val lastColumn = GridPlacement(cell = GridCell(column = 3, row = 0))
        val libraryLayout =
            layout(LIBRARY).let { layout ->
                layout.copy(pages = listOf(layout.selectedPage.copy(items = listOf(app("edge", lastColumn)))))
            }
        val set =
            HomeLayoutSet(
                activeKey = key(STANDARD),
                layouts = mapOf(key(STANDARD) to layout(STANDARD), key(LIBRARY) to libraryLayout),
            )

        val moved =
            set.withActiveLayout(
                set.activeLayout.copy(dock = set.activeLayout.dock.copy(position = DockPosition.LEFT)),
            )
        val library = moved.layoutFor(key(LIBRARY))

        assertEquals(DockPosition.LEFT, library.dock.position)
        assertEquals(3, library.workspaceGrid.columns)
        assertTrue(library.pages.all { page -> page.grid == library.workspaceGrid })
        assertTrue(library.everyItemOnTheWorkspace())
        assertEquals(listOf(app("edge").appIdentity), library.pageApps())
    }

    @Test
    fun aTemplateSeedKeepsTheSharedDock() {
        val dock = DockModel(capacity = 6, items = listOf(app("phone")))
        val set = HomeLayoutSet.standard().withActiveDock(dock)

        val seeded = set.withLayoutKeepingDock(key(CARDS), layout(CARDS))

        assertEquals(dock, seeded.layoutFor(key(CARDS)).dock)
        assertEquals(dock, seeded.activeLayout.dock)
    }

    @Test
    fun theSharedDockIsOfferedOnlyTheEdgesEveryModeCanDraw() {
        assertEquals(listOf(DockPosition.LEFT, DockPosition.RIGHT, DockPosition.BOTTOM), sharedDockPositions)
    }

    // --- Migration ---------------------------------------------------------------------------

    @Test
    fun migrationKeepsTheActiveModesDockAndRescuesDivergentPinsForEveryModeAndEdge() {
        listOf(DockPosition.BOTTOM, DockPosition.LEFT).forEach { edge ->
            LauncherViewMode.entries.forEach { active ->
                val set = divergentLegacySet(activeMode = active, activeEdge = edge)

                val migrated = set.withLegacyDocksUnified()
                val context = "active $active, edge $edge"

                assertEquals(dockFor(active).copy(position = edge), migrated.dockFor(PHONE), context)
                LauncherViewMode.entries.forEach { mode ->
                    assertEquals(dockFor(active).copy(position = edge), migrated.layoutFor(key(mode)).dock, context)
                }
                // Every pin some mode's dock held is still reachable somewhere visible.
                val visible =
                    migrated.dockFor(PHONE).items.map { item -> (item as AppShortcutItem).appIdentity } +
                        migrated.layoutFor(key(STANDARD)).pageApps() +
                        migrated.layoutFor(key(LIBRARY)).pageApps()
                listOf("shared", "standard-only", "library-only", "cards-only").forEach { name ->
                    assertTrue(app(name).appIdentity in visible, "$name lost ($context)")
                }
                // Rescued items never land off the workspace the shared dock leaves.
                LauncherViewMode.entries.forEach { mode ->
                    assertTrue(migrated.layoutFor(key(mode)).everyItemOnTheWorkspace(), "$mode placements ($context)")
                }
            }
        }
    }

    @Test
    fun aPinOnlyAnotherHomeModesDockHeldGoesToThatModesOwnHomePage() {
        val migrated = divergentLegacySet(activeMode = STANDARD).withLegacyDocksUnified()

        assertTrue(app("library-only").appIdentity in migrated.layoutFor(key(LIBRARY)).pageApps())
        assertFalse(app("library-only").appIdentity in migrated.layoutFor(key(STANDARD)).pageApps())
    }

    @Test
    fun aPinOnlyTheCardsDockHeldGoesToTheShowingModesHomePage() {
        val migrated = divergentLegacySet(activeMode = STANDARD).withLegacyDocksUnified()

        assertTrue(app("cards-only").appIdentity in migrated.layoutFor(key(STANDARD)).pageApps())
        assertTrue(migrated.layoutFor(key(CARDS)).pageApps().isEmpty())
    }

    @Test
    fun whenCardsWasShowingEachHomeModeGetsItsOwnPinsBack() {
        val migrated = divergentLegacySet(activeMode = CARDS).withLegacyDocksUnified()

        assertEquals(listOf(app("shared"), app("cards-only")), migrated.dockFor(PHONE).items)
        assertTrue(app("standard-only").appIdentity in migrated.layoutFor(key(STANDARD)).pageApps())
        assertTrue(app("library-only").appIdentity in migrated.layoutFor(key(LIBRARY)).pageApps())
        assertFalse(app("shared").appIdentity in migrated.layoutFor(key(STANDARD)).pageApps())
    }

    @Test
    fun aPinAlreadyOnTheHomePageIsNotDuplicated() {
        val onHome = app("library-only", GridPlacement(GridCell(column = 0, row = 0)))
        val set =
            divergentLegacySet(activeMode = STANDARD).let { set ->
                val library = set.layouts.getValue(key(LIBRARY))
                val onHomePage = library.selectedPage.copy(items = listOf(onHome))
                set.copy(layouts = set.layouts + (key(LIBRARY) to library.copy(pages = listOf(onHomePage))))
            }

        val migrated = set.withLegacyDocksUnified()

        assertEquals(listOf(app("library-only").appIdentity), migrated.layoutFor(key(LIBRARY)).pageApps())
    }

    @Test
    fun pinsThatFindNoFreeCellGoIntoAFromDockFolder() {
        val full = layout(STANDARD).fullOf("filler")
        val set =
            HomeLayoutSet(
                activeKey = key(STANDARD),
                layouts =
                    mapOf(
                        key(STANDARD) to full.copy(dock = DockModel(capacity = 5)),
                        key(CARDS) to layout(CARDS).copy(dock = dockOf(app("a"), app("b"))),
                    ),
            )

        val migrated = set.withLegacyDocksUnified().layoutFor(key(STANDARD))

        val folder =
            migrated.pages.flatMap { page -> page.items }.filterIsInstance<FolderItem>().single()
        assertEquals(FROM_DOCK_FOLDER_LABEL, folder.label)
        assertEquals(listOf(app("a").appIdentity, app("b").appIdentity), folder.items.map { item -> item.appIdentity })
        // The folder needed a cell too, so it went on a page of its own rather than being dropped.
        assertEquals(2, migrated.pages.size)
    }

    @Test
    fun aRescuedPinGetsAFreshIdWhenItsOldOneIsTaken() {
        val clash = app("other").copy(id = LauncherItemId("shared"))
        val set =
            HomeLayoutSet(
                activeKey = key(STANDARD),
                layouts =
                    mapOf(
                        key(STANDARD) to layout(STANDARD).copy(dock = dockOf(app("shared"))),
                        key(CARDS) to layout(CARDS).copy(dock = dockOf(clash)),
                    ),
            )

        val migrated = set.withLegacyDocksUnified().layoutFor(key(STANDARD))

        val rescued = migrated.pages.flatMap { page -> page.items }.single() as AppShortcutItem
        assertEquals(clash.appIdentity, rescued.appIdentity)
        assertFalse(rescued.id == LauncherItemId("shared"))
    }

    @Test
    fun aWidgetOnlyAnotherModesDockPanelHeldIsRescuedToo() {
        val widget =
            WidgetItem(
                id = LauncherItemId("clock"),
                appWidgetId = HostedWidgetId(7),
                label = "Clock",
                placement = GridPlacement(GridCell(0, 0), GridSpan(columns = 2, rows = 1)),
            )
        val panel = LauncherPage(id = LauncherPageId("dock-panel"), grid = GridDimensions(4, 2), items = listOf(widget))
        val set =
            HomeLayoutSet(
                activeKey = key(STANDARD),
                layouts =
                    mapOf(
                        key(STANDARD) to layout(STANDARD),
                        key(LIBRARY) to layout(LIBRARY).copy(dock = DockModel(capacity = 5, panel = panel)),
                    ),
            )

        val migrated = set.withLegacyDocksUnified()

        assertEquals(null, migrated.dockFor(PHONE).panel)
        val libraryItems = migrated.layoutFor(key(LIBRARY)).pages.flatMap { page -> page.items }
        assertEquals(listOf(HostedWidgetId(7)), libraryItems.filterIsInstance<WidgetItem>().map { it.appWidgetId })
    }

    @Test
    fun aTopEdgeOnlyCardsCouldDrawComesDownToTheBottom() {
        val set =
            HomeLayoutSet(
                activeKey = key(CARDS),
                layouts =
                    mapOf(
                        key(CARDS) to layout(CARDS).copy(dock = DockModel(capacity = 5, position = DockPosition.TOP)),
                        key(STANDARD) to layout(STANDARD),
                    ),
            )

        assertEquals(DockPosition.BOTTOM, set.withLegacyDocksUnified().dockFor(PHONE).position)
    }

    @Test
    fun matchingDocksMigrateUnchanged() {
        val dock = DockModel(capacity = 5, items = listOf(app("phone")))
        val set =
            HomeLayoutSet(
                activeKey = key(LIBRARY),
                layouts = LauncherViewMode.entries.associate { mode -> key(mode) to layout(mode).copy(dock = dock) },
            )

        val migrated = set.withLegacyDocksUnified()

        assertEquals(set.layouts, migrated.layouts)
        assertEquals(dock, migrated.dockFor(PHONE))
    }

    // --- Fixtures ----------------------------------------------------------------------------

    private fun threeModes(activeMode: LauncherViewMode): HomeLayoutSet =
        HomeLayoutSet(
            activeKey = key(activeMode),
            layouts = LauncherViewMode.entries.associate { mode -> key(mode) to layout(mode) },
        )

    /** Every mode's dock shares one pin and holds one of its own, with its own capacity. */
    private fun divergentLegacySet(
        activeMode: LauncherViewMode,
        activeEdge: DockPosition = DockPosition.BOTTOM,
    ): HomeLayoutSet =
        HomeLayoutSet(
            activeKey = key(activeMode),
            layouts =
                LauncherViewMode.entries.associate { mode ->
                    val dock = if (mode == activeMode) dockFor(mode).copy(position = activeEdge) else dockFor(mode)
                    key(mode) to layout(mode).copy(dock = dock)
                },
        )

    private fun dockFor(mode: LauncherViewMode): DockModel =
        when (mode) {
            LauncherViewMode.STANDARD_APP_DRAWER ->
                DockModel(capacity = 4, items = listOf(app("shared"), app("standard-only")))

            LauncherViewMode.HOME_SCREEN_LIBRARY ->
                DockModel(capacity = 5, iconSizeDp = 40, items = listOf(app("shared"), app("library-only")))

            LauncherViewMode.CARD_INTERFACE ->
                DockModel(
                    capacity = 6,
                    showNotificationCards = true,
                    position = DockPosition.RIGHT,
                    items = listOf(app("shared"), app("cards-only")),
                )
        }

    private fun HomeLayoutSet.withActiveDock(dock: DockModel): HomeLayoutSet =
        withActiveLayout(activeLayout.copy(dock = dock))

    private fun dockOf(vararg items: LauncherItem): DockModel = DockModel(capacity = 5, items = items.toList())

    private fun HomeLayout.everyItemOnTheWorkspace(): Boolean =
        pages.flatMap { page -> page.items }.all { item -> workspaceGrid.holds(item.placement) }

    private fun layout(mode: LauncherViewMode): HomeLayout = HomeLayoutDefaults.standard().copy(viewMode = mode)

    private fun HomeLayout.fullOf(prefix: String): HomeLayout =
        copy(
            pages =
                listOf(
                    selectedPage.copy(
                        items =
                            (0 until workspaceGrid.rows).flatMap { row ->
                                (0 until workspaceGrid.columns).map { column ->
                                    app("$prefix-$column-$row", GridPlacement(GridCell(column = column, row = row)))
                                }
                            },
                    ),
                ),
        )

    private fun HomeLayout.pageApps(): List<AppIdentity> =
        pages
            .flatMap { page -> page.items }
            .filterNot { item -> item is AppShortcutItem && item.label.startsWith("filler") }
            .flatMap { item ->
                when (item) {
                    is AppShortcutItem -> listOf(item.appIdentity)
                    is FolderItem -> item.items.map { app -> app.appIdentity }
                    is WidgetItem -> emptyList()
                }
            }

    private fun key(mode: LauncherViewMode): HomeLayoutKey = HomeLayoutKey(viewMode = mode, deviceClass = PHONE)

    private fun app(
        name: String,
        placement: GridPlacement? = null,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(name),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
            placement = placement,
        )

    private companion object {
        val PHONE = HomeLayoutDeviceClass.PHONE
        val STANDARD = LauncherViewMode.STANDARD_APP_DRAWER
        val LIBRARY = LauncherViewMode.HOME_SCREEN_LIBRARY
        val CARDS = LauncherViewMode.CARD_INTERFACE
    }
}
