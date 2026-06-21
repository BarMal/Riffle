package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class AppNotificationCounterTest {
    @Test
    fun countsActiveNotificationsByPackage() {
        val counter = AppNotificationCounter()

        val counts =
            counter.countByPackage(
                listOf(
                    notification(key = "camera-1", packageName = "com.android.camera"),
                    notification(key = "camera-2", packageName = "com.android.camera"),
                    notification(key = "maps-1", packageName = "com.google.maps"),
                ),
            )

        assertEquals(2, counts[AppPackageName("com.android.camera")])
        assertEquals(1, counts[AppPackageName("com.google.maps")])
    }

    @Test
    fun countsActiveNotificationsByCategory() {
        val counter = AppNotificationCounter()

        val counts =
            counter.countByCategory(
                listOf(
                    notification(
                        key = "message-1",
                        packageName = "com.riffle.chat",
                        category = NotificationCategory.MESSAGE,
                    ),
                    notification(
                        key = "message-2",
                        packageName = "com.riffle.chat",
                        category = NotificationCategory.MESSAGE,
                    ),
                    notification(
                        key = "email-1",
                        packageName = "com.riffle.mail",
                        category = NotificationCategory.EMAIL,
                    ),
                ),
            )

        assertEquals(2, counts[NotificationCategory.MESSAGE])
        assertEquals(1, counts[NotificationCategory.EMAIL])
    }

    @Test
    fun returnsEmptyCountsForEmptyNotifications() {
        val counter = AppNotificationCounter()

        assertEquals(emptyMap<AppPackageName, Int>(), counter.countByPackage(emptyList()))
        assertEquals(emptyMap<NotificationCategory, Int>(), counter.countByCategory(emptyList()))
    }

    private fun notification(
        key: String,
        packageName: String,
        category: NotificationCategory = NotificationCategory.UNKNOWN,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            category = category,
            postedAtEpochMillis = 1_000L,
        )
}
