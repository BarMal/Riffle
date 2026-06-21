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

                AppNotificationGroup(
                    packageName = key.packageName,
                    profileId = key.profileId,
                    latestCategory = sortedNotifications.first().category,
                    latestAgeBucket = ageBucketClassifier.bucketFor(sortedNotifications.first(), nowEpochMillis),
                    notifications = sortedNotifications,
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
