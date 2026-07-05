package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.HomeLayoutRepository

internal class LauncherDockEditReducer(
    private val dockEngine: DockEngine,
    private val homeLayoutRepository: HomeLayoutRepository,
) {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState =
        if (state.shouldEditSettingsTargetDock(action)) {
            state.withSettingsDockEdit(
                action = action,
                dockEngine = dockEngine,
                homeLayoutRepository = homeLayoutRepository,
            )
        } else {
            when (val result = dockEngine.applyEdit(action = action, layout = state.homeLayout)) {
                is DockEditResult.Updated -> state.withHomeLayout(result.layout, homeLayoutRepository)
                is DockEditResult.Rejected -> state
            }
        }
}
