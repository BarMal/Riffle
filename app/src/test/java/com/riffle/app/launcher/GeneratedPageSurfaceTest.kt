package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun notificationCardKeysKeepAmbiguousConcatenatedValuesDistinct() {
        val first = notificationGroup(packageName = "com.example.app1", profileId = "23")
        val second = notificationGroup(packageName = "com.example.app12", profileId = "3")

        assertEquals(
            first.packageName.value + first.profileId.value,
            second.packageName.value + second.profileId.value,
        )
        assertNotEquals(generatedNotificationCardKey(first), generatedNotificationCardKey(second))
    }

    private fun notificationGroup(
        packageName: String,
        profileId: String,
    ) = AppNotificationGroup(
        packageName = AppPackageName(packageName),
        profileId = AppProfileId(profileId),
        latestCategory = NotificationCategory.UNKNOWN,
        latestAgeBucket = NotificationAgeBucket.NOW,
        notifications = emptyList(),
    )
}
