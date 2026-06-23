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
    fun rejectsWhenDockHasNoAvailableSlots() {
        val fullDock =
            DockModel(
                capacity = 1,
                items = listOf(appShortcut(id = "phone")),
            )
        val layout = HomeLayoutDefaults.standard().copy(dock = fullDock)

        val result = engine.addAppToDock(layout = layout, app = app(label = "Camera"))

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.NO_AVAILABLE_SLOT, rejected.reason)
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
    fun updatesDockVisibilityWithoutChangingItems() {
        val phone = appShortcut(id = "phone")
        val layout = layoutWithDockItems(phone)

        val result = engine.setDockEnabled(layout = layout, enabled = false)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(false, updated.layout.dock.isEnabled)
        assertEquals(listOf(phone.id), updated.layout.dock.items.map { item -> item.id })
    }

    @Test
    fun updatesDockCapacity() {
        val result = engine.setDockCapacity(layout = HomeLayoutDefaults.standard(), capacity = 7)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(7, updated.layout.dock.capacity)
    }

    @Test
    fun rejectsNegativeDockCapacity() {
        val result = engine.setDockCapacity(layout = HomeLayoutDefaults.standard(), capacity = -1)

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.INVALID_CAPACITY, rejected.reason)
    }

    @Test
    fun rejectsDockCapacityBelowCurrentItemCount() {
        val layout = layoutWithDockItems(appShortcut(id = "phone"), appShortcut(id = "camera"))

        val result = engine.setDockCapacity(layout = layout, capacity = 1)

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.CAPACITY_BELOW_ITEM_COUNT, rejected.reason)
    }

    private fun layoutWithDockItems(vararg items: AppShortcutItem): HomeLayout =
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
}
