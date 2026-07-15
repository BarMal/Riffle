package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings

data class SettingsSurfaceState(
    val settings: LauncherSettings,
    val homeLayout: HomeLayout,
    val selectedLayoutDeviceClass: HomeLayoutDeviceClass,
    val availableLayoutDeviceClasses: Set<HomeLayoutDeviceClass>,
    val availableLauncherViewModes: List<LauncherViewMode>,
    val homeRoleStatus: HomeRoleStatus,
    val overlayDockPermissionStatus: OverlayDockPermissionStatus,
    val notificationAccessStatus: NotificationAccessStatus,
    val installedApps: List<InstalledApp>,
    val appShortcutsByApp: AppShortcutsByApp,
    val hiddenApps: List<InstalledApp>,
    val appVersionLabel: String,
    val appBuildIdentityLabel: String,
)
