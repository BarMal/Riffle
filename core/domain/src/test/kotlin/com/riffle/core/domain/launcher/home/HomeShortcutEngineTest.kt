package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeShortcutEngineTest {
    private val engine = HomeShortcutEngine()

    @Test
    fun rejectsAddingAppToGeneratedPage() {
        val result = engine.addAppToSelectedPage(generatedLayout(), app(label = "Camera"))

        assertEquals(
            PlacementRejectionReason.GENERATED_PAGE,
            assertIs<HomeShortcutResult.Rejected>(result).reason,
        )
    }

    @Test
    fun addsAppShortcutToSelectedPage() {
        val app = app(label = "Camera")

        val result = engine.addAppToSelectedPage(layout = HomeLayoutDefaults.standard(), app = app)

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        val shortcut = updated.layout.selectedPage.items.single() as AppShortcutItem
        assertEquals(app.identity, shortcut.appIdentity)
        assertEquals("Camera", shortcut.label)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), shortcut.placement)
    }

    private fun generatedLayout(): HomeLayout =
        HomeLayoutDefaults.standard().let { layout ->
            layout.copy(
                pages =
                    listOf(
                        layout.selectedPage.copy(type = LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY)),
                    ),
            )
        }

    @Test
    fun rejectsDuplicateAppShortcutOnHomePages() {
        val app = app(label = "Camera")
        val layout =
            layoutWith(
                AppShortcutItem(
                    id = LauncherItemId("camera"),
                    appIdentity = app.identity,
                    label = app.label,
                    placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                ),
            )

        val result = engine.addAppToSelectedPage(layout = layout, app = app)

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.DUPLICATE_APP, rejected.reason)
    }

    @Test
    fun rejectsDuplicateAppShortcutAlreadyInsideHomeFolder() {
        val app = app(label = "Camera")
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages =
                    listOf(
                        HomeLayoutDefaults.standard().selectedPage.copy(
                            items =
                                listOf(
                                    FolderItem(
                                        id = LauncherItemId("folder"),
                                        label = "Tools",
                                        items =
                                            listOf(
                                                AppShortcutItem(
                                                    id = LauncherItemId("folder-camera"),
                                                    appIdentity = app.identity,
                                                    label = app.label,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )

        val result = engine.addAppToSelectedPage(layout = layout, app = app)

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.DUPLICATE_APP, rejected.reason)
    }

    @Test
    fun addsPlatformAppShortcutToSelectedPage() {
        val shortcut = appShortcut(label = "Compose", shortcutId = "compose")

        val result = engine.addAppShortcutToSelectedPage(layout = HomeLayoutDefaults.standard(), shortcut = shortcut)

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        val item = updated.layout.selectedPage.items.single() as AppShortcutItem
        assertEquals(shortcut.appIdentity, item.appIdentity)
        assertEquals(shortcut.id, item.appShortcutId)
        assertEquals("Compose message", item.label)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), item.placement)
    }

    @Test
    fun allowsMainAppShortcutAndPlatformShortcutForSameApp() {
        val app = app(label = "Messages")
        val layoutWithMainApp =
            layoutWith(
                AppShortcutItem(
                    id = LauncherItemId("messages"),
                    appIdentity = app.identity,
                    label = app.label,
                    placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                ),
            )
        val shortcut =
            AppShortcut(
                id = AppShortcutId("compose"),
                appIdentity = app.identity,
                shortLabel = "Compose",
            )

        val result = engine.addAppShortcutToSelectedPage(layout = layoutWithMainApp, shortcut = shortcut)

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        assertEquals(2, updated.layout.selectedPage.items.size)
    }

    @Test
    fun rejectsDuplicatePlatformAppShortcut() {
        val shortcut = appShortcut(label = "Compose", shortcutId = "compose")
        val layout =
            layoutWith(
                AppShortcutItem(
                    id = LauncherItemId("shortcut:compose"),
                    appIdentity = shortcut.appIdentity,
                    label = shortcut.shortLabel,
                    appShortcutId = shortcut.id,
                    placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                ),
            )

        val result = engine.addAppShortcutToSelectedPage(layout = layout, shortcut = shortcut)

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.DUPLICATE_APP_SHORTCUT, rejected.reason)
    }

    @Test
    fun rejectsWhenSelectedPageHasNoAvailableCells() {
        val fullPage =
            LauncherPage(
                id = LauncherPageId("full"),
                grid = GridDimensions(columns = 1, rows = 1),
                items =
                    listOf(
                        appShortcut(
                            id = "calendar",
                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                        ),
                    ),
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(fullPage),
                selectedPageId = fullPage.id,
            )

        val result = engine.addAppToSelectedPage(layout = layout, app = app(label = "Camera"))

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.NO_AVAILABLE_CELL, rejected.reason)
    }

    @Test
    fun removesShortcutFromSelectedPage() {
        val shortcut =
            appShortcut(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(shortcut))),
            )

        val result = engine.removeShortcutFromSelectedPage(layout = layout, itemId = shortcut.id)

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        assertEquals(emptyList(), updated.layout.selectedPage.items)
    }

    @Test
    fun rejectsRemovingMissingShortcut() {
        val result =
            engine.removeShortcutFromSelectedPage(
                layout = HomeLayoutDefaults.standard(),
                itemId = LauncherItemId("missing"),
            )

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.ITEM_NOT_FOUND, rejected.reason)
    }

    @Test
    fun movesShortcutToCellOnSelectedPage() {
        val shortcut =
            appShortcut(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout = layoutWith(shortcut)

        val result =
            engine.moveShortcutToCellOnSelectedPage(
                layout = layout,
                itemId = shortcut.id,
                cell = GridCell(column = 2, row = 1),
            )

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        assertEquals(
            GridPlacement(cell = GridCell(column = 2, row = 1)),
            updated.layout.selectedPage.items.single().placement,
        )
    }

    @Test
    fun shiftsShortcutsWhenMovingToOccupiedCell() {
        val camera =
            appShortcut(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val calendar =
            appShortcut(
                id = "calendar",
                placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
            )
        val layout = layoutWith(camera, calendar)

        val result =
            engine.moveShortcutToCellOnSelectedPage(
                layout = layout,
                itemId = camera.id,
                cell = GridCell(column = 1, row = 0),
            )

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        assertEquals(
            GridPlacement(cell = GridCell(column = 1, row = 0)),
            updated.layout.selectedPage.items.single { item -> item.id == camera.id }.placement,
        )
        assertEquals(
            GridPlacement(cell = GridCell(column = 0, row = 0)),
            updated.layout.selectedPage.items.single { item -> item.id == calendar.id }.placement,
        )
    }

    @Test
    fun movesShortcutToEmptyCellWithoutShiftingSpannedWidget() {
        val camera =
            appShortcut(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val widget =
            widget(
                id = "clock",
                placement =
                    GridPlacement(
                        cell = GridCell(column = 1, row = 1),
                        span = GridSpan(columns = 2, rows = 2),
                    ),
            )
        val layout = layoutWith(camera, widget)

        val result =
            engine.moveShortcutToCellOnSelectedPage(
                layout = layout,
                itemId = camera.id,
                cell = GridCell(column = 3, row = 4),
            )

        val updated = assertIs<HomeShortcutResult.Updated>(result)
        assertEquals(
            GridPlacement(cell = GridCell(column = 3, row = 4)),
            updated.layout.selectedPage.items.single { item -> item.id == camera.id }.placement,
        )
        assertEquals(
            widget.placement,
            updated.layout.selectedPage.items.single { item -> item.id == widget.id }.placement,
        )
    }

    @Test
    fun rejectsMovingShortcutIntoSpannedWidget() {
        val camera =
            appShortcut(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val widget =
            widget(
                id = "clock",
                placement =
                    GridPlacement(
                        cell = GridCell(column = 1, row = 1),
                        span = GridSpan(columns = 2, rows = 2),
                    ),
            )
        val layout = layoutWith(camera, widget)

        val result =
            engine.moveShortcutToCellOnSelectedPage(
                layout = layout,
                itemId = camera.id,
                cell = GridCell(column = 2, row = 2),
            )

        val rejected = assertIs<HomeShortcutResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.COLLISION, rejected.reason)
    }

    private fun layoutWith(vararg shortcuts: LauncherItem): HomeLayout =
        HomeLayoutDefaults.standard().copy(
            pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = shortcuts.toList())),
        )

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private fun appShortcut(
        label: String,
        shortcutId: String,
    ): AppShortcut =
        AppShortcut(
            id = AppShortcutId(shortcutId),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            shortLabel = label,
            longLabel = "$label message",
        )

    private fun appShortcut(
        id: String,
        placement: GridPlacement,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
            placement = placement,
        )

    private fun widget(
        id: String,
        placement: GridPlacement,
    ): WidgetItem =
        WidgetItem(
            id = LauncherItemId(id),
            appWidgetId = HostedWidgetId(42),
            label = id,
            placement = placement,
        )
}
