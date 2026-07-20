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

    @Test
    fun keepsFloatingDockRecoverableAfterOverlayAccessIsDeniedOrUnknown() {
        assertEquals(
            OverlayDockAccessPresentation(
                subtitle = "Overlay access is not allowed. Allow it to show the Floating dock.",
                retryLabel = "Allow overlay access",
            ),
            overlayDockAccessPresentation(
                enabled = true,
                permissionStatus = OverlayDockPermissionStatus.NOT_GRANTED,
            ),
        )
        assertEquals(
            OverlayDockAccessPresentation(
                subtitle = "Overlay access is still checking. Try again if it does not update.",
                retryLabel = "Retry overlay access",
            ),
            overlayDockAccessPresentation(
                enabled = true,
                permissionStatus = OverlayDockPermissionStatus.UNKNOWN,
            ),
        )
    }
}
