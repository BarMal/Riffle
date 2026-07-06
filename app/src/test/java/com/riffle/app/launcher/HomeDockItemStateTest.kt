package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDockItemStateTest {
    @Test
    fun emptyDockSlotHasNoItemState() {
        assertNull(dockSlotItemState(null))
    }

    @Test
    fun appShortcutDockSlotKeepsShortcutState() {
        val shortcut = shortcut()

        val state = dockSlotItemState(shortcut) as DockSlotItemState.Shortcut

        assertEquals(shortcut, state.shortcut)
        assertEquals(shortcut.id, state.id)
        assertEquals("Camera", state.label)
    }

    @Test
    fun folderDockSlotRendersPlaceholderInsteadOfDisappearing() {
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Tools",
                items = listOf(shortcut()),
            )

        val state = dockSlotItemState(folder) as DockSlotItemState.Placeholder

        assertEquals(folder.id, state.id)
        assertEquals("Tools", state.label)
        assertEquals(DockSlotPlaceholderKind.FOLDER, state.kind)
    }

    @Test
    fun widgetDockSlotRendersPlaceholderInsteadOfDisappearing() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:weather"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
            )

        val state = dockSlotItemState(widget) as DockSlotItemState.Placeholder

        assertEquals(widget.id, state.id)
        assertEquals("Weather", state.label)
        assertEquals(DockSlotPlaceholderKind.WIDGET, state.kind)
    }

    private fun shortcut(): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:camera:1"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.example.camera"),
                    activityName = AppActivityName(".CameraActivity"),
                ),
            label = "Camera",
        )
}
