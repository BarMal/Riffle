package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
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

    @Test
    fun emptyStateLabelsNotificationAccessStatus() {
        assertEquals(
            "No active notifications",
            NotificationAccessStatus.GRANTED.emptyNotificationOverviewLabel,
        )
        assertEquals(
            "Notification access is not allowed",
            NotificationAccessStatus.NOT_GRANTED.emptyNotificationOverviewLabel,
        )
        assertEquals(
            "Notification access was revoked",
            NotificationAccessStatus.REVOKED.emptyNotificationOverviewLabel,
        )
        assertEquals(
            "Notification access has not been checked",
            NotificationAccessStatus.UNKNOWN.emptyNotificationOverviewLabel,
        )
    }

    @Test
    fun emptyStateShowsAccessActionWhenStatusIsNotGranted() {
        assertEquals(null, NotificationAccessStatus.GRANTED.emptyNotificationOverviewActionLabel)
        assertEquals(
            "Open notification access",
            NotificationAccessStatus.NOT_GRANTED.emptyNotificationOverviewActionLabel,
        )
        assertEquals(
            "Open notification access",
            NotificationAccessStatus.REVOKED.emptyNotificationOverviewActionLabel,
        )
        assertEquals(
            "Open notification access",
            NotificationAccessStatus.UNKNOWN.emptyNotificationOverviewActionLabel,
        )
    }

    @Test
    fun categoryFilterOptionsUseLatestGroupCategories() {
        val options =
            notificationCategoryFilterOptions(
                listOf(
                    notificationGroup(
                        packageName = "com.example.mail",
                        count = 2,
                        latestCategory = NotificationCategory.EMAIL,
                    ),
                    notificationGroup(
                        packageName = "com.example.chat",
                        count = 3,
                        latestCategory = NotificationCategory.MESSAGE,
                    ),
                ),
            )

        assertEquals(
            listOf(
                NotificationCategoryOption(category = null, label = "All 5"),
                NotificationCategoryOption(category = NotificationCategory.MESSAGE, label = "Message 3"),
                NotificationCategoryOption(category = NotificationCategory.EMAIL, label = "Email 2"),
            ),
            options,
        )
    }

    @Test
    fun categoryFilterKeepsMatchingGroupsOnly() {
        val mail =
            notificationGroup(
                packageName = "com.example.mail",
                count = 1,
                latestCategory = NotificationCategory.EMAIL,
            )
        val chat =
            notificationGroup(
                packageName = "com.example.chat",
                count = 1,
                latestCategory = NotificationCategory.MESSAGE,
            )

        assertEquals(
            listOf(mail, chat),
            notificationGroupsMatchingCategory(listOf(mail, chat), category = null),
        )
        assertEquals(
            listOf(mail),
            notificationGroupsMatchingCategory(listOf(mail, chat), category = NotificationCategory.EMAIL),
        )
    }

    private fun notificationGroup(
        packageName: String,
        count: Int,
        latestCategory: NotificationCategory = NotificationCategory.MESSAGE,
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = AppProfile.personal().id,
            latestCategory = latestCategory,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                (1..count).map { index ->
                    LauncherNotification(
                        key = LauncherNotificationKey("$packageName:$index"),
                        packageName = AppPackageName(packageName),
                        category = latestCategory,
                        postedAtEpochMillis = index.toLong(),
                    )
                },
        )
}
