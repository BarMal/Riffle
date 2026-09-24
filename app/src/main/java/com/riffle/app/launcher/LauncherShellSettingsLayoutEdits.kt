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
                layout = settingsTargetLayout,
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
): LauncherShellState {
    val layout = settingsTargetLayout
    val result =
        if (action is LauncherShellAction.SelectDockPosition && layout.usesCompactLibraryPacking) {
            layout.repackedForDockPosition(action.position, installedApps)
        } else {
            dockEngine.applyEdit(action = action, layout = layout)
        }

    return when (result) {
        is DockEditResult.Updated ->
            withSettingsTargetLayout(
                layout = result.layout,
                homeLayoutRepository = homeLayoutRepository,
            ).copy(dockEditRejectionReason = null)

        is DockEditResult.Rejected -> copy(dockEditRejectionReason = result.reason)
    }
}
