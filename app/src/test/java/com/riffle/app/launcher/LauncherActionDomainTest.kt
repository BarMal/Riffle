package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherActionDomainTest {
    @Test
    fun routesActivityOwnedActions() {
        val actions =
            listOf(
                LauncherShellAction.OpenSettings,
                LauncherShellAction.RequestDefaultHome,
                LauncherShellAction.SelectHomePage(LauncherPageId("home")),
                LauncherShellAction.SelectHomeGridDimensions(GridDimensions(columns = 4, rows = 6)),
            )

        actions.forEach { action ->
            assertEquals(LauncherActionDomain.ACTIVITY, action.launcherActionDomain())
        }
    }

    @Test
    fun routesAppOwnedActions() {
        val identity =
            AppIdentity(
                packageName = AppPackageName("com.example.camera"),
                activityName = AppActivityName(".CameraActivity"),
            )
        val app = InstalledApp(identity = identity, label = "Camera")
        val actions =
            listOf(
                LauncherShellAction.LaunchApp(identity),
                LauncherShellAction.RefreshInstalledApps,
                LauncherShellAction.SearchQueryChanged("cam"),
                LauncherShellAction.AddAppToHome(app),
            )

        actions.forEach { action ->
            assertEquals(LauncherActionDomain.APP, action.launcherActionDomain())
        }
    }

    @Test
    fun routesSettingsOwnedActions() {
        val actions =
            listOf(
                LauncherShellAction.RequestImportLauncherBackup,
                LauncherShellAction.RequestNotificationAccess,
                LauncherShellAction.SelectFullscreenHomeEnabled(enabled = true),
                LauncherShellAction.SelectReducedMotionEnabled(enabled = true),
                LauncherShellAction.ResetHomeSwipeGestureActions,
            )

        actions.forEach { action ->
            assertEquals(LauncherActionDomain.SETTINGS, action.launcherActionDomain())
        }
    }

    @Test
    fun routesNotificationOwnedActions() {
        val action = LauncherShellAction.DismissNotifications(listOf(LauncherNotificationKey("camera")))

        assertEquals(LauncherActionDomain.NOTIFICATION, action.launcherActionDomain())
    }
}
