package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction

internal class LauncherActivityActionHandler(
    private val requestDefaultHome: () -> Unit,
    private val navigate: (ShellNavigationAction) -> Unit,
    private val editHomePage: (LauncherShellAction) -> Unit,
    private val editHomeShortcut: (LauncherShellAction) -> Unit,
    private val editDock: (LauncherShellAction) -> Unit,
) {
    fun handle(action: LauncherShellAction): Boolean =
        when (val route = action.launcherActivityRoute()) {
            LauncherActivityRoute.RequestDefaultHome -> {
                requestDefaultHome()
                true
            }

            LauncherActivityRoute.OpenDefaultHome -> {
                editHomePage(action)
                navigate(ShellNavigationAction.OpenHome)
                true
            }

            is LauncherActivityRoute.Navigation -> {
                navigate(route.action)
                true
            }

            LauncherActivityRoute.HomePageEdit -> {
                editHomePage(action)
                true
            }

            LauncherActivityRoute.HomeShortcutEdit -> {
                editHomeShortcut(action)
                true
            }

            LauncherActivityRoute.DockEdit -> {
                editDock(action)
                true
            }

            null -> false
        }
}
