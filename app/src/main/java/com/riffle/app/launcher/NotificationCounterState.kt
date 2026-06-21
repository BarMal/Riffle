package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.notifications.AppNotificationCounter
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository

fun LauncherShellState.withNotificationCounts(
    notificationRepository: LauncherNotificationRepository,
    appNotificationCounter: AppNotificationCounter,
): LauncherShellState =
    copy(
        notificationCountsByPackage =
            appNotificationCounter.countByPackage(
                notificationRepository.activeNotifications(),
            ),
    )
