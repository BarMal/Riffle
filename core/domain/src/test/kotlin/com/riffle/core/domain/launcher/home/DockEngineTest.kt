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
