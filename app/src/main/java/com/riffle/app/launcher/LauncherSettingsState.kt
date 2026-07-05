package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository

fun LauncherShellState.withLauncherSettings(
    settings: LauncherSettings,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState =
    copy(launcherSettings = settings)
        .also { state -> launcherSettingsRepository.saveLauncherSettings(state.launcherSettings) }

fun LauncherShellState.withHomeSwipeGestureAction(
    direction: HomeSwipeGestureDirection,
    action: LauncherGestureAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState =
    withHomeGestureAction(
        gesture = direction.homeGesture,
        action = action,
        launcherSettingsRepository = launcherSettingsRepository,
    )

fun LauncherShellState.withHomeGestureAction(
    gesture: HomeGesture,
    action: LauncherGestureAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState =
    withLauncherSettings(
        settings =
            launcherSettings.copy(
                gestures =
                    launcherSettings.gestures.copy(
                        homeGestures = launcherSettings.gestures.homeGestures.withAction(gesture, action),
                    ),
            ),
        launcherSettingsRepository = launcherSettingsRepository,
    )

fun LauncherShellState.withDefaultHomeSwipes(repo: LauncherSettingsRepository): LauncherShellState =
    withLauncherSettings(
        settings =
            launcherSettings.copy(
                gestures =
                    launcherSettings.gestures.copy(
                        homeGestures = defaultHomeGestureSettings,
                    ),
            ),
        launcherSettingsRepository = repo,
    )

internal fun LauncherShellState.withAppearanceSettingsAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState =
    when (action) {
        is LauncherShellAction.SelectWallpaperSource ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        appearance =
                            launcherSettings.appearance.copy(
                                wallpaper = com.riffle.core.domain.launcher.home.WallpaperSettings(action.source),
                            ),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        is LauncherShellAction.SelectFullscreenHomeEnabled ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        appearance =
                            launcherSettings.appearance.copy(
                                fullscreenHome = action.enabled,
                            ),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        else -> this
    }

internal fun LauncherShellState.withOverlayDockSettingsAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState =
    withLauncherSettings(
        settings =
            launcherSettings.copy(
                overlayDock =
                    launcherSettings.overlayDock.withOverlayDockSettingsAction(action),
            ),
        launcherSettingsRepository = launcherSettingsRepository,
    )

private val defaultHomeGestureSettings = HomeGestureSettings()

private val HomeSwipeGestureDirection.homeGesture: HomeGesture
    get() =
        when (this) {
            HomeSwipeGestureDirection.UP -> HomeGesture.ONE_FINGER_UP
            HomeSwipeGestureDirection.DOWN -> HomeGesture.ONE_FINGER_DOWN
            HomeSwipeGestureDirection.LEFT -> HomeGesture.ONE_FINGER_LEFT
            HomeSwipeGestureDirection.RIGHT -> HomeGesture.ONE_FINGER_RIGHT
        }
