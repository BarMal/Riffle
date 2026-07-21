package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.settings.AppDrawerPresentation
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockItemMoveDirection
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherSettingsActionRouteTest {
    @Test
    fun routesSettingsStateActions() {
        val actions =
            listOf(
                LauncherShellAction.SelectReducedMotionEnabled(enabled = true),
                LauncherShellAction.SelectContextualEnabled(enabled = true),
                LauncherShellAction.SelectWallpaperScrollMode(WallpaperScrollMode.SCROLLING),
                LauncherShellAction.SelectHomeStatusBarHidden(hidden = true),
                LauncherShellAction.SelectHomeNavigationBarHidden(hidden = true),
                LauncherShellAction.SelectAppDrawerPresentation(AppDrawerPresentation.ICONS),
                LauncherShellAction.SelectAppDrawerIconGridColumns(columns = 5),
                LauncherShellAction.UpdateTimeScapeAppearance(TimeScapeAppearanceSettings.modern()),
            )

        actions.forEach { action ->
            assertEquals(
                LauncherSettingsActionRoute.SettingsState(action),
                action.launcherSettingsActionRoute(),
            )
        }
    }

    @Test
    fun routesOverlayDockSettingAsSettingsStateAction() {
        val actions =
            listOf(
                LauncherShellAction.SelectOverlayDockEnabled(enabled = true),
                LauncherShellAction.SelectOverlayDockEdge(OverlayDockEdge.START),
                LauncherShellAction.SelectOverlayDockHandleThickness(thicknessDp = 24),
                LauncherShellAction.SelectOverlayDockHandleHeight(heightDp = 96),
                LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp = -48),
                LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent = 65),
                LauncherShellAction.SelectOverlayDockExpandedIconSize(sizeDp = 64),
                LauncherShellAction.SelectOverlayDockExpandedOrientation(OverlayDockExpandedOrientation.TALL),
                LauncherShellAction.SelectOverlayDockShowLabels(showLabels = true),
                LauncherShellAction.AddAppToFloatingDock(installedApp),
                LauncherShellAction.AddAppShortcutToFloatingDock(appShortcut),
                LauncherShellAction.RemoveFloatingDockShortcut(LauncherItemId("floating-dock:example")),
                LauncherShellAction.MoveFloatingDockShortcut(
                    itemId = LauncherItemId("floating-dock:example"),
                    direction = OverlayDockItemMoveDirection.DOWN,
                ),
            )

        actions.forEach { action ->
            assertEquals(
                LauncherSettingsActionRoute.SettingsState(action),
                action.launcherSettingsActionRoute(),
            )
        }
    }

    @Test
    fun routesSettingsSideEffects() {
        assertEquals(
            LauncherSettingsActionRoute.RequestNotificationAccess,
            LauncherShellAction.RequestNotificationAccess.launcherSettingsActionRoute(),
        )
        assertEquals(
            LauncherSettingsActionRoute.RequestOverlayDockPermission,
            LauncherShellAction.RequestOverlayDockPermission.launcherSettingsActionRoute(),
        )
        assertEquals(
            LauncherSettingsActionRoute.ChangeWallpaper,
            LauncherShellAction.ChangeWallpaper.launcherSettingsActionRoute(),
        )
        assertEquals(
            LauncherSettingsActionRoute.ExportBackup,
            LauncherShellAction.ExportLauncherBackup.launcherSettingsActionRoute(),
        )
        assertEquals(
            LauncherSettingsActionRoute.RequestImportBackup,
            LauncherShellAction.RequestImportLauncherBackup.launcherSettingsActionRoute(),
        )
    }

    @Test
    fun ignoresNonSettingsActions() {
        assertNull(LauncherShellAction.OpenHome.launcherSettingsActionRoute())
        assertNull(LauncherShellAction.RefreshInstalledApps.launcherSettingsActionRoute())
        assertNull(LauncherShellAction.DismissNotifications(emptyList()).launcherSettingsActionRoute())
    }

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.app"),
                activityName = AppActivityName(".MainActivity"),
            )
        val installedApp = InstalledApp(identity = appIdentity, label = "Example")
        val appShortcut =
            AppShortcut(
                id = AppShortcutId("compose"),
                appIdentity = appIdentity,
                shortLabel = "Compose",
            )
    }
}
