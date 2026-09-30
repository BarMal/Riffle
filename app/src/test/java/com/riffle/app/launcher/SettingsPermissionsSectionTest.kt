package com.riffle.app.launcher

import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun calendarRowExplainsWhyBeforeAskingAndRoutesPermanentDenialToAppSettings() {
        assertEquals("Allow calendar access", CalendarAccessStatus.NOT_GRANTED.calendarAccessActionLabel())
        assertEquals("Open app settings", CalendarAccessStatus.DENIED_PERMANENTLY.calendarAccessActionLabel())
        assertNull(CalendarAccessStatus.GRANTED.calendarAccessActionLabel())
        assertTrue(CalendarAccessStatus.NOT_GRANTED.calendarAccessSettingsLabel().contains("only when you tap"))
        assertTrue(CalendarAccessStatus.DENIED_PERMANENTLY.calendarAccessSettingsLabel().contains("Android settings"))
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
