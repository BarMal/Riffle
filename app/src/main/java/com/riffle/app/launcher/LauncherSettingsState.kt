package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
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
    withLauncherSettings(
        settings =
            launcherSettings.copy(
                gestures =
                    launcherSettings.gestures.copy(
                        homeSwipe = launcherSettings.gestures.homeSwipe.withAction(direction, action),
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
                        homeSwipe = defaultHomeSwipeGestureSettings,
                    ),
            ),
        launcherSettingsRepository = repo,
    )

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

private val defaultHomeSwipeGestureSettings = HomeSwipeGestureSettings()

private fun HomeSwipeGestureSettings.withAction(
    direction: HomeSwipeGestureDirection,
    action: LauncherGestureAction,
): HomeSwipeGestureSettings =
    when (direction) {
        HomeSwipeGestureDirection.UP -> copy(up = action)
        HomeSwipeGestureDirection.DOWN -> copy(down = action)
        HomeSwipeGestureDirection.LEFT -> copy(left = action)
        HomeSwipeGestureDirection.RIGHT -> copy(right = action)
    }
