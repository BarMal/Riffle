package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.withFullscreenHome
import com.riffle.core.domain.launcher.settings.withHomeNavigationBarHidden
import com.riffle.core.domain.launcher.settings.withHomeStatusBarHidden

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

internal fun LauncherShellState.withMotionSettingsAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState =
    when (action) {
        is LauncherShellAction.SelectReducedMotionEnabled ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        motion =
                            launcherSettings.motion.copy(
                                reducedMotion = action.enabled,
                            ),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        else -> this
    }

internal fun LauncherShellState.withContextualSettingsAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState =
    when (action) {
        is LauncherShellAction.SelectContextualEnabled ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        contextual =
                            launcherSettings.contextual.copy(
                                enabled = action.enabled,
                            ),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        else -> this
    }

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
                                wallpaper = launcherSettings.appearance.wallpaper.copy(source = action.source),
                            ),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        is LauncherShellAction.SelectWallpaperScrollMode ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        appearance =
                            launcherSettings.appearance.copy(
                                wallpaper = launcherSettings.appearance.wallpaper.copy(scrollMode = action.mode),
                            ),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        is LauncherShellAction.SelectFullscreenHomeEnabled ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        appearance =
                            launcherSettings.appearance.withFullscreenHome(action.enabled),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        is LauncherShellAction.SelectHomeStatusBarHidden ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        appearance =
                            launcherSettings.appearance.withHomeStatusBarHidden(action.hidden),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        is LauncherShellAction.SelectHomeNavigationBarHidden ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        appearance =
                            launcherSettings.appearance.withHomeNavigationBarHidden(action.hidden),
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
