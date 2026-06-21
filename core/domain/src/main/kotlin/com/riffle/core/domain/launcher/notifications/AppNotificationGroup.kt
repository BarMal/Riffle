package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId

data class AppNotificationGroup(
    val packageName: AppPackageName,
    val profileId: AppProfileId,
    val notifications: List<LauncherNotification>,
) {
    val count: Int
        get() = notifications.size

    val latestPostedAtEpochMillis: Long
        get() = notifications.maxOfOrNull { notification -> notification.postedAtEpochMillis } ?: 0L
}

data class AppNotificationGroupKey(
    val packageName: AppPackageName,
    val profileId: AppProfileId,
)
