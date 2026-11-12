package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceSet

/**
 * The one-time repair of what the cut-over to the pool (ADR 0003, P8) left behind: until then the dock a workspace drew
 * was the layout's shared dock with the shown workspace's override on top (`WorkspaceDock.presentation`: icon size and
 * home edge), and the cut-over kept only the shared dock. A user who sized the dock icons through the workspace
 * therefore saw them fall back to the shared size. The stored workspaces are untouched (still decodable), so the value
 * is still there to read.
 *
 * Only a value the user has not since replaced is carried over, and nothing is guessed: the override's icon size
 * replaces the pool dock's only while that still holds a default size (its device class's, or the dock model's own:
 * what the shared dock held when nobody sized it), and its edge only while the pool dock names none. A hidden override
 * is not carried: a hidden dock is a state the user would have had to find a way back from, not a size to restore.
 */
object PoolDockRepair {
    private fun untouchedSizes(deviceClass: HomeLayoutDeviceClass) =
        setOf(HomeLayoutDefaults.standard(deviceClass).dock.iconSizeDp, DEFAULT_DOCK_ICON_SIZE_DP)

    /** [state] with each pool dock's size and edge restored from the active workspace of its layout in [workspaces]. */
    fun restoreWorkspaceOverrides(
        state: PoolStoreState,
        workspaces: WorkspaceSet?,
    ): PoolStoreState {
        if (workspaces == null) return state
        val pools =
            state.pools.mapValues { (deviceClass, pool) ->
                val dock = pool.dock
                val presentation =
                    if (dock == null || !workspaces.hasLayout(deviceClass)) {
                        null
                    } else {
                        workspaces.workspacesFor(deviceClass).active.dock.presentation
                    }
                if (dock == null || presentation == null) {
                    pool
                } else {
                    val settings = dock.settings
                    val size = presentation.iconSizeDp
                    val edge = presentation.edge
                    val repaired =
                        settings.copy(
                            iconSizeDp =
                                if (size != null && settings.iconSizeDp in untouchedSizes(deviceClass)) {
                                    size
                                } else {
                                    settings.iconSizeDp
                                },
                            position = if (edge != null && settings.position == null) edge else settings.position,
                        )
                    if (repaired == settings) pool else pool.copy(dock = dock.copy(settings = repaired))
                }
            }
        return if (pools == state.pools) state else state.copy(pools = pools)
    }
}
