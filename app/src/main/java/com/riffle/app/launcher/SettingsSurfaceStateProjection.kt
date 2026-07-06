package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState

internal fun LauncherShellState.settingsSurfaceState(
    appVersionLabel: String = "",
    appBuildIdentityLabel: String = "",
): SettingsSurfaceState =
    SettingsSurfaceState(
        settings = launcherSettings,
        homeLayout = homeLayoutSet.layoutFor(settingsTargetLayoutKey),
        selectedLayoutDeviceClass = settingsLayoutDeviceClass,
        availableLayoutDeviceClasses = availableLayoutDeviceClasses,
        homeRoleStatus = homeRoleStatus,
        overlayDockPermissionStatus = overlayDockPermissionStatus,
        notificationAccessStatus = notificationAccessStatus,
        installedApps = installedApps,
        hiddenApps = hiddenApps,
        appVersionLabel = appVersionLabel,
        appBuildIdentityLabel = appBuildIdentityLabel,
    )
