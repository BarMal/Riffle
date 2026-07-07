package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId

internal class LauncherActivityActionHandler(
    private val requestDefaultHome: () -> Unit,
    private val navigate: (ShellNavigationAction) -> Unit,
    private val editHomePage: (LauncherShellAction) -> Unit,
    private val editHomeShortcut: (LauncherShellAction) -> Unit,
    private val editDock: (LauncherShellAction) -> Unit,
    private val hostedWidgetIdForRemovedItem: (LauncherItemId) -> HostedWidgetId?,
    private val deleteHostedWidget: (HostedWidgetId) -> Unit,
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
                if (action is LauncherShellAction.RemoveHomeShortcut) {
                    hostedWidgetIdForRemovedItem(action.itemId)?.let(deleteHostedWidget)
                }
                editHomeShortcut(action)
                true
            }

            LauncherActivityRoute.DockEdit -> {
                if (action is LauncherShellAction.RemoveDockShortcut) {
                    hostedWidgetIdForRemovedItem(action.itemId)?.let(deleteHostedWidget)
                }
                editDock(action)
                true
            }

            null -> false
        }
}
