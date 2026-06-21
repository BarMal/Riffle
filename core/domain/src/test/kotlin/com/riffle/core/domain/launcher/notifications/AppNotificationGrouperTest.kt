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
        postedAtEpochMillis: Long = 1_000L,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            profileId = profileId,
            postedAtEpochMillis = postedAtEpochMillis,
        )
}
