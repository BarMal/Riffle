package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.notifications.AppNotificationCounter
import com.riffle.core.domain.launcher.notifications.AppNotificationGrouper
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
        )

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
