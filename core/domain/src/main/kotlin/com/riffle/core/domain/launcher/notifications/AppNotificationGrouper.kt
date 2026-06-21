package com.riffle.core.domain.launcher.notifications

class AppNotificationGrouper {
    fun groupByApp(notifications: List<LauncherNotification>): List<AppNotificationGroup> =
        notifications
            .groupBy { notification ->
                AppNotificationGroupKey(
                    packageName = notification.packageName,
                    profileId = notification.profileId,
                )
            }
            .map { (key, groupedNotifications) ->
                AppNotificationGroup(
                    packageName = key.packageName,
                    profileId = key.profileId,
                    notifications = groupedNotifications.sortedForDisplay(),
                )
            }
            .sortedWith(displayOrder)

    private fun List<LauncherNotification>.sortedForDisplay(): List<LauncherNotification> =
        sortedWith(
            compareByDescending<LauncherNotification> { notification -> notification.postedAtEpochMillis }
                .thenBy { notification -> notification.key.value },
        )

    private companion object {
        val displayOrder: Comparator<AppNotificationGroup> =
            compareByDescending<AppNotificationGroup> { group -> group.latestPostedAtEpochMillis }
                .thenBy { group -> group.packageName.value }
                .thenBy { group -> group.profileId.value }
    }
}
