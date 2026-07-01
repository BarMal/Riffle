package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode

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
): LauncherShellState =
    currentLayoutSet(homeLayoutRepository)
        .withActiveLayout(homeLayout)
        .selectMode(mode)
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
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState {
    val layoutSet = currentLayoutSet(homeLayoutRepository)

    if (layoutSet.activeKey.deviceClass == deviceClass && layoutSet.activeLayout == homeLayout) {
        return this
    }

    return layoutSet
        .withActiveLayout(homeLayout)
        .selectDeviceClass(deviceClass)
        .also(homeLayoutRepository::saveHomeLayoutSet)
        .let { updatedLayoutSet ->
            copy(
                homeLayout = updatedLayoutSet.activeLayout,
                homeLayoutSet = updatedLayoutSet,
                settingsLayoutDeviceClass =
                    when (destination) {
                        ShellDestination.SETTINGS -> settingsLayoutDeviceClass
                        else -> updatedLayoutSet.activeKey.deviceClass
                    },
            )
        }
}

internal fun LauncherShellState.withSettingsLayoutDeviceClass(deviceClass: HomeLayoutDeviceClass): LauncherShellState =
    copy(settingsLayoutDeviceClass = deviceClass)

internal fun LauncherShellState.withSettingsTargetLayout(
    layout: HomeLayout,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState {
    val key =
        HomeLayoutKey(
            viewMode = homeLayout.viewMode,
            deviceClass = settingsLayoutDeviceClass,
        )
    val layoutSet =
        currentLayoutSet(homeLayoutRepository)
            .withActiveLayout(homeLayout)
            .withLayout(key = key, layout = layout)

    homeLayoutRepository.saveHomeLayoutSet(layoutSet)

    return copy(
        homeLayout = layoutSet.activeLayout,
        homeLayoutSet = layoutSet,
    )
}

internal val LauncherShellState.settingsTargetLayoutKey: HomeLayoutKey
    get() =
        HomeLayoutKey(
            viewMode = homeLayout.viewMode,
            deviceClass = settingsLayoutDeviceClass,
        )

internal fun LauncherShellState.settingsTargetLayout(homeLayoutRepository: HomeLayoutRepository): HomeLayout =
    currentLayoutSet(homeLayoutRepository).layoutFor(settingsTargetLayoutKey)

private fun LauncherShellState.currentLayoutSet(homeLayoutRepository: HomeLayoutRepository): HomeLayoutSet =
    homeLayoutRepository.loadHomeLayoutSet() ?: homeLayoutSet.withActiveLayout(homeLayout)
