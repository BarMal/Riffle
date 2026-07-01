package com.riffle.app.launcher

internal sealed interface LauncherAppActionRoute {
    data class LaunchApp(
        val action: LauncherShellAction.LaunchApp,
    ) : LauncherAppActionRoute

    data class LaunchAppShortcut(
        val action: LauncherShellAction.LaunchAppShortcut,
    ) : LauncherAppActionRoute

    data class OpenAppInfo(
        val action: LauncherShellAction.OpenAppInfo,
    ) : LauncherAppActionRoute

    data class UninstallApp(
        val action: LauncherShellAction.UninstallApp,
    ) : LauncherAppActionRoute

    data class AddAppToHome(
        val action: LauncherShellAction.AddAppToHome,
    ) : LauncherAppActionRoute

    data class RequestAddWidget(
        val action: LauncherShellAction.RequestAddWidget,
    ) : LauncherAppActionRoute

    data class AppState(
        val action: LauncherShellAction,
    ) : LauncherAppActionRoute
}

internal fun LauncherShellAction.launcherAppActionRoute(): LauncherAppActionRoute? =
    when (this) {
        is LauncherShellAction.LaunchApp -> LauncherAppActionRoute.LaunchApp(this)
        is LauncherShellAction.LaunchAppShortcut -> LauncherAppActionRoute.LaunchAppShortcut(this)
        is LauncherShellAction.OpenAppInfo -> LauncherAppActionRoute.OpenAppInfo(this)
        is LauncherShellAction.UninstallApp -> LauncherAppActionRoute.UninstallApp(this)
        is LauncherShellAction.AddAppToHome -> LauncherAppActionRoute.AddAppToHome(this)
        is LauncherShellAction.RequestAddWidget -> LauncherAppActionRoute.RequestAddWidget(this)
        is LauncherShellAction.HideApp,
        is LauncherShellAction.UnhideApp,
        LauncherShellAction.RefreshInstalledApps,
        is LauncherShellAction.AppDrawerQueryChanged,
        is LauncherShellAction.AppDrawerProfileFilterSelected,
        is LauncherShellAction.SearchQueryChanged,
        is LauncherShellAction.SearchProfileFilterSelected,
        LauncherShellAction.OpenWidgetPicker,
        LauncherShellAction.CloseWidgetPicker,
        -> LauncherAppActionRoute.AppState(this)

        else -> null
    }
