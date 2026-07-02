package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.coerceOverlayDockVerticalOffset

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
                    when (action) {
                        is LauncherShellAction.SelectOverlayDockEnabled ->
                            launcherSettings.overlayDock.copy(enabled = action.enabled)

                        is LauncherShellAction.SelectOverlayDockEdge ->
                            launcherSettings.overlayDock.copy(edge = action.edge)

                        is LauncherShellAction.SelectOverlayDockHandleThickness ->
                            launcherSettings.overlayDock.copy(
                                handleThicknessDp =
                                    action.thicknessDp.coerceIn(
                                        MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
                                        MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
                                    ),
                            )

                        is LauncherShellAction.SelectOverlayDockHandleHeight ->
                            launcherSettings.overlayDock.copy(
                                handleHeightDp =
                                    action.heightDp.coerceIn(
                                        MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
                                        MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
                                    ),
                            )

                        is LauncherShellAction.SelectOverlayDockVerticalOffset ->
                            launcherSettings.overlayDock.copy(
                                verticalOffsetDp = action.offsetDp.coerceOverlayDockVerticalOffset(),
                            )

                        is LauncherShellAction.SelectOverlayDockHandleAlpha ->
                            launcherSettings.overlayDock.copy(
                                handleAlphaPercent =
                                    action.alphaPercent.coerceIn(
                                        MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
                                        MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
                                    ),
                            )

                        is LauncherShellAction.SelectOverlayDockExpandedIconSize ->
                            launcherSettings.overlayDock.copy(
                                expandedIconSizeDp =
                                    action.sizeDp.coerceIn(
                                        MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
                                        MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
                                    ),
                            )

                        is LauncherShellAction.SelectOverlayDockShowLabels ->
                            launcherSettings.overlayDock.copy(showLabels = action.showLabels)

                        else -> launcherSettings.overlayDock
                    },
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
