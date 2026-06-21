package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.notifications.AppNotificationCounter
import com.riffle.core.domain.launcher.notifications.AppNotificationGrouper
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository

fun LauncherShellState.withNotificationState(
    notificationRepository: LauncherNotificationRepository,
    appNotificationCounter: AppNotificationCounter,
    appNotificationGrouper: AppNotificationGrouper,
): LauncherShellState =
    notificationRepository.activeNotifications().let { activeNotifications ->
        copy(
            notificationCountsByPackage = appNotificationCounter.countByPackage(activeNotifications),
            notificationGroupsByApp = appNotificationGrouper.groupByApp(activeNotifications),
        )
    }
