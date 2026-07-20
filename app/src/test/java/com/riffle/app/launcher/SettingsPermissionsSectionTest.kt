package com.riffle.app.launcher

import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPermissionsSectionTest {
    @Test
    fun requestsNotificationAccessOnlyWhenEnablingNotificationCardsWithoutAccess() {
        assertEquals(
            listOf(
                LauncherShellAction.SelectDockNotificationCardsEnabled(true),
                LauncherShellAction.RequestNotificationAccess,
            ),
            dockNotificationCardsEnabledActions(
                enabled = true,
                wasEnabled = false,
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
            ),
        )
        assertEquals(
            listOf(LauncherShellAction.SelectDockNotificationCardsEnabled(true)),
            dockNotificationCardsEnabledActions(
                enabled = true,
                wasEnabled = false,
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
            ),
        )
    }

    @Test
    fun requestsOverlayAccessOnlyWhenEnablingFloatingDockWithoutAccess() {
        assertEquals(
            listOf(
                LauncherShellAction.SelectOverlayDockEnabled(true),
                LauncherShellAction.RequestOverlayDockPermission,
            ),
            overlayDockEnabledActions(
                enabled = true,
                wasEnabled = false,
                permissionStatus = OverlayDockPermissionStatus.NOT_GRANTED,
            ),
        )
        assertEquals(
            listOf(LauncherShellAction.SelectOverlayDockEnabled(true)),
            overlayDockEnabledActions(
                enabled = true,
                wasEnabled = false,
                permissionStatus = OverlayDockPermissionStatus.GRANTED,
            ),
        )
    }
}
