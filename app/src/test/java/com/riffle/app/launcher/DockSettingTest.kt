package com.riffle.app.launcher

import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DockSettingTest {
    @Test
    fun dockNotificationCardsSettingSubtitleUsesGenericDisabledCopy() {
        assertEquals(
            "Expanded dock only shows shortcuts and widgets",
            dockNotificationCardsSettingSubtitle(
                enabled = false,
                notificationAccessStatus = NotificationAccessStatus.REVOKED,
            ),
        )
    }

    @Test
    fun dockNotificationCardsSettingSubtitleUsesGrantedCopy() {
        assertEquals(
            "Expanded dock can show notification cards",
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
