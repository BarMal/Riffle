package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherSettingsActionRouteTest {
    @Test
    fun routesSettingsStateActions() {
        val action = LauncherShellAction.SelectHapticFeedbackStrength(HapticFeedbackStrength.STRONG)

        assertEquals(
            LauncherSettingsActionRoute.SettingsState(action),
            action.launcherSettingsActionRoute(),
        )
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
}
