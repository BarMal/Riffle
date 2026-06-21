package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.LauncherSettings

data class LauncherShellState(
    val firstRunStatus: FirstRunStatus = FirstRunStatus.NEEDS_HOME_ROLE,
    val homeRoleStatus: HomeRoleStatus = HomeRoleStatus.UNKNOWN,
    val destination: ShellDestination = ShellDestination.HOME,
    val homeLayout: HomeLayout = HomeLayoutDefaults.standard(),
    val launcherSettings: LauncherSettings = LauncherSettings(),
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    val notificationCountsByPackage: Map<AppPackageName, Int> = emptyMap(),
    val notificationCountsByCategory: Map<NotificationCategory, Int> = emptyMap(),
    val notificationGroupsByApp: List<AppNotificationGroup> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<InstalledApp> = emptyList(),
) {
    val shouldShowDefaultHomePrompt: Boolean =
        firstRunStatus != FirstRunStatus.COMPLETE && homeRoleStatus != HomeRoleStatus.DEFAULT_HOME

    val shouldShowEmptyHome: Boolean =
        firstRunStatus == FirstRunStatus.COMPLETE || homeRoleStatus == HomeRoleStatus.DEFAULT_HOME
}

enum class FirstRunStatus {
    NEEDS_HOME_ROLE,
    REQUESTING_HOME_ROLE,
    COMPLETE,
}
