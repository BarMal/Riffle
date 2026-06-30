package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutRepository

internal fun LauncherShellState.withLibraryReflowAfterShortcutMove(
    action: LauncherShellAction,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    when (action) {
        is LauncherShellAction.MoveHomeShortcutToCell -> withHomeScreenLibraryApps(homeLayoutRepository)
        else -> this
    }
