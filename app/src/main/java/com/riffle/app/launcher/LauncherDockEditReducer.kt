package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.ModeSurface
import com.riffle.core.domain.launcher.home.withDockEdge
import com.riffle.core.domain.launcher.settings.dockDeviceClass

internal class LauncherDockEditReducer(
    private val dockEngine: DockEngine,
    private val homeLayoutRepository: HomeLayoutRepository,
) {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState =
        if (action is LauncherShellAction.SelectPostureDockEdge) {
            reducePostureEdge(state, action)
        } else {
            reduceDockEdit(state, action)
        }

    private fun reduceDockEdit(
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

    /**
     * Settings > Layout: the Dock edge of a posture. It is the same stored edge the Dock page sets, for the posture's
     * device class, so this runs the Dock page's own edit against that class and puts the page's own class back.
     */
    private fun reducePostureEdge(
        state: LauncherShellState,
        action: LauncherShellAction.SelectPostureDockEdge,
    ): LauncherShellState {
        val target = action.posture.dockDeviceClass(state.homeLayoutSet.activeKey.deviceClass)
        val edit = LauncherShellAction.SelectDockPosition(action.position)
        if (state.destination != ShellDestination.SETTINGS) {
            // Outside Settings the edit lands on the layout being held, which is only the posture's when it is it.
            return if (target == state.homeLayoutSet.activeKey.deviceClass) reduce(state, edit) else state
        }
        val shown = state.settingsLayoutDeviceClass
        return reduce(state.copy(settingsLayoutDeviceClass = target), edit).copy(settingsLayoutDeviceClass = shown)
    }

    private fun LauncherShellState.withLibraryDockEdge(
        deviceClass: HomeLayoutDeviceClass,
        edge: DockPosition,
    ): LauncherShellState {
        val layoutSet = homeLayoutSet.withDockEdge(deviceClass, ModeSurface.LIBRARY, edge)
        if (layoutSet == homeLayoutSet) return this
        return copy(homeLayoutSet = homeLayoutRepository.commitHomeLayoutSet(layoutSet))
    }
}
