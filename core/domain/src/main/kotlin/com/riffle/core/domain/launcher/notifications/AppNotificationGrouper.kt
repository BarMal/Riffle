package com.riffle.core.domain.launcher.notifications

class AppNotificationGrouper(
    private val ageBucketClassifier: NotificationAgeBucketClassifier = NotificationAgeBucketClassifier(),
) {
    fun groupByApp(
        notifications: List<LauncherNotification>,
        nowEpochMillis: Long,
    ): List<AppNotificationGroup> =
        notifications
            .groupBy { notification ->
                AppNotificationGroupKey(
                    packageName = notification.packageName,
                    profileId = notification.profileId,
                )
            }
            .map { (key, groupedNotifications) ->
                val sortedNotifications = groupedNotifications.sortedForDisplay()
                val latestNotification = groupedNotifications.latestForGroup()

                AppNotificationGroup(
                    packageName = key.packageName,
                    profileId = key.profileId,
                    latestCategory = latestNotification.category,
                    latestAgeBucket = ageBucketClassifier.bucketFor(latestNotification, nowEpochMillis),
                    notifications = sortedNotifications,
                )
            }
            .sortedWith(displayOrder)

    private fun List<LauncherNotification>.sortedForDisplay(): List<LauncherNotification> {
        return sortedWith(notificationDisplayOrder)
    }

    private fun List<LauncherNotification>.latestForGroup(): LauncherNotification {
        return minWith(latestNotificationOrder)
    }

    private companion object {
        val notificationDisplayOrder: Comparator<LauncherNotification> =
            compareByDescending<LauncherNotification> { notification -> notification.priority.rank }
                .thenByDescending { notification -> notification.postedAtEpochMillis }
                .thenBy { notification -> notification.packageName.value }
                .thenBy { notification -> notification.profileId.value }
                .thenBy { notification -> notification.key.value }

        val latestNotificationOrder: Comparator<LauncherNotification> =
            compareByDescending<LauncherNotification> { notification -> notification.postedAtEpochMillis }
                .thenBy { notification -> notification.packageName.value }
                .thenBy { notification -> notification.profileId.value }
                .thenBy { notification -> notification.key.value }

        val displayOrder: Comparator<AppNotificationGroup> =
            compareByDescending<AppNotificationGroup> { group -> group.highestPriority.rank }
                .thenByDescending { group -> group.latestPostedAtEpochMillis }
                .thenBy { group -> group.packageName.value }
                .thenBy { group -> group.profileId.value }
    }
}
