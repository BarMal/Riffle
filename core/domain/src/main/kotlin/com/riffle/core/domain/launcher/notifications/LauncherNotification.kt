package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId

data class LauncherNotification(
    val key: LauncherNotificationKey,
    val packageName: AppPackageName,
    val profileId: AppProfileId = AppProfile.personal().id,
    val postedAtEpochMillis: Long,
)

@JvmInline
value class LauncherNotificationKey(val value: String)

fun interface LauncherNotificationRepository {
    fun activeNotifications(): List<LauncherNotification>
}
