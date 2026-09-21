package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine

internal fun LauncherShellState.shouldEditSettingsTargetLayout(action: LauncherShellAction): Boolean =
    destination == ShellDestination.SETTINGS && action.isHomeLayoutConfigurationAction()

internal fun LauncherShellState.withSettingsHomePageEdit(
    action: LauncherShellAction,
    homePageEngine: HomePageEngine,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    when (
        val result =
            homePageEngine.applyHomeLayoutConfigurationEdit(
                action = action,
                layout = settingsTargetLayout(homeLayoutRepository),
            )
    ) {
        is HomePageEditResult.Updated ->
            withSettingsTargetLayout(
                layout = result.layout.withHomeScreenLibraryApps(installedApps),
                homeLayoutRepository = homeLayoutRepository,
            )

        is HomePageEditResult.Rejected -> this
    }

internal fun LauncherShellState.shouldEditSettingsTargetDock(action: LauncherShellAction): Boolean =
    destination == ShellDestination.SETTINGS && action.isDockConfigurationAction()

internal fun LauncherShellState.withSettingsDockEdit(
    action: LauncherShellAction,
    dockEngine: DockEngine,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    when (
        val result =
            dockEngine.applyEdit(
                action = action,
                layout = settingsTargetLayout(homeLayoutRepository),
            )
    ) {
        is DockEditResult.Updated ->
            withSettingsTargetLayout(
                layout = result.layout,
                homeLayoutRepository = homeLayoutRepository,
            )

        is DockEditResult.Rejected -> this
    }
