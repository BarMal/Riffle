package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode

internal fun LauncherShellState.withHomeLayout(
    layout: HomeLayout,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    copy(homeLayout = layout)
        .also { state -> homeLayoutRepository.saveHomeLayout(state.homeLayout) }

internal fun LauncherShellState.withSelectedHomeLayoutMode(
    mode: LauncherViewMode,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    (homeLayoutRepository.loadHomeLayoutSet() ?: HomeLayoutSet.fromLayout(homeLayout))
        .withActiveLayout(homeLayout)
        .selectMode(mode)
        .also(homeLayoutRepository::saveHomeLayoutSet)
        .let { layoutSet -> copy(homeLayout = layoutSet.activeLayout) }

internal fun LauncherShellState.withSelectedHomeLayoutDeviceClass(
    deviceClass: HomeLayoutDeviceClass,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    (homeLayoutRepository.loadHomeLayoutSet() ?: HomeLayoutSet.fromLayout(homeLayout))
        .withActiveLayout(homeLayout)
        .selectDeviceClass(deviceClass)
        .also(homeLayoutRepository::saveHomeLayoutSet)
        .let { layoutSet -> copy(homeLayout = layoutSet.activeLayout) }
