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

            LauncherSettingsActionRoute.RequestCalendarAccess -> {
                callbacks.requestCalendarAccess()
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

            LauncherSettingsActionRoute.UndoImportBackup -> {
                callbacks.undoImportBackup()
                true
            }

            LauncherSettingsActionRoute.DismissImportUndo -> {
                callbacks.dismissImportUndo()
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
    val undoImportBackup: () -> Unit = {},
    val dismissImportUndo: () -> Unit = {},
    /** Explicit, user-initiated only: launches the system dialog or app settings per the calendar policy. */
    val requestCalendarAccess: () -> Unit = {},
)

internal sealed interface LauncherSettingsActionRoute {
    data class SettingsState(
        val action: LauncherShellAction,
    ) : LauncherSettingsActionRoute

    data object RequestNotificationAccess : LauncherSettingsActionRoute

    data object RequestOverlayDockPermission : LauncherSettingsActionRoute

    data object RequestCalendarAccess : LauncherSettingsActionRoute

    data object ChangeWallpaper : LauncherSettingsActionRoute

    data object ExportBackup : LauncherSettingsActionRoute

    data object RequestImportBackup : LauncherSettingsActionRoute

    data object UndoImportBackup : LauncherSettingsActionRoute

    data object DismissImportUndo : LauncherSettingsActionRoute
}

@Suppress("LongMethod") // One branch list: every settings action and where it goes.
internal fun LauncherShellAction.launcherSettingsActionRoute(): LauncherSettingsActionRoute? =
    when (this) {
        is LauncherShellAction.SelectWallpaperSource,
        is LauncherShellAction.SelectLauncherThemeMode,
        is LauncherShellAction.SelectLauncherThemeAccent,
        is LauncherShellAction.SelectCustomAccent,
        is LauncherShellAction.SelectLauncherThemeCornerStyle,
        is LauncherShellAction.SelectLauncherThemeTypography,
        is LauncherShellAction.SelectIconShape,
        is LauncherShellAction.SelectLegacyIconHandling,
        is LauncherShellAction.SetThemedIcons,
        is LauncherShellAction.SetThemeAllIcons,
        is LauncherShellAction.SelectIconPack,
        is LauncherShellAction.SetAppIconOverride,
        is LauncherShellAction.SetAppLabelOverride,
        is LauncherShellAction.RestoreIconOverrides,
        is LauncherShellAction.OpenChangeIcon,
        LauncherShellAction.ResetIconChanges,
        is LauncherShellAction.SelectWallpaperScrollMode,
        is LauncherShellAction.SelectFullscreenHomeEnabled,
        is LauncherShellAction.SelectHomeStatusBarHidden,
        is LauncherShellAction.SelectHomeNavigationBarHidden,
        is LauncherShellAction.SelectReducedTransparency,
        is LauncherShellAction.SelectLiquidGlassSpotlightFlow,
        is LauncherShellAction.SelectHomeSwipeGestureAction,
        is LauncherShellAction.SelectHomeGestureAction,
        LauncherShellAction.ResetHomeSwipeGestureActions,
        is LauncherShellAction.SelectHapticFeedbackStrength,
        is LauncherShellAction.SelectReducedMotionPreference,
        is LauncherShellAction.SelectMotionPerformanceTargetFps,
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
        is LauncherShellAction.SetSearchSettings,
        is LauncherShellAction.SelectPairsMode,
        is LauncherShellAction.SelectStreamCardTint,
        is LauncherShellAction.SetStreamCardTopGlow,
        is LauncherShellAction.SelectOpenCardPlacement,
        is LauncherShellAction.SelectCardActionsPlacement,
        is LauncherShellAction.SetDockMotion,
        LauncherShellAction.ResetSettings,
        is LauncherShellAction.RestoreSettings,
        is LauncherShellAction.SetAddNewApps,
        LauncherShellAction.AddAllAppsToHome,
        LauncherShellAction.ArrangeHomeAtoZ,
        is LauncherShellAction.SetCompactArrange,
        is LauncherShellAction.UndoHomeBulkEdit,
        LauncherShellAction.DismissEmptyHomeHint,
        is LauncherShellAction.SetDragTuning,
        is LauncherShellAction.SetPairRecipe,
        LauncherShellAction.RevealDeveloperOptions,
        is LauncherShellAction.SetDeveloperOptions,
        is LauncherShellAction.SelectPageScroll,
        is LauncherShellAction.SelectPageIndicator,
        is LauncherShellAction.SetQuickScroll,
        is LauncherShellAction.SetAppsBehindCards,
        is LauncherShellAction.SetFeatureEnabled,
        is LauncherShellAction.SetSimpleMode,
        is LauncherShellAction.SetSummonMode,
        is LauncherShellAction.SetSummonSettings,
        LauncherShellAction.ShowHintsAgain,
        is LauncherShellAction.SetScreenContentProtection,
        is LauncherShellAction.SelectReturnBehavior,
        is LauncherShellAction.SelectLibraryView,
        is LauncherShellAction.SelectAppDrawerIconGridColumns,
        is LauncherShellAction.AddRssFeed,
        is LauncherShellAction.RemoveRssFeed,
        is LauncherShellAction.SetRssFeedEnabled,
        is LauncherShellAction.SelectRssRefreshInterval,
        is LauncherShellAction.SetRssBackgroundWifiOnly,
        is LauncherShellAction.SetRssBackgroundChargingOnly,
        is LauncherShellAction.AddNotificationHideRule,
        is LauncherShellAction.RemoveNotificationHideRule,
        -> LauncherSettingsActionRoute.SettingsState(this)

        LauncherShellAction.RequestNotificationAccess -> LauncherSettingsActionRoute.RequestNotificationAccess
        LauncherShellAction.RequestOverlayDockPermission -> LauncherSettingsActionRoute.RequestOverlayDockPermission
        LauncherShellAction.RequestCalendarAccess -> LauncherSettingsActionRoute.RequestCalendarAccess
        LauncherShellAction.ChangeWallpaper -> LauncherSettingsActionRoute.ChangeWallpaper
        LauncherShellAction.ExportLauncherBackup -> LauncherSettingsActionRoute.ExportBackup
        LauncherShellAction.RequestImportLauncherBackup -> LauncherSettingsActionRoute.RequestImportBackup
        LauncherShellAction.UndoLastBackupImport -> LauncherSettingsActionRoute.UndoImportBackup
        LauncherShellAction.DismissLastBackupImportUndo -> LauncherSettingsActionRoute.DismissImportUndo
        else -> null
    }
