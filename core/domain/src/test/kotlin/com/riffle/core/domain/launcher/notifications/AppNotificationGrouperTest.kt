package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import kotlin.test.Test
import kotlin.test.assertEquals

class AppNotificationGrouperTest {
    private val grouper = AppNotificationGrouper()
    private val nowEpochMillis = 10 * 60 * 1_000L

    @Test
    fun groupsNotificationsByPackageAndProfile() {
        val groups =
            grouper.groupByApp(
                listOf(
                    notification(key = "personal-1", packageName = "com.riffle.mail"),
                    notification(key = "work-1", packageName = "com.riffle.mail", profileId = AppProfile.work().id),
                    notification(key = "personal-2", packageName = "com.riffle.mail"),
                ),
                nowEpochMillis = nowEpochMillis,
            )

        assertEquals(2, groups.size)
        assertEquals(2, groups.group("com.riffle.mail", AppProfile.personal().id).count)
        assertEquals(1, groups.group("com.riffle.mail", AppProfile.work().id).count)
    }

    @Test
    fun ordersGroupsByMostRecentNotification() {
        val groups =
            grouper.groupByApp(
                listOf(
                    notification(key = "older", packageName = "com.riffle.calendar", postedAtEpochMillis = 1_000L),
                    notification(key = "newer", packageName = "com.riffle.mail", postedAtEpochMillis = 2_000L),
                ),
                nowEpochMillis = nowEpochMillis,
            )

        assertEquals(AppPackageName("com.riffle.mail"), groups[0].packageName)
        assertEquals(AppPackageName("com.riffle.calendar"), groups[1].packageName)
    }

    @Test
    fun ordersGroupsByHighestPriorityBeforeRecency() {
        val groups =
            grouper.groupByApp(
                listOf(
                    notification(
                        key = "newer-routine",
                        packageName = "com.riffle.calendar",
                        priority = NotificationPriority.DEFAULT,
                        postedAtEpochMillis = 2_000L,
                    ),
                    notification(
                        key = "older-urgent",
                        packageName = "com.riffle.mail",
                        priority = NotificationPriority.HIGH,
                        postedAtEpochMillis = 1_000L,
                    ),
                ),
                nowEpochMillis = nowEpochMillis,
            )

        assertEquals(AppPackageName("com.riffle.mail"), groups[0].packageName)
        assertEquals(AppPackageName("com.riffle.calendar"), groups[1].packageName)
    }

    @Test
    fun ordersEqualPriorityGroupsByTimestampThenAppIdentity() {
        val groups =
            grouper.groupByApp(
                listOf(
                    notification(
                        key = "mail-newer",
                        packageName = "com.riffle.mail",
                        priority = NotificationPriority.DEFAULT,
                        postedAtEpochMillis = 2_000L,
                    ),
                    notification(
                        key = "calendar-same-time",
                        packageName = "com.riffle.calendar",
                        priority = NotificationPriority.DEFAULT,
                        postedAtEpochMillis = 1_000L,
                    ),
                    notification(
                        key = "mail-work-same-time",
                        packageName = "com.riffle.mail",
                        profileId = AppProfile.work().id,
                        priority = NotificationPriority.DEFAULT,
                        postedAtEpochMillis = 1_000L,
                    ),
                ),
                nowEpochMillis = nowEpochMillis,
            )

        assertEquals(
            listOf(
                AppNotificationGroupKey(AppPackageName("com.riffle.mail"), AppProfile.personal().id),
                AppNotificationGroupKey(AppPackageName("com.riffle.calendar"), AppProfile.personal().id),
                AppNotificationGroupKey(AppPackageName("com.riffle.mail"), AppProfile.work().id),
            ),
            groups.map { group -> AppNotificationGroupKey(group.packageName, group.profileId) },
        )
    }

    @Test
    fun ordersNotificationsWithinGroupByMostRecentFirst() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(key = "older", packageName = "com.riffle.mail", postedAtEpochMillis = 1_000L),
                        notification(key = "newer", packageName = "com.riffle.mail", postedAtEpochMillis = 2_000L),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(
            listOf(LauncherNotificationKey("newer"), LauncherNotificationKey("older")),
            group.notifications.map { notification -> notification.key },
        )
    }

    @Test
    fun ordersNotificationsWithinGroupByPriorityBeforeRecency() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(
                            key = "newer-routine",
                            packageName = "com.riffle.mail",
                            priority = NotificationPriority.DEFAULT,
                            postedAtEpochMillis = 2_000L,
                        ),
                        notification(
                            key = "older-urgent",
                            packageName = "com.riffle.mail",
                            priority = NotificationPriority.HIGH,
                            postedAtEpochMillis = 1_000L,
                        ),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(
            listOf(LauncherNotificationKey("older-urgent"), LauncherNotificationKey("newer-routine")),
            group.notifications.map { notification -> notification.key },
        )
    }

    @Test
    fun ordersEqualPriorityNotificationsByTimestampThenKey() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(
                            key = "same-time-b",
                            packageName = "com.riffle.mail",
                            priority = NotificationPriority.DEFAULT,
                            postedAtEpochMillis = 1_000L,
                        ),
                        notification(
                            key = "newer",
                            packageName = "com.riffle.mail",
                            priority = NotificationPriority.DEFAULT,
                            postedAtEpochMillis = 2_000L,
                        ),
                        notification(
                            key = "same-time-a",
                            packageName = "com.riffle.mail",
                            priority = NotificationPriority.DEFAULT,
                            postedAtEpochMillis = 1_000L,
                        ),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(
            listOf(
                LauncherNotificationKey("newer"),
                LauncherNotificationKey("same-time-a"),
                LauncherNotificationKey("same-time-b"),
            ),
            group.notifications.map { notification -> notification.key },
        )
    }

    @Test
    fun assignsGroupAgeFromMostRecentNotification() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(key = "older", packageName = "com.riffle.mail", postedAtEpochMillis = 1_000L),
                        notification(
                            key = "newer",
                            packageName = "com.riffle.mail",
                            postedAtEpochMillis = 9 * 60 * 1_000L,
                        ),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(NotificationAgeBucket.NOW, group.latestAgeBucket)
    }

    @Test
    fun assignsGroupAgeFromMostRecentNotificationWhenHigherPriorityNotificationIsOlder() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(
                            key = "older-urgent",
                            packageName = "com.riffle.mail",
                            priority = NotificationPriority.HIGH,
                            postedAtEpochMillis = 1_000L,
                        ),
                        notification(
                            key = "newer-routine",
                            packageName = "com.riffle.mail",
                            priority = NotificationPriority.DEFAULT,
                            postedAtEpochMillis = 9 * 60 * 1_000L,
                        ),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(NotificationAgeBucket.NOW, group.latestAgeBucket)
    }

    @Test
    fun assignsGroupCategoryFromMostRecentNotification() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(
                            key = "older",
                            packageName = "com.riffle.mail",
                            category = NotificationCategory.EMAIL,
                            postedAtEpochMillis = 1_000L,
                        ),
                        notification(
                            key = "newer",
                            packageName = "com.riffle.mail",
                            category = NotificationCategory.MESSAGE,
                            postedAtEpochMillis = 2_000L,
                        ),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(NotificationCategory.MESSAGE, group.latestCategory)
    }

    @Test
    fun assignsGroupCategoryFromMostRecentNotificationWhenHigherPriorityNotificationIsOlder() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(
                            key = "older-urgent",
                            packageName = "com.riffle.mail",
                            category = NotificationCategory.EMAIL,
                            priority = NotificationPriority.HIGH,
                            postedAtEpochMillis = 1_000L,
                        ),
                        notification(
                            key = "newer-routine",
                            packageName = "com.riffle.mail",
                            category = NotificationCategory.MESSAGE,
                            priority = NotificationPriority.DEFAULT,
                            postedAtEpochMillis = 2_000L,
                        ),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(NotificationCategory.MESSAGE, group.latestCategory)
    }

    @Test
    fun exposesClearableNotificationCount() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(key = "dismissable", packageName = "com.riffle.mail", canDismiss = true),
                        notification(key = "ongoing", packageName = "com.riffle.mail", canDismiss = false),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(1, group.clearableCount)
    }

    @Test
    fun exposesDismissibleNotificationKeys() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(key = "dismissable", packageName = "com.riffle.mail", canDismiss = true),
                        notification(key = "ongoing", packageName = "com.riffle.mail", canDismiss = false),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(listOf(LauncherNotificationKey("dismissable")), group.dismissibleNotificationKeys)
    }

    @Test
    fun exposesHighestNotificationPriority() {
        val group =
            grouper
                .groupByApp(
                    listOf(
                        notification(
                            key = "routine",
                            packageName = "com.riffle.mail",
                            priority = NotificationPriority.DEFAULT,
                        ),
                        notification(
                            key = "urgent",
                            packageName = "com.riffle.mail",
                            priority = NotificationPriority.HIGH,
                        ),
                    ),
                    nowEpochMillis = nowEpochMillis,
                ).single()

        assertEquals(NotificationPriority.HIGH, group.highestPriority)
    }

    @Test
    fun returnsEmptyGroupsForEmptyNotifications() {
        assertEquals(emptyList(), grouper.groupByApp(emptyList(), nowEpochMillis = nowEpochMillis))
    }

    private fun List<AppNotificationGroup>.group(
        packageName: String,
        profileId: AppProfileId,
    ): AppNotificationGroup =
        single { group ->
            group.packageName == AppPackageName(packageName) &&
                group.profileId == profileId
        }

    private fun notification(
        key: String,
        packageName: String,
        profileId: AppProfileId = AppProfile.personal().id,
        category: NotificationCategory = NotificationCategory.UNKNOWN,
        priority: NotificationPriority = NotificationPriority.UNKNOWN,
        canDismiss: Boolean = false,
        postedAtEpochMillis: Long = 1_000L,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            profileId = profileId,
            category = category,
            priority = priority,
            canDismiss = canDismiss,
            postedAtEpochMillis = postedAtEpochMillis,
        )
}
