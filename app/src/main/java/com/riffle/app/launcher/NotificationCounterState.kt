package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.notifications.AppNotificationCounter
import com.riffle.core.domain.launcher.notifications.AppNotificationGrouper
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationStaleFilter

fun LauncherShellState.withNotificationState(
    notificationRepository: LauncherNotificationRepository,
    appNotificationCounter: AppNotificationCounter,
    appNotificationGrouper: AppNotificationGrouper,
    notificationStaleFilter: NotificationStaleFilter,
    nowEpochMillis: Long,
): LauncherShellState {
    val activeNotifications =
        notificationStaleFilter.activeForLauncherState(
            notifications = notificationRepository.activeNotifications(),
            nowEpochMillis = nowEpochMillis,
        ).filterNotHidden(hiddenApps = hiddenApps.map { app -> app.identity }.toSet())

    return copy(
        notificationCountsByPackage = appNotificationCounter.countByPackage(activeNotifications),
        notificationCountsByCategory = appNotificationCounter.countByCategory(activeNotifications),
        notificationGroupsByApp =
            appNotificationGrouper.groupByApp(
                notifications = activeNotifications,
                nowEpochMillis = nowEpochMillis,
            ),
    )
}

private fun List<LauncherNotification>.filterNotHidden(hiddenApps: Set<AppIdentity>): List<LauncherNotification> =
    filterNot { notification ->
        hiddenApps.any { identity ->
            identity.packageName == notification.packageName &&
                identity.profile.id == notification.profileId
        }
    }
