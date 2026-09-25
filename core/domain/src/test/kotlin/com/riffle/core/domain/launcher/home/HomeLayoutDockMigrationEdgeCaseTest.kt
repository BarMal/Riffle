package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The per-mode dock migration (#1205) at its edges: widgets that fit nowhere, device classes other
 * than a phone, and rescued items whose ids all collide.
 */
class HomeLayoutDockMigrationEdgeCaseTest {
    @Test
    fun anOversizedPanelWidgetRescuedOntoANarrowSideDockGridIsKept() {
        // Wider (4) than the 3 columns a left dock leaves a 4-column phone grid, with a minimum span
        // that allows nothing narrower -- no span the widget itself permits fits anywhere.
        val widget = widget(id = "wide", hostId = 11, span = GridSpan(columns = 4, rows = 2), minSpan = GridSpan(4, 1))
        val set =
            HomeLayoutSet(
                activeKey = key(STANDARD),
                layouts =
                    mapOf(
                        key(STANDARD) to
                            layout(STANDARD).copy(dock = DockModel(capacity = 5, position = DockPosition.LEFT)),
                        key(LIBRARY) to layout(LIBRARY).copy(dock = DockModel(capacity = 5, panel = panelOf(widget))),
                    ),
            )

        val migrated = set.withLegacyDocksUnified()
        val library = migrated.layoutFor(key(LIBRARY))

        val kept = library.pages.flatMap { page -> page.items }.filterIsInstance<WidgetItem>().single()
        assertEquals(HostedWidgetId(11), kept.appWidgetId)
        assertEquals(3, library.workspaceGrid.columns)
        assertTrue(library.workspaceGrid.holds(kept.placement), "clamped onto the workspace")
        assertTrue(migrated.hostsWidget(HostedWidgetId(11)), "its host id stays referenced")
    }

    @Test
    fun aPanelWidgetThatFitsNoFreeCellGoesOnAPageOfItsOwn() {
        val widget = widget(id = "clock", hostId = 12, span = GridSpan(columns = 2, rows = 2))
        val full = layout(STANDARD).fullOf("filler", GridDimensions(columns = 4, rows = 5))
        val set =
            HomeLayoutSet(
                activeKey = key(STANDARD),
                layouts =
                    mapOf(
                        key(STANDARD) to full.copy(dock = DockModel(capacity = 5)),
                        key(CARDS) to layout(CARDS).copy(dock = DockModel(capacity = 5, panel = panelOf(widget))),
                    ),
            )

        val standard = set.withLegacyDocksUnified().layoutFor(key(STANDARD))

        assertEquals(2, standard.pages.size)
        val ownPageWidgets = standard.pages.last().items.filterIsInstance<WidgetItem>()
        assertEquals(listOf(HostedWidgetId(12)), ownPageWidgets.map { it.appWidgetId })
    }

    @Test
    fun migrationWorksForFoldablesAndTabletsWithSideDocks() {
        val deviceClasses = listOf(HomeLayoutDeviceClass.FOLDABLE, HomeLayoutDeviceClass.TABLET)
        val edges = listOf(DockPosition.LEFT, DockPosition.RIGHT)
        deviceClasses
            .flatMap { deviceClass -> edges.map { edge -> deviceClass to edge } }
            .flatMap { (deviceClass, edge) ->
                LauncherViewMode.entries.map { active -> Triple(deviceClass, edge, active) }
            }.forEach { (deviceClass, edge, active) -> assertMigratesWithoutLoss(deviceClass, edge, active) }
    }

    private fun assertMigratesWithoutLoss(
        deviceClass: HomeLayoutDeviceClass,
        edge: DockPosition,
        active: LauncherViewMode,
    ) {
        val context = "$deviceClass, $edge, active $active"
        val migrated = divergentSet(deviceClass, active, edge).withLegacyDocksUnified()

        val sharedDock = dockFor(active).copy(position = edge)
        assertEquals(sharedDock, migrated.dockFor(deviceClass), context)
        val standard = migrated.layoutFor(key(STANDARD, deviceClass))
        val library = migrated.layoutFor(key(LIBRARY, deviceClass))
        assertEquals(
            HomeLayoutSettings.standard(deviceClass).grid.dimensions.columns - 1,
            standard.workspaceGrid.columns,
            context,
        )
        val visible = sharedDock.items.appIdentities() + standard.pageApps() + library.pageApps()
        listOf("shared", "standard-only", "library-only", "cards-only").forEach { name ->
            assertTrue(app(name).appIdentity in visible, "$name lost ($context)")
        }
        assertTrue(standard.everyItemOnTheWorkspace(), "Standard off the workspace ($context)")
        assertTrue(library.everyItemOnTheWorkspace(), "Library off the workspace ($context)")
    }

    @Test
    fun eachDeviceClassMigratesToItsOwnShowingModesDock() {
        val set =
            HomeLayoutSet(
                activeKey = key(CARDS, HomeLayoutDeviceClass.PHONE),
                layouts =
                    divergentSet(HomeLayoutDeviceClass.PHONE, CARDS, DockPosition.BOTTOM).layouts +
                        divergentSet(HomeLayoutDeviceClass.TABLET, LIBRARY, DockPosition.LEFT).layouts,
                preferredModesByDeviceClass =
                    mapOf(HomeLayoutDeviceClass.PHONE to CARDS, HomeLayoutDeviceClass.TABLET to LIBRARY),
            )

        val migrated = set.withLegacyDocksUnified()

        assertEquals(dockFor(CARDS).copy(position = DockPosition.BOTTOM), migrated.dockFor(HomeLayoutDeviceClass.PHONE))
        // The tablet is not the active device, so its preferred mode's dock wins there. Its layouts
        // were built with Library on the left edge, so that is the edge it keeps.
        assertEquals(
            dockFor(LIBRARY).copy(position = DockPosition.LEFT),
            migrated.dockFor(HomeLayoutDeviceClass.TABLET),
        )
    }

    @Test
    fun rescuedItemsWhoseIdsAllCollideNeverShareAnId() {
        // The shared dock already uses "x" and the first suffix the rescue would reach for.
        val sharedDock =
            DockModel(capacity = 5, items = listOf(app("kept", id = "x"), app("kept2", id = "x-from-dock-1")))
        val colliding =
            listOf(
                app("a", id = "x"),
                app("b", id = "x"),
                FolderItem(
                    id = LauncherItemId("x"),
                    label = "Folder",
                    items = listOf(app("c", id = "x"), app("d", id = "x")),
                ),
                app("e", id = "x-from-dock-2"),
            )
        val set =
            HomeLayoutSet(
                activeKey = key(STANDARD),
                layouts =
                    mapOf(
                        key(STANDARD) to layout(STANDARD).copy(dock = sharedDock),
                        key(CARDS) to layout(CARDS).copy(dock = DockModel(capacity = 5, items = colliding)),
                    ),
            )

        val standard = set.withLegacyDocksUnified().layoutFor(key(STANDARD))

        val ids =
            (standard.pages.flatMap { page -> page.items } + standard.dock.items)
                .flatMap { item -> listOf(item.id) + (item as? FolderItem)?.items?.map { it.id }.orEmpty() }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids: $ids")
        assertTrue(listOf("a", "b", "c", "d", "e").all { name -> app(name).appIdentity in standard.pageApps() })
    }

    @Test
    fun claimingIdsNeverHandsOutTheSameOneTwice() {
        val used = mutableSetOf("x", "x-from-dock-1", "x-from-dock-3")

        val claimed = List(4) { LauncherItemId("x").claimUniqueIn(used) }.map { it.value }

        assertEquals(listOf("x-from-dock-2", "x-from-dock-4", "x-from-dock-5", "x-from-dock-6"), claimed)
    }

    // --- Fixtures ----------------------------------------------------------------------------

    private fun divergentSet(
        deviceClass: HomeLayoutDeviceClass,
        active: LauncherViewMode,
        edge: DockPosition,
    ): HomeLayoutSet =
        HomeLayoutSet(
            activeKey = key(active, deviceClass),
            layouts =
                LauncherViewMode.entries.associate { mode ->
                    val dock = if (mode == active) dockFor(mode).copy(position = edge) else dockFor(mode)
                    key(mode, deviceClass) to layout(mode, deviceClass).copy(dock = dock)
                },
        )

    private fun dockFor(mode: LauncherViewMode): DockModel =
        when (mode) {
            LauncherViewMode.STANDARD_APP_DRAWER ->
                DockModel(capacity = 4, items = listOf(app("shared"), app("standard-only")))

            LauncherViewMode.HOME_SCREEN_LIBRARY ->
                DockModel(capacity = 5, iconSizeDp = 40, items = listOf(app("shared"), app("library-only")))

            LauncherViewMode.CARD_INTERFACE ->
                DockModel(capacity = 6, showNotificationCards = true, items = listOf(app("shared"), app("cards-only")))
        }

    private fun panelOf(vararg items: LauncherItem): LauncherPage =
        LauncherPage(
            id = LauncherPageId("dock-panel"),
            grid = GridDimensions(columns = 4, rows = 2),
            items = items.toList(),
        )

    private fun widget(
        id: String,
        hostId: Int,
        span: GridSpan,
        minSpan: GridSpan = GridSpan(),
    ): WidgetItem =
        WidgetItem(
            id = LauncherItemId(id),
            appWidgetId = HostedWidgetId(hostId),
            label = id,
            resizeConstraints = WidgetResizeConstraints(minSpan = minSpan),
            placement = GridPlacement(cell = GridCell(0, 0), span = span),
        )

    private fun layout(
        mode: LauncherViewMode,
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
    ): HomeLayout = HomeLayoutDefaults.standard(deviceClass).copy(viewMode = mode)

    private fun HomeLayout.fullOf(
        prefix: String,
        grid: GridDimensions,
    ): HomeLayout =
        copy(
            pages =
                listOf(
                    selectedPage.copy(
                        items =
                            (0 until grid.rows).flatMap { row ->
                                (0 until grid.columns).map { column ->
                                    app("$prefix-$column-$row", placement = GridPlacement(GridCell(column, row)))
                                }
                            },
                    ),
                ),
        )

    private fun HomeLayout.everyItemOnTheWorkspace(): Boolean =
        pages.flatMap { page -> page.items }.all { item -> workspaceGrid.holds(item.placement) }

    private fun List<LauncherItem>.appIdentities(): List<AppIdentity> =
        flatMap { item ->
            when (item) {
                is AppShortcutItem -> listOf(item.appIdentity)
                is FolderItem -> item.items.map { app -> app.appIdentity }
                is WidgetItem -> emptyList()
            }
        }

    private fun HomeLayout.pageApps(): List<AppIdentity> = pages.flatMap { page -> page.items }.appIdentities()

    private fun key(
        mode: LauncherViewMode,
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
    ): HomeLayoutKey = HomeLayoutKey(viewMode = mode, deviceClass = deviceClass)

    private fun app(
        name: String,
        id: String = name,
        placement: GridPlacement? = null,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
            placement = placement,
        )

    private companion object {
        val STANDARD = LauncherViewMode.STANDARD_APP_DRAWER
        val LIBRARY = LauncherViewMode.HOME_SCREEN_LIBRARY
        val CARDS = LauncherViewMode.CARD_INTERFACE
    }
}
