package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability

internal fun LauncherShellState.settingsSurfaceState(
    appVersionLabel: String = "",
    appBuildIdentityLabel: String = "",
    viewModeAvailability: LauncherViewModeAvailability = defaultLauncherViewModeAvailability(),
): SettingsSurfaceState {
    val targetLayoutKey =
        viewModeAvailability.availableKeyFor(
            layoutSet = homeLayoutSet,
            deviceClass = settingsLayoutDeviceClass,
        )

    return SettingsSurfaceState(
        settings = launcherSettings,
        homeLayout = homeLayoutSet.layoutFor(targetLayoutKey),
        selectedLayoutDeviceClass = settingsLayoutDeviceClass,
        availableLayoutDeviceClasses = availableLayoutDeviceClasses,
        availableLauncherViewModes = viewModeAvailability.availableModes(settingsLayoutDeviceClass),
        homeRoleStatus = homeRoleStatus,
        overlayDockPermissionStatus = overlayDockPermissionStatus,
        notificationAccessStatus = notificationAccessStatus,
        installedApps = installedApps,
        appShortcutsByApp = appShortcutsByApp,
        hiddenApps = hiddenApps,
        appVersionLabel = appVersionLabel,
        appBuildIdentityLabel = appBuildIdentityLabel,
    )
}
