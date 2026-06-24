package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDockContextMenuTest {
    @Test
    fun editDockShortcutMenuAddsMoveActionsAndOmitsEditHome() {
        val shortcut = shortcut()

        val items =
            dockShortcutContextMenuItems(
                shortcut = shortcut,
                isEditing = true,
                shortcutIndex = 1,
                shortcutCount = 3,
            )

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.LEFT,
                        ),
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.RIGHT,
                        ),
                ),
                ShortcutContextMenuItem("App info", LauncherShellAction.OpenAppInfo(shortcut.appIdentity)),
                ShortcutContextMenuItem("Hide app", LauncherShellAction.HideApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Uninstall", LauncherShellAction.UninstallApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Remove from dock", LauncherShellAction.RemoveDockShortcut(shortcut.id)),
            ),
            items,
        )
    }

    @Test
    fun editDockShortcutMenuDisablesUnavailableMoveActionsAtBoundaries() {
        val shortcut = shortcut()

        val firstItems =
            dockShortcutContextMenuItems(
                shortcut = shortcut,
                isEditing = true,
                shortcutIndex = 0,
                shortcutCount = 2,
            )
        val lastItems =
            dockShortcutContextMenuItems(
                shortcut = shortcut,
                isEditing = true,
                shortcutIndex = 1,
                shortcutCount = 2,
            )

        assertEquals(false, firstItems.first { it.label == "Move left" }.enabled)
        assertEquals(true, firstItems.first { it.label == "Move right" }.enabled)
        assertEquals(true, lastItems.first { it.label == "Move left" }.enabled)
        assertEquals(false, lastItems.first { it.label == "Move right" }.enabled)
    }

    private fun shortcut(): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("camera"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.example.camera"),
                    activityName = AppActivityName(".CameraActivity"),
                ),
            label = "Camera",
        )
}
