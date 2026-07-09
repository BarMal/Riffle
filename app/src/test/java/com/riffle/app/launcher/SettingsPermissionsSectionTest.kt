package com.riffle.app.launcher

import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPermissionsSectionTest {
    @Test
    fun labelsNotificationAccessStatus() {
        assertEquals("Allowed", NotificationAccessStatus.GRANTED.settingsNotificationAccessLabel())
        assertEquals("Not allowed", NotificationAccessStatus.NOT_GRANTED.settingsNotificationAccessLabel())
        assertEquals("Revoked", NotificationAccessStatus.REVOKED.settingsNotificationAccessLabel())
        assertEquals("Unknown", NotificationAccessStatus.UNKNOWN.settingsNotificationAccessLabel())
    }

    @Test
    fun labelsOverlayDockPermissionStatus() {
        assertEquals("Allowed", OverlayDockPermissionStatus.GRANTED.settingsOverlayDockPermissionLabel())
        assertEquals("Not allowed", OverlayDockPermissionStatus.NOT_GRANTED.settingsOverlayDockPermissionLabel())
        assertEquals("Unknown", OverlayDockPermissionStatus.UNKNOWN.settingsOverlayDockPermissionLabel())
    }
}
