package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutContextMenuTest {
    @Test
    fun homeShortcutMenuContainsStandardLauncherActions() {
        val shortcut = shortcut()

        val items = shortcutContextMenuItems(shortcut, ShortcutContextSurface.HOME)

        assertEquals(
            listOf(
                ShortcutContextMenuItem("Edit home", LauncherShellAction.EnterHomeEditMode),
                ShortcutContextMenuItem("App info", LauncherShellAction.OpenAppInfo(shortcut.appIdentity)),
                ShortcutContextMenuItem("Hide app", LauncherShellAction.HideApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Uninstall", LauncherShellAction.UninstallApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Remove from home", LauncherShellAction.RemoveHomeShortcut(shortcut.id)),
            ),
            items,
        )
    }

    @Test
    fun dockShortcutMenuRemovesFromDock() {
        val shortcut = shortcut()

        val items = shortcutContextMenuItems(shortcut, ShortcutContextSurface.DOCK)

        assertEquals(
            ShortcutContextMenuItem("Remove from dock", LauncherShellAction.RemoveDockShortcut(shortcut.id)),
            items.last(),
        )
    }

    @Test
    fun editModeHomeShortcutMenuOmitsEditHomeAction() {
        val shortcut = shortcut()

        val items =
            shortcutContextMenuItems(
                shortcut = shortcut,
                surface = ShortcutContextSurface.HOME,
                includeEditHome = false,
            )

        assertEquals(
            listOf(
                ShortcutContextMenuItem("App info", LauncherShellAction.OpenAppInfo(shortcut.appIdentity)),
                ShortcutContextMenuItem("Hide app", LauncherShellAction.HideApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Uninstall", LauncherShellAction.UninstallApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Remove from home", LauncherShellAction.RemoveHomeShortcut(shortcut.id)),
            ),
            items,
        )
    }

    @Test
    fun shortcutMenuIncludesPlatformAppShortcutsFirst() {
        val shortcut = shortcut()
        val platformShortcut =
            AppShortcut(
                id = AppShortcutId("compose"),
                appIdentity = shortcut.appIdentity,
                shortLabel = "Compose",
                longLabel = "Compose message",
            )

        val items =
            shortcutContextMenuItems(
                shortcut = shortcut,
                surface = ShortcutContextSurface.HOME,
                appShortcuts = listOf(platformShortcut),
            )

        assertEquals(
            ShortcutContextMenuItem(
                label = "Compose message",
                action = LauncherShellAction.LaunchAppShortcut(platformShortcut),
            ),
            items.first(),
        )
    }

    @Test
    fun disabledPlatformShortcutMenuItemsAreDisabled() {
        val shortcut = shortcut()
        val platformShortcut =
            AppShortcut(
                id = AppShortcutId("compose"),
                appIdentity = shortcut.appIdentity,
                shortLabel = "Compose",
                enabled = false,
            )

        val items =
            shortcutContextMenuItems(
                shortcut = shortcut,
                surface = ShortcutContextSurface.HOME,
                appShortcuts = listOf(platformShortcut),
            )

        assertEquals(
            ShortcutContextMenuItem(
                label = "Compose",
                action = LauncherShellAction.LaunchAppShortcut(platformShortcut),
                enabled = false,
            ),
            items.first(),
        )
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
