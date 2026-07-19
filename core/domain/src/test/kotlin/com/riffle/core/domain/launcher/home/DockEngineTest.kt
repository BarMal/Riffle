package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DockEngineTest {
    private val engine = DockEngine()

    @Test
    fun addsAppShortcutToDock() {
        val app = app(label = "Phone")

        val result = engine.addAppToDock(layout = HomeLayoutDefaults.standard(), app = app)

        val updated = assertIs<DockEditResult.Updated>(result)
        val shortcut = updated.layout.dock.items.single() as AppShortcutItem
        assertEquals(app.identity, shortcut.appIdentity)
        assertEquals("Phone", shortcut.label)
        assertEquals(null, shortcut.placement)
        assertEquals(4, updated.layout.dock.availableSlots)
    }

    @Test
    fun rejectsDuplicateDockApp() {
        val app = app(label = "Phone")
        val layout = assertIs<DockEditResult.Updated>(engine.addAppToDock(HomeLayoutDefaults.standard(), app)).layout

        val result = engine.addAppToDock(layout = layout, app = app)

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.DUPLICATE_APP, rejected.reason)
    }

    @Test
    fun preservesDockCapacityWhenAddingToFullDock() {
        val fullDock =
            DockModel(
                capacity = 1,
                items = listOf(appShortcut(id = "phone")),
            )
        val layout = HomeLayoutDefaults.standard().copy(dock = fullDock)

        val result = engine.addAppToDock(layout = layout, app = app(label = "Camera"))

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(1, updated.layout.dock.capacity)
        assertEquals(listOf("phone", "Camera"), updated.layout.dock.items.filterIsInstance<AppShortcutItem>().labels)
    }

    @Test
    fun enablesDockWhenAddingAppShortcut() {
        val app = app(label = "Phone")
        val layout = HomeLayoutDefaults.standard().copy(dock = DockModel(capacity = 0, isEnabled = false))

        val result = engine.addAppToDock(layout = layout, app = app)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(true, updated.layout.dock.isEnabled)
        assertEquals(1, updated.layout.dock.capacity)
        assertEquals("Phone", updated.layout.dock.items.filterIsInstance<AppShortcutItem>().single().label)
    }

    @Test
    fun rejectsAddingAppShortcutToEnabledZeroCapacityDock() {
        val layout = HomeLayoutDefaults.standard().copy(dock = DockModel(capacity = 0, isEnabled = true))

        val result = engine.addAppToDock(layout = layout, app = app(label = "Phone"))

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.NO_AVAILABLE_SLOT, rejected.reason)
    }

    @Test
    fun addsWidgetToDock() {
        val layout = HomeLayoutDefaults.standard().copy(dock = DockModel(capacity = 0, isEnabled = false))

        val result =
            engine.addWidgetToDock(
                layout = layout,
                hostedWidgetId = HostedWidgetId(7),
                label = "Weather",
            )

        val updated = assertIs<DockEditResult.Updated>(result)
        val widget = updated.layout.dock.items.single() as WidgetItem
        assertEquals(LauncherItemId("dock-widget:7"), widget.id)
        assertEquals(HostedWidgetId(7), widget.appWidgetId)
        assertEquals("Weather", widget.label)
        assertEquals(true, updated.layout.dock.isEnabled)
        assertEquals(1, updated.layout.dock.capacity)
    }

    @Test
    fun rejectsDuplicateDockWidget() {
        val layout =
            assertIs<DockEditResult.Updated>(
                engine.addWidgetToDock(
                    layout = HomeLayoutDefaults.standard(),
                    hostedWidgetId = HostedWidgetId(7),
                    label = "Weather",
                ),
            ).layout

        val result =
            engine.addWidgetToDock(
                layout = layout,
                hostedWidgetId = HostedWidgetId(7),
                label = "Weather",
            )

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.DUPLICATE_WIDGET, rejected.reason)
    }

    @Test
    fun rejectsDockWidgetWhenHostedWidgetIdAlreadyExistsOnHomePage() {
        val existingWidget =
            WidgetItem(
                id = LauncherItemId("widget:7"),
                appWidgetId = HostedWidgetId(7),
                label = "Existing",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(existingWidget))),
            )

        val result =
            engine.addWidgetToDock(
                layout = layout,
                hostedWidgetId = HostedWidgetId(7),
                label = "Weather",
            )

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.DUPLICATE_WIDGET, rejected.reason)
    }

    @Test
    fun removesDockItem() {
        val shortcut = appShortcut(id = "phone")
        val layout = HomeLayoutDefaults.standard().copy(dock = DockModel(capacity = 5, items = listOf(shortcut)))

        val result = engine.removeDockItem(layout = layout, itemId = shortcut.id)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(emptyList(), updated.layout.dock.items)
    }

    @Test
    fun rejectsRemovingMissingDockItem() {
        val result =
            engine.removeDockItem(
                layout = HomeLayoutDefaults.standard(),
                itemId = LauncherItemId("missing"),
            )

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.ITEM_NOT_FOUND, rejected.reason)
    }

    @Test
    fun movesDockItemLeft() {
        val phone = appShortcut(id = "phone")
        val camera = appShortcut(id = "camera")
        val layout = layoutWithDockItems(phone, camera)

        val result =
            engine.moveDockItem(
                layout = layout,
                itemId = camera.id,
                direction = DockItemMoveDirection.LEFT,
            )

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(listOf(camera.id, phone.id), updated.layout.dock.items.map { item -> item.id })
        assertEquals(listOf(phone.id, camera.id), layout.dock.items.map { item -> item.id })
    }

    @Test
    fun movesDockItemRight() {
        val phone = appShortcut(id = "phone")
        val camera = appShortcut(id = "camera")
        val layout = layoutWithDockItems(phone, camera)

        val result =
            engine.moveDockItem(
                layout = layout,
                itemId = phone.id,
                direction = DockItemMoveDirection.RIGHT,
            )

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(listOf(camera.id, phone.id), updated.layout.dock.items.map { item -> item.id })
    }

    @Test
    fun movesDockItemToAnExactTargetIndexByStableId() {
        val phone = appShortcut(id = "phone")
        val camera = appShortcut(id = "camera")
        val maps = appShortcut(id = "maps")
        val layout = layoutWithDockItems(phone, camera, maps)

        val result = engine.moveDockItemToIndex(layout = layout, itemId = phone.id, targetIndex = 2)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(listOf(camera.id, maps.id, phone.id), updated.layout.dock.items.map { item -> item.id })
        assertEquals(listOf(phone.id, camera.id, maps.id), layout.dock.items.map { item -> item.id })
    }

    @Test
    fun keepsDockOrderWhenAnExactMoveTargetsItsCurrentIndex() {
        val phone = appShortcut(id = "phone")
        val camera = appShortcut(id = "camera")
        val layout = layoutWithDockItems(phone, camera)

        val result = engine.moveDockItemToIndex(layout = layout, itemId = camera.id, targetIndex = 1)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(layout.dock.items, updated.layout.dock.items)
    }

    @Test
    fun movesNonAppDockItem() {
        val phone = appShortcut(id = "phone")
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:weather"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = DockModel(capacity = 5, items = listOf(phone, widget)),
            )

        val result =
            engine.moveDockItem(
                layout = layout,
                itemId = widget.id,
                direction = DockItemMoveDirection.LEFT,
            )

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(listOf(widget.id, phone.id), updated.layout.dock.items.map { item -> item.id })
    }

    @Test
    fun movesFolderToAnExactTargetIndexWithoutChangingItsContents() {
        val phone = appShortcut(id = "phone")
        val folder =
            FolderItem(
                id = LauncherItemId("folder:work"),
                label = "Work",
                items = listOf(appShortcut(id = "calendar")),
            )
        val camera = appShortcut(id = "camera")
        val layout = layoutWithDockItems(phone, folder, camera)

        val result = engine.moveDockItemToIndex(layout = layout, itemId = folder.id, targetIndex = 2)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(listOf(phone.id, camera.id, folder.id), updated.layout.dock.items.map { item -> item.id })
        assertEquals(folder.items, assertIs<FolderItem>(updated.layout.dock.items.last()).items)
    }

    @Test
    fun movesHomeFolderToDockAtTheRequestedIndexWithoutChangingItsContents() {
        val folder =
            FolderItem(
                id = LauncherItemId("folder:work"),
                label = "Work",
                items = listOf(appShortcut(id = "calendar")),
                placement = GridPlacement(GridCell(0, 0)),
            )
        val phone = appShortcut(id = "phone")
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(folder))),
                dock = DockModel(capacity = 2, items = listOf(phone)),
            )

        val updated =
            assertIs<DockEditResult.Updated>(
                engine.moveHomeItemToDock(layout, folder.id, targetIndex = 0),
            ).layout

        assertEquals(listOf(folder.id, phone.id), updated.dock.items.map { it.id })
        assertEquals(emptyList<LauncherItem>(), updated.selectedPage.items)
        assertEquals(folder.items, assertIs<FolderItem>(updated.dock.items.first()).items)
    }

    @Test
    fun movesDockShortcutToTheRequestedHomeCellWithoutChangingItsIdentity() {
        val phone = appShortcut(id = "phone")
        val layout = HomeLayoutDefaults.standard().copy(dock = DockModel(capacity = 2, items = listOf(phone)))

        val updated =
            assertIs<DockEditResult.Updated>(
                engine.moveDockItemToHome(layout, phone.id, GridCell(column = 2, row = 1)),
            ).layout

        assertEquals(emptyList<LauncherItem>(), updated.dock.items)
        val moved = assertIs<AppShortcutItem>(updated.selectedPage.items.single())
        assertEquals(phone.id, moved.id)
        assertEquals(GridPlacement(GridCell(column = 2, row = 1)), moved.placement)
    }

    @Test
    fun rejectsHomeToDockTransferWhenDockIsFullWithoutMutatingEitherSurface() {
        val home = appShortcut(id = "home").copy(placement = GridPlacement(GridCell(0, 0)))
        val dock = appShortcut(id = "dock")
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(home))),
                dock = DockModel(capacity = 1, items = listOf(dock)),
            )

        val result = engine.moveHomeItemToDock(layout, home.id)

        assertEquals(DockEditRejectionReason.NO_AVAILABLE_SLOT, assertIs<DockEditResult.Rejected>(result).reason)
        assertEquals(listOf(home), layout.selectedPage.items)
        assertEquals(listOf(dock), layout.dock.items)
    }

    @Test
    fun rejectsDockItemMoveOutsideBounds() {
        val phone = appShortcut(id = "phone")
        val layout = layoutWithDockItems(phone)

        val result =
            engine.moveDockItem(
                layout = layout,
                itemId = phone.id,
                direction = DockItemMoveDirection.LEFT,
            )

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.INDEX_OUT_OF_BOUNDS, rejected.reason)
    }

    @Test
    fun rejectsMovingMissingDockItem() {
        val result =
            engine.moveDockItem(
                layout = HomeLayoutDefaults.standard(),
                itemId = LauncherItemId("missing"),
                direction = DockItemMoveDirection.RIGHT,
            )

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.ITEM_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsExactDockMoveWithAnInvalidTargetOrDuplicateIds() {
        val phone = appShortcut(id = "phone")
        val layout = layoutWithDockItems(phone)

        val invalidTarget = engine.moveDockItemToIndex(layout = layout, itemId = phone.id, targetIndex = 1)
        val duplicateIds =
            engine.moveDockItemToIndex(
                layout = layoutWithDockItems(phone, phone.copy(label = "Duplicate")),
                itemId = phone.id,
                targetIndex = 0,
            )

        assertEquals(
            DockEditRejectionReason.INDEX_OUT_OF_BOUNDS,
            assertIs<DockEditResult.Rejected>(invalidTarget).reason,
        )
        assertEquals(
            DockEditRejectionReason.DUPLICATE_ITEM_ID,
            assertIs<DockEditResult.Rejected>(duplicateIds).reason,
        )
    }

    @Test
    fun rejectsDirectionalDockMoveWithDuplicateIdsWithoutChangingLayout() {
        val phone = appShortcut(id = "phone")
        val layout = layoutWithDockItems(phone, phone.copy(label = "Duplicate"))

        val result = engine.moveDockItem(layout, phone.id, DockItemMoveDirection.RIGHT)

        assertEquals(
            DockEditRejectionReason.DUPLICATE_ITEM_ID,
            assertIs<DockEditResult.Rejected>(result).reason,
        )
        assertEquals(listOf("phone", "phone"), layout.dock.items.map { item -> item.id.value })
    }

    private fun layoutWithDockItems(vararg items: LauncherItem): HomeLayout =
        HomeLayoutDefaults.standard().copy(
            dock = DockModel(capacity = 5, items = items.toList()),
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

    private fun appShortcut(id: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
        )

    private val List<AppShortcutItem>.labels: List<String>
        get() = map { item -> item.label }
}
