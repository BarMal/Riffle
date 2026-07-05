package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationOverviewSurfaceTest {
    @Test
    fun titleIncludesActiveNotificationCount() {
        assertEquals(
            "Notifications (3)",
            notificationOverviewTitle(
                baseTitle = "Notifications",
                groups =
                    listOf(
                        notificationGroup(packageName = "com.example.mail", count = 2),
                        notificationGroup(packageName = "com.example.chat", count = 1),
                    ),
            ),
        )
    }

    @Test
    fun titleOmitsCountWhenThereAreNoActiveNotifications() {
        assertEquals(
            "Notifications",
            notificationOverviewTitle(baseTitle = "Notifications", groups = emptyList()),
        )
    }

    private fun notificationGroup(
        packageName: String,
        count: Int,
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = AppProfile.personal().id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                (1..count).map { index ->
                    LauncherNotification(
                        key = LauncherNotificationKey("$packageName:$index"),
                        packageName = AppPackageName(packageName),
                        postedAtEpochMillis = index.toLong(),
                    )
                },
        )
}
