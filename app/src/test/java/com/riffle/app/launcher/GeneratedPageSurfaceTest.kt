package com.riffle.app.launcher

import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedPageSurfaceTest {
    @Test
    fun unavailableNotificationAccessExplainsHowToEnableCards() {
        assertEquals(
            GeneratedNotificationCardsPageState.Message(
                "Notification access needed",
                "Allow notification access to show your notification cards.",
            ),
            generatedNotificationCardsPageState(
                groups = emptyList(),
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                apps = emptyList(),
            ),
        )
    }

    @Test
    fun grantedAccessWithoutNotificationsShowsEmptyState() {
        assertEquals(
            GeneratedNotificationCardsPageState.Message("No notifications", "New notifications will appear here."),
            generatedNotificationCardsPageState(
                groups = emptyList(),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                apps = emptyList(),
            ),
        )
    }
}
