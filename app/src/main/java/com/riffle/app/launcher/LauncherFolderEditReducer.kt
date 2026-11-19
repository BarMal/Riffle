package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.FolderEditResult
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.HomeLayoutRepository

internal class LauncherFolderEditReducer(
    private val folderEngine: FolderEngine,
    private val homeLayoutRepository: HomeLayoutRepository,
) {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState =
        when (
            val result =
                folderEngine.applyEdit(
                    action = action,
                    layout = state.homeLayout,
                )
        ) {
            is FolderEditResult.Updated -> state.withHomeLayout(result.layout, homeLayoutRepository)
            is FolderEditResult.Rejected -> state
        }
}
