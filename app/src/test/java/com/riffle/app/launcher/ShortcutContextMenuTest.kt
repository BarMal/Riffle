package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutContextMenuTest {
    @Test
    fun homeShortcutMenuAddsToDockWithoutRemovingTheHomeShortcut() {
        val shortcut = shortcut()

        val items = shortcutContextMenuItems(shortcut, ShortcutContextSurface.HOME)

        assertEquals(
            listOf(
                ShortcutContextMenuItem("App info", LauncherShellAction.OpenAppInfo(shortcut.appIdentity)),
                ShortcutContextMenuItem("Hide app", LauncherShellAction.HideApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Uninstall", LauncherShellAction.UninstallApp(shortcut.appIdentity)),
                ShortcutContextMenuItem(
                    "Add to dock",
                    LauncherShellAction.AddAppToDock(
                        InstalledApp(identity = shortcut.appIdentity, label = shortcut.label),
                    ),
                ),
                ShortcutContextMenuItem(
                    "Add to floating dock",
                    LauncherShellAction.AddAppToFloatingDock(
                        InstalledApp(identity = shortcut.appIdentity, label = shortcut.label),
                    ),
                ),
                ShortcutContextMenuItem("Remove from home", LauncherShellAction.RemoveHomeShortcut(shortcut.id)),
            ),
            items,
        )
    }

    @Test
    fun dockShortcutMenuRemovesFromDock() {
        val shortcut = shortcut()

        val items = shortcutContextMenuItems(shortcut, ShortcutContextSurface.DOCK)

        assertFalse(items.any { it.label == "Move to dock" })
        assertEquals(
            ShortcutContextMenuItem("Move to home", LauncherShellAction.MoveDockItemToHome(shortcut.id)),
            items[3],
        )
        assertEquals(
            ShortcutContextMenuItem("Remove from dock", LauncherShellAction.RemoveDockShortcut(shortcut.id)),
            items.last(),
        )
    }

    @Test
    fun panelShortcutMenuRemovesFromThePanel() {
        // The panel is not one of the layout's pages, so the home removal would match nothing on it.
        val shortcut = shortcut()

        val items = shortcutContextMenuItems(shortcut, ShortcutContextSurface.DOCK_PANEL)

        assertEquals(
            ShortcutContextMenuItem("Remove from panel", LauncherShellAction.RemoveDockPanelItem(shortcut.id)),
            items.last(),
        )
        assertFalse(items.any { it.label == "Move to home" })
        assertFalse(items.any { it.label == "Move to dock" })
    }

    @Test
    fun homeShortcutMenuDoesNotExposeEditMode() {
        val shortcut = shortcut()

        val items =
            shortcutContextMenuItems(
                shortcut = shortcut,
                surface = ShortcutContextSurface.HOME,
            )

        assertEquals(
            listOf(
                ShortcutContextMenuItem("App info", LauncherShellAction.OpenAppInfo(shortcut.appIdentity)),
                ShortcutContextMenuItem("Hide app", LauncherShellAction.HideApp(shortcut.appIdentity)),
                ShortcutContextMenuItem("Uninstall", LauncherShellAction.UninstallApp(shortcut.appIdentity)),
                ShortcutContextMenuItem(
                    "Add to dock",
                    LauncherShellAction.AddAppToDock(
                        InstalledApp(identity = shortcut.appIdentity, label = shortcut.label),
                    ),
                ),
                ShortcutContextMenuItem(
                    "Add to floating dock",
                    LauncherShellAction.AddAppToFloatingDock(
                        InstalledApp(identity = shortcut.appIdentity, label = shortcut.label),
                    ),
                ),
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

    @Test
    fun upToFourShortcutsStandInTheMenuWithNoMoreRow() {
        val shortcut = shortcut()
        val items =
            shortcutContextMenuItems(
                shortcut = shortcut,
                surface = ShortcutContextSurface.HOME,
                appShortcuts = platformShortcuts(shortcut, 4),
            )

        assertEquals(
            listOf("Shortcut 0", "Shortcut 1", "Shortcut 2", "Shortcut 3", "App info"),
            items.take(5).map { it.label },
        )
        assertTrue(items.none { it.submenuItems.isNotEmpty() })
    }

    @Test
    fun moreThanFourShortcutsPutTheRestBehindAMoreRowAndKeepEveryOne() {
        val shortcut = shortcut()
        val items =
            shortcutContextMenuItems(
                shortcut = shortcut,
                surface = ShortcutContextSurface.HOME,
                appShortcuts = platformShortcuts(shortcut, 6),
            )

        assertEquals(
            listOf("Shortcut 0", "Shortcut 1", "Shortcut 2", "Shortcut 3", "More"),
            items.take(5).map { it.label },
        )
        assertEquals(listOf("Shortcut 4", "Shortcut 5"), items[4].submenuItems.map { it.label })
        assertEquals("App info", items[5].label)
    }

    @Test
    fun anAppWithNoShortcutsStartsWithItsOwnActions() {
        val items = shortcutContextMenuItems(shortcut(), ShortcutContextSurface.HOME)

        assertEquals("App info", items.first().label)
    }

    private fun platformShortcuts(
        shortcut: AppShortcutItem,
        count: Int,
    ): List<AppShortcut> =
        (0 until count).map { index ->
            AppShortcut(
                id = AppShortcutId("shortcut-$index"),
                appIdentity = shortcut.appIdentity,
                shortLabel = "Shortcut $index",
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
