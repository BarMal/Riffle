package com.riffle.app.launcher

import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DockSettingTest {
    @Test
    fun dockNotificationCardsSettingSubtitleUsesGenericDisabledCopy() {
        assertEquals(
            "Dock only shows what you pinned to it",
            dockNotificationCardsSettingSubtitle(
                enabled = false,
                notificationAccessStatus = NotificationAccessStatus.REVOKED,
            ),
        )
    }

    @Test
    fun dockNotificationCardsSettingSubtitleUsesGrantedCopy() {
        assertEquals(
            "Dock shows apps with notifications beside your pinned ones",
            dockNotificationCardsSettingSubtitle(
                enabled = true,
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
            ),
        )
    }

    @Test
    fun dockNotificationCardsSettingSubtitleExplainsNotGrantedAccess() {
        assertEquals(
            "Notification cards are on, but access is not allowed",
            dockNotificationCardsSettingSubtitle(
                enabled = true,
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
            ),
        )
    }

    @Test
    fun dockNotificationCardsSettingSubtitleExplainsRevokedAccess() {
        assertEquals(
            "Notification cards are on, but access was revoked",
            dockNotificationCardsSettingSubtitle(
                enabled = true,
                notificationAccessStatus = NotificationAccessStatus.REVOKED,
            ),
        )
    }

    @Test
    fun dockNotificationCardsSettingSubtitleExplainsUnknownAccess() {
        assertEquals(
            "Notification cards are on, but access has not been checked",
            dockNotificationCardsSettingSubtitle(
                enabled = true,
                notificationAccessStatus = NotificationAccessStatus.UNKNOWN,
            ),
        )
    }
}
