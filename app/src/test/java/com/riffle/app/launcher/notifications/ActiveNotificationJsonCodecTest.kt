package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveNotificationJsonCodecTest {
    @Test
    fun roundTripsActiveNotifications() {
        val notifications =
            listOf(
                LauncherNotification(
                    key = LauncherNotificationKey("camera-1"),
                    packageName = AppPackageName("com.riffle.camera"),
                    category = NotificationCategory.MESSAGE,
                    priority = NotificationPriority.HIGH,
                    canDismiss = true,
                    postedAtEpochMillis = 1_000L,
                ),
            )

        assertEquals(notifications, decodeActiveNotifications(encodeActiveNotifications(notifications)))
    }

    @Test
    fun decodesEmptyNotificationList() {
        assertEquals(emptyList<LauncherNotification>(), decodeActiveNotifications("[]"))
    }

    @Test
    fun decodesMissingNotificationCategoryAsUnknown() {
        val notifications =
            decodeActiveNotifications(
                """
                [
                    {
                        "key": "legacy",
                        "packageName": "com.riffle.legacy",
                        "postedAtEpochMillis": 1000
                    }
                ]
                """.trimIndent(),
            )

        assertEquals(NotificationCategory.UNKNOWN, notifications.single().category)
    }

    @Test
    fun decodesMissingNotificationPriorityAsUnknown() {
        val notifications =
            decodeActiveNotifications(
                """
                [
                    {
                        "key": "legacy",
                        "packageName": "com.riffle.legacy",
                        "postedAtEpochMillis": 1000
                    }
                ]
                """.trimIndent(),
            )

        assertEquals(NotificationPriority.UNKNOWN, notifications.single().priority)
    }

    @Test
    fun decodesMissingDismissibleStateAsFalse() {
        val notifications =
            decodeActiveNotifications(
                """
                [
                    {
                        "key": "legacy",
                        "packageName": "com.riffle.legacy",
                        "postedAtEpochMillis": 1000
                    }
                ]
                """.trimIndent(),
            )

        assertEquals(false, notifications.single().canDismiss)
    }
}
