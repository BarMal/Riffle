package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.dockFor
import com.riffle.core.domain.launcher.home.sharedDockPositions
import com.riffle.core.domain.launcher.settings.LayoutPosture
import com.riffle.core.domain.launcher.settings.defaultDockEdge
import com.riffle.core.domain.launcher.settings.dockDeviceClass

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
        firstRunStatus = firstRunStatus,
        homeRoleStatus = homeRoleStatus,
        overlayDockPermissionStatus = overlayDockPermissionStatus,
        notificationAccessStatus = notificationAccessStatus,
        installedApps = installedApps,
        appShortcutsByApp = appShortcutsByApp,
        hiddenApps = hiddenApps,
        appVersionLabel = appVersionLabel,
        appBuildIdentityLabel = appBuildIdentityLabel,
        modePair = homeLayoutSet.modePairFor(settingsLayoutDeviceClass),
        calendarAccessStatus = calendarAccessStatus,
        postureDockEdges = postureDockEdges(),
    )
}

/** The edge each posture's Dock stands on: the stored choice for the device class Settings edits, else its default. */
private fun LauncherShellState.postureDockEdges(): Map<LayoutPosture, DockPosition> {
    val layoutSet = homeLayoutSet.withActiveLayout(homeLayout)
    return LayoutPosture.entries.associateWith { posture ->
        val deviceClass = posture.dockDeviceClass(homeLayoutSet.activeKey.deviceClass)
        layoutSet.dockFor(deviceClass).position?.takeIf { it in sharedDockPositions } ?: deviceClass.defaultDockEdge
    }
}
