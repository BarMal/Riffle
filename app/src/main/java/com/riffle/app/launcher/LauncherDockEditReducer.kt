package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.ModeSurface
import com.riffle.core.domain.launcher.home.withDockEdge

internal class LauncherDockEditReducer(
    private val dockEngine: DockEngine,
    private val homeLayoutRepository: HomeLayoutRepository,
) {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState {
        val editsSettingsTarget = state.shouldEditSettingsTargetDock(action)
        val reduced =
            if (editsSettingsTarget) {
                state.withSettingsDockEdit(
                    action = action,
                    dockEngine = dockEngine,
                    homeLayoutRepository = homeLayoutRepository,
                )
            } else {
                when (val result = dockEngine.applyEdit(action = action, layout = state.homeLayout)) {
                    is DockEditResult.Updated ->
                        state.withHomeLayout(result.layout, homeLayoutRepository).copy(dockEditRejectionReason = null)

                    is DockEditResult.Rejected -> state.copy(dockEditRejectionReason = result.reason)
                }
            }
        if (action !is LauncherShellAction.SelectDockPosition || reduced.dockEditRejectionReason != null) {
            return reduced
        }
        // There is still one dock-edge setting (#1242 splits it per surface). Until then, choosing an
        // edge moves the dock in Library as well as Home, as it did while the edge was shared.
        val deviceClass =
            if (editsSettingsTarget) state.settingsLayoutDeviceClass else state.homeLayoutSet.activeKey.deviceClass
        return reduced.withLibraryDockEdge(deviceClass, action.position)
    }

    private fun LauncherShellState.withLibraryDockEdge(
        deviceClass: HomeLayoutDeviceClass,
        edge: DockPosition,
    ): LauncherShellState {
        val layoutSet = homeLayoutSet.withDockEdge(deviceClass, ModeSurface.LIBRARY, edge)
        if (layoutSet == homeLayoutSet) return this
        homeLayoutRepository.saveHomeLayoutSet(layoutSet)
        return copy(homeLayoutSet = layoutSet)
    }
}
