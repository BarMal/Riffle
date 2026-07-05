package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository

internal class LauncherSettingsStateReducer(
    private val homeLayoutRepository: HomeLayoutRepository,
    private val launcherSettingsRepository: LauncherSettingsRepository,
    private val appVisibilityRepository: AppVisibilityRepository,
) {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState =
        when (val route = action.launcherSettingsActionRoute()) {
            is LauncherSettingsActionRoute.SettingsState -> reduceSettingsStateAction(state, route.action)
            else -> state
        }

    private fun reduceSettingsStateAction(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState =
        when (action) {
            is LauncherShellAction.SelectWallpaperSource,
            is LauncherShellAction.SelectFullscreenHomeEnabled,
            ->
                state.withAppearanceSettingsAction(
                    action = action,
                    launcherSettingsRepository = launcherSettingsRepository,
                )

            is LauncherShellAction.SelectHomeSwipeGestureAction ->
                state.withHomeSwipeGestureAction(
                    direction = action.direction,
                    action = action.action,
                    launcherSettingsRepository = launcherSettingsRepository,
                )

            is LauncherShellAction.SelectHomeGestureAction ->
                state.withHomeGestureAction(
                    gesture = action.gesture,
                    action = action.action,
                    launcherSettingsRepository = launcherSettingsRepository,
                )

            LauncherShellAction.ResetHomeSwipeGestureActions ->
                state.withDefaultHomeSwipes(
                    repo = launcherSettingsRepository,
                )

            is LauncherShellAction.SelectHapticFeedbackStrength ->
                state.withLauncherSettings(
                    settings =
                        state.launcherSettings.copy(
                            haptics =
                                state.launcherSettings.haptics.copy(
                                    feedbackStrength = action.strength,
                                ),
                        ),
                    launcherSettingsRepository = launcherSettingsRepository,
                )

            is LauncherShellAction.SelectReducedMotionEnabled ->
                state.withMotionSettingsAction(
                    action = action,
                    launcherSettingsRepository = launcherSettingsRepository,
                )

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
            ->
                state.withOverlayDockSettingsAction(
                    action = action,
                    launcherSettingsRepository = launcherSettingsRepository,
                )

            is LauncherShellAction.SelectSettingsLayoutDeviceClass ->
                state.withSettingsLayoutDeviceClass(action.deviceClass)

            is LauncherShellAction.ImportLauncherBackup ->
                state
                    .withImportedBackup(
                        document = action.document,
                        homeLayoutRepository = homeLayoutRepository,
                        launcherSettingsRepository = launcherSettingsRepository,
                        appVisibilityRepository = appVisibilityRepository,
                    )
                    .withoutUnavailableApps(homeLayoutRepository)
                    .withHomeScreenLibraryApps(homeLayoutRepository)

            else -> state
        }
}
