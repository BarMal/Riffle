package com.riffle.app.launcher

internal class LauncherAppActionHandler(
    private val callbacks: LauncherAppActionCallbacks,
) {
    fun handle(action: LauncherShellAction): Boolean =
        when (val route = action.launcherAppActionRoute()) {
            is LauncherAppActionRoute.LaunchApp -> {
                callbacks.launch.launchApp(route.action)
                true
            }

            is LauncherAppActionRoute.LaunchAppShortcut -> {
                callbacks.launch.launchAppShortcut(route.action)
                true
            }

            is LauncherAppActionRoute.OpenAppInfo -> {
                callbacks.launch.openAppInfo(route.action)
                true
            }

            is LauncherAppActionRoute.UninstallApp -> {
                callbacks.launch.uninstallApp(route.action)
                true
            }

            is LauncherAppActionRoute.AddAppToHome -> {
                callbacks.addAppToHome(route.action)
                true
            }

            is LauncherAppActionRoute.RequestAddWidget -> {
                callbacks.requestAddWidget(route.action)
                true
            }

            LauncherAppActionRoute.RefreshInstalledApps -> {
                callbacks.applyAppState(action)
                callbacks.appListRefreshed()
                true
            }

            is LauncherAppActionRoute.AppVisibilityState -> {
                callbacks.applyAppState(route.action)
                true
            }

            is LauncherAppActionRoute.AppListState -> {
                callbacks.applyAppState(route.action)
                true
            }

            is LauncherAppActionRoute.WidgetPickerState -> {
                callbacks.applyAppState(route.action)
                true
            }

            null -> false
        }
}

internal data class LauncherAppActionCallbacks(
    val launch: LauncherAppLaunchCallbacks,
    val addAppToHome: (LauncherShellAction.AddAppToHome) -> Unit,
    val requestAddWidget: (LauncherShellAction.RequestAddWidget) -> Unit,
    val applyAppState: (LauncherShellAction) -> Unit,
    val appListRefreshed: () -> Unit,
)

internal data class LauncherAppLaunchCallbacks(
    val launchApp: (LauncherShellAction.LaunchApp) -> Unit,
    val launchAppShortcut: (LauncherShellAction.LaunchAppShortcut) -> Unit,
    val openAppInfo: (LauncherShellAction.OpenAppInfo) -> Unit,
    val uninstallApp: (LauncherShellAction.UninstallApp) -> Unit,
)
