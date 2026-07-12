package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability

internal fun LauncherShellState.withHomeLayout(
    layout: HomeLayout,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    currentLayoutSet(homeLayoutRepository)
        .withActiveLayout(layout)
        .also(homeLayoutRepository::saveHomeLayoutSet)
        .let { layoutSet ->
            copy(
                homeLayout = layoutSet.activeLayout,
                homeLayoutSet = layoutSet,
            )
        }

internal fun LauncherShellState.withSelectedHomeLayoutMode(
    mode: LauncherViewMode,
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
): LauncherShellState =
    currentLayoutSet(homeLayoutRepository)
        .withActiveLayout(homeLayout)
        .selectMode(mode, viewModeAvailability)
        .also(homeLayoutRepository::saveHomeLayoutSet)
        .let { layoutSet ->
            copy(
                homeLayout = layoutSet.activeLayout,
                homeLayoutSet = layoutSet,
                settingsLayoutDeviceClass = layoutSet.activeKey.deviceClass,
            )
        }

internal fun LauncherShellState.withSelectedHomeLayoutDeviceClass(
    deviceClass: HomeLayoutDeviceClass,
    availableDeviceClasses: Set<HomeLayoutDeviceClass> = setOf(deviceClass),
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
): LauncherShellState {
    val layoutSet = currentLayoutSet(homeLayoutRepository)
    val updatedAvailableDeviceClasses = availableLayoutDeviceClasses + availableDeviceClasses + deviceClass

    if (layoutSet.activeKey.deviceClass == deviceClass && layoutSet.activeLayout == homeLayout) {
        return copy(
            availableLayoutDeviceClasses = updatedAvailableDeviceClasses,
            settingsLayoutDeviceClass =
                settingsLayoutDeviceClassForDeviceSelection(
                    layoutSet = layoutSet,
                    availableDeviceClasses = updatedAvailableDeviceClasses,
                ),
        )
    }

    return layoutSet
        .withActiveLayout(homeLayout)
        .selectDeviceClass(deviceClass, viewModeAvailability)
        .also(homeLayoutRepository::saveHomeLayoutSet)
        .let { updatedLayoutSet ->
            copy(
                homeLayout = updatedLayoutSet.activeLayout,
                homeLayoutSet = updatedLayoutSet,
                availableLayoutDeviceClasses = updatedAvailableDeviceClasses,
                settingsLayoutDeviceClass =
                    settingsLayoutDeviceClassForDeviceSelection(
                        layoutSet = updatedLayoutSet,
                        availableDeviceClasses = updatedAvailableDeviceClasses,
                    ),
            )
        }
}

private fun LauncherShellState.settingsLayoutDeviceClassForDeviceSelection(
    layoutSet: HomeLayoutSet,
    availableDeviceClasses: Set<HomeLayoutDeviceClass>,
): HomeLayoutDeviceClass =
    when (destination) {
        ShellDestination.SETTINGS ->
            settingsLayoutDeviceClass
                .takeIf { selectedDeviceClass -> selectedDeviceClass in availableDeviceClasses }
                ?: layoutSet.activeKey.deviceClass

        else -> layoutSet.activeKey.deviceClass
    }

internal fun LauncherShellState.withSettingsLayoutDeviceClass(deviceClass: HomeLayoutDeviceClass): LauncherShellState {
    val supportsSettingsDeviceClass =
        deviceClass in availableLayoutDeviceClasses ||
            deviceClass == HomeLayoutDeviceClass.PHONE ||
            deviceClass == HomeLayoutDeviceClass.FOLDABLE ||
            deviceClass == HomeLayoutDeviceClass.DESKTOP

    return if (supportsSettingsDeviceClass) {
        copy(
            settingsLayoutDeviceClass = deviceClass,
            availableLayoutDeviceClasses = availableLayoutDeviceClasses + deviceClass,
        )
    } else {
        this
    }
}

internal fun LauncherShellState.withSettingsTargetLayout(
    layout: HomeLayout,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState {
    val key =
        HomeLayoutKey(
            viewMode = layout.viewMode,
            deviceClass = settingsLayoutDeviceClass,
        )
    val currentLayoutSet =
        currentLayoutSet(homeLayoutRepository)
            .withActiveLayout(homeLayout)
            .withLayout(key = key, layout = layout)
            .withPreferredMode(
                deviceClass = settingsLayoutDeviceClass,
                mode = key.viewMode,
            )
    val layoutSet =
        if (currentLayoutSet.activeKey.deviceClass == settingsLayoutDeviceClass) {
            currentLayoutSet.selectMode(key.viewMode)
        } else {
            currentLayoutSet
        }

    homeLayoutRepository.saveHomeLayoutSet(layoutSet)

    return copy(
        homeLayout =
            when (layoutSet.activeKey) {
                key -> layoutSet.activeLayout
                else -> homeLayout
            },
        homeLayoutSet = layoutSet,
    )
}

internal val LauncherShellState.settingsTargetLayoutKey: HomeLayoutKey
    get() =
        HomeLayoutKey(
            viewMode = homeLayoutSet.preferredModesByDeviceClass[settingsLayoutDeviceClass] ?: homeLayout.viewMode,
            deviceClass = settingsLayoutDeviceClass,
        )

internal fun LauncherShellState.settingsTargetLayout(homeLayoutRepository: HomeLayoutRepository): HomeLayout =
    currentLayoutSet(homeLayoutRepository).layoutFor(settingsTargetLayoutKey)

private fun LauncherShellState.currentLayoutSet(homeLayoutRepository: HomeLayoutRepository): HomeLayoutSet =
    homeLayoutRepository.loadHomeLayoutSet() ?: homeLayoutSet.withActiveLayout(homeLayout)
