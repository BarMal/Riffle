package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.cards.CardStackSurfaceLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationOverviewSurfaceTest {
    @Test
    fun prototypeLayoutKeepsPhoneCardsCenterStageAndUsesWiderWindowContext() {
        assertEquals(
            CardStackSurfaceLayout.CENTER_STAGE,
            notificationPrototypeSurfaceLayout(HomeLayoutDeviceClass.PHONE),
        )
        assertEquals(
            CardStackSurfaceLayout.SIDE_BY_SIDE,
            notificationPrototypeSurfaceLayout(HomeLayoutDeviceClass.FOLDABLE),
        )
    }

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

    @Test
    fun overviewFallbackLabelUsesReadablePackageTail() {
        assertEquals(
            "Chat Service",
            notificationOverviewGroupLabel(
                app = null,
                group = notificationGroup(packageName = "com.example.chat_service", count = 1),
            ),
        )
    }

    @Test
    fun overviewMetadataUsesReadableFallbackLabel() {
        val group = notificationGroup(packageName = "com.example.chat", count = 1)

        assertEquals(
            "Chat - Message - Priority unknown - Pinned",
            group.notificationOverviewMetadataLabel(
                label = notificationOverviewGroupLabel(app = null, group = group),
            ),
        )
    }

    @Test
    fun focusedNotificationUsesFirstVisibleNotification() {
        val notifications =
            listOf(
                notification(key = "chat-1", title = "First"),
                notification(key = "chat-2", title = "Second"),
                notification(key = "chat-3", title = "Third"),
            )

        assertEquals(
            LauncherNotificationKey("chat-2"),
            notificationOverviewFocusedNotification(
                notifications = notifications,
                firstVisibleItemIndex = 1,
            )?.key,
        )
    }

    @Test
    fun scrollProgressClampsToVisibleItemBounds() {
        assertEquals(
            0f,
            notificationOverviewScrollProgress(
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemSize = 200,
            ),
        )
        assertEquals(
            0.5f,
            notificationOverviewScrollProgress(
                firstVisibleItemScrollOffset = 100,
                firstVisibleItemSize = 200,
            ),
        )
        assertEquals(
            1f,
            notificationOverviewScrollProgress(
                firstVisibleItemScrollOffset = 300,
                firstVisibleItemSize = 200,
            ),
        )
        assertEquals(
            0f,
            notificationOverviewScrollProgress(
                firstVisibleItemScrollOffset = 10,
                firstVisibleItemSize = 0,
            ),
        )
    }

    @Test
    fun selectedGroupIndexUsesMatchingGroupOrFallsBackToFirst() {
        val groups =
            listOf(
                notificationGroup(packageName = "com.example.chat", count = 1),
                notificationGroup(packageName = "com.example.mail", count = 1),
            )

        assertEquals(
            1,
            notificationOverviewSelectedGroupIndex(
                groups = groups,
                selectedGroupKey = groups[1].key,
            ),
        )
        assertEquals(
            0,
            notificationOverviewSelectedGroupIndex(
                groups = groups,
                selectedGroupKey =
                    AppNotificationGroupKey(
                        packageName = AppPackageName("com.example.unknown"),
                        profileId = AppProfile.personal().id,
                    ),
            ),
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
                    notification(
                        key = "$packageName:$index",
                        packageName = packageName,
                        category = latestCategory,
                        postedAtEpochMillis = index.toLong(),
                    )
                },
        )

    private fun notification(
        key: String,
        packageName: String = "com.example.chat",
        category: NotificationCategory = NotificationCategory.MESSAGE,
        postedAtEpochMillis: Long = 1L,
        title: String = "Message",
        text: String = "Body",
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            category = category,
            title = title,
            text = text,
            postedAtEpochMillis = postedAtEpochMillis,
        )
}
