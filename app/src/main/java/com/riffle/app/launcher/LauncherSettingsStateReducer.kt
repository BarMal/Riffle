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
        if (action.isAppearanceSettingsAction) {
            state.withAppearanceSettingsAction(
                action = action,
                launcherSettingsRepository = launcherSettingsRepository,
            )
        } else {
            when (action) {
                is LauncherShellAction.SelectHomeSwipeGestureAction ->
                    state.withHomeSwipeGestureAction(action.direction, action.action, launcherSettingsRepository)

                is LauncherShellAction.SelectHomeGestureAction ->
                    state.withHomeGestureAction(
                        action.gesture,
                        action.action,
                        action.launchTarget,
                        launcherSettingsRepository,
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

                is LauncherShellAction.SelectReducedMotionEnabled,
                is LauncherShellAction.SelectMotionPerformanceTargetFps,
                ->
                    state.withMotionSettingsAction(
                        action = action,
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                is LauncherShellAction.SelectContextualEnabled ->
                    state.withContextualSettingsAction(
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
}

private val LauncherShellAction.isAppearanceSettingsAction: Boolean
    get() =
        when (this) {
            is LauncherShellAction.SelectWallpaperSource,
            is LauncherShellAction.SelectLauncherThemeMode,
            is LauncherShellAction.SelectLauncherThemePreset,
            is LauncherShellAction.SelectCustomThemeAccent,
            is LauncherShellAction.SelectCustomThemeCardCornerRadius,
            is LauncherShellAction.SelectWallpaperScrollMode,
            is LauncherShellAction.SelectFullscreenHomeEnabled,
            is LauncherShellAction.SelectHomeStatusBarHidden,
            is LauncherShellAction.SelectHomeNavigationBarHidden,
            -> true

            else -> false
        }
