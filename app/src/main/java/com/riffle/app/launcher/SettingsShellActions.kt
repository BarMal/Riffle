package com.riffle.app.launcher

internal fun interface LauncherSettingsActionHandler {
    fun handle(action: LauncherShellAction): Boolean
}

internal class DefaultLauncherSettingsActionHandler(
    private val callbacks: LauncherSettingsActionCallbacks,
) : LauncherSettingsActionHandler {
    override fun handle(action: LauncherShellAction): Boolean =
        when (val route = action.launcherSettingsActionRoute()) {
            is LauncherSettingsActionRoute.SettingsState -> {
                callbacks.applySettingsState(route.action)
                true
            }

            LauncherSettingsActionRoute.RequestNotificationAccess -> {
                callbacks.requestNotificationAccess()
                true
            }

            LauncherSettingsActionRoute.RequestOverlayDockPermission -> {
                callbacks.requestOverlayDockPermission()
                true
            }

            LauncherSettingsActionRoute.ChangeWallpaper -> {
                callbacks.changeWallpaper()
                true
            }

            LauncherSettingsActionRoute.ExportBackup -> {
                callbacks.exportBackup()
                true
            }

            LauncherSettingsActionRoute.RequestImportBackup -> {
                callbacks.importBackup()
                true
            }

            null -> false
        }
}

internal data class LauncherSettingsActionCallbacks(
    val applySettingsState: (LauncherShellAction) -> Unit,
    val requestNotificationAccess: () -> Unit,
    val requestOverlayDockPermission: () -> Unit,
    val changeWallpaper: () -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
)

internal sealed interface LauncherSettingsActionRoute {
    data class SettingsState(
        val action: LauncherShellAction,
    ) : LauncherSettingsActionRoute

    data object RequestNotificationAccess : LauncherSettingsActionRoute

    data object RequestOverlayDockPermission : LauncherSettingsActionRoute

    data object ChangeWallpaper : LauncherSettingsActionRoute

    data object ExportBackup : LauncherSettingsActionRoute

    data object RequestImportBackup : LauncherSettingsActionRoute
}

internal fun LauncherShellAction.launcherSettingsActionRoute(): LauncherSettingsActionRoute? =
    when (this) {
        is LauncherShellAction.SelectWallpaperSource,
        is LauncherShellAction.SelectLauncherThemeMode,
        is LauncherShellAction.SelectLauncherThemePreset,
        is LauncherShellAction.SelectLauncherThemeAccent,
        is LauncherShellAction.SelectLauncherThemeColor,
        is LauncherShellAction.SelectLauncherThemeCornerStyle,
        is LauncherShellAction.SelectLauncherThemeTypography,
        is LauncherShellAction.SelectWallpaperScrollMode,
        is LauncherShellAction.SelectFullscreenHomeEnabled,
        is LauncherShellAction.SelectHomeStatusBarHidden,
        is LauncherShellAction.SelectHomeNavigationBarHidden,
        is LauncherShellAction.SelectHomeSwipeGestureAction,
        is LauncherShellAction.SelectHomeGestureAction,
        LauncherShellAction.ResetHomeSwipeGestureActions,
        is LauncherShellAction.SelectHapticFeedbackStrength,
        is LauncherShellAction.SelectReducedMotionEnabled,
        is LauncherShellAction.SelectMotionPerformanceTargetFps,
        is LauncherShellAction.SelectContextualEnabled,
        is LauncherShellAction.UpdateTimeScapeAppearance,
        is LauncherShellAction.SelectOverlayDockEnabled,
        is LauncherShellAction.SelectOverlayDockEdge,
        is LauncherShellAction.SelectOverlayDockHandleThickness,
        is LauncherShellAction.SelectOverlayDockHandleHeight,
        is LauncherShellAction.SelectOverlayDockVerticalOffset,
        is LauncherShellAction.SelectOverlayDockHandleAlpha,
        is LauncherShellAction.SelectOverlayDockExpandedIconSize,
        is LauncherShellAction.SelectOverlayDockExpandedOrientation,
        is LauncherShellAction.SelectOverlayDockShowLabels,
        is LauncherShellAction.AddAppToFloatingDock,
        is LauncherShellAction.AddAppShortcutToFloatingDock,
        is LauncherShellAction.RemoveFloatingDockShortcut,
        is LauncherShellAction.MoveFloatingDockShortcut,
        is LauncherShellAction.SelectSettingsLayoutDeviceClass,
        is LauncherShellAction.ImportLauncherBackup,
        is LauncherShellAction.SelectSearchResultPresentation,
        is LauncherShellAction.SelectAppDrawerPresentation,
        is LauncherShellAction.SelectAppDrawerIconGridColumns,
        -> LauncherSettingsActionRoute.SettingsState(this)

        LauncherShellAction.RequestNotificationAccess -> LauncherSettingsActionRoute.RequestNotificationAccess
        LauncherShellAction.RequestOverlayDockPermission -> LauncherSettingsActionRoute.RequestOverlayDockPermission
        LauncherShellAction.ChangeWallpaper -> LauncherSettingsActionRoute.ChangeWallpaper
        LauncherShellAction.ExportLauncherBackup -> LauncherSettingsActionRoute.ExportBackup
        LauncherShellAction.RequestImportLauncherBackup -> LauncherSettingsActionRoute.RequestImportBackup
        else -> null
    }
