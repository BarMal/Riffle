package com.riffle.app.launcher

import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.app.launcher.rss.NoopFeedArticleCacheRepository
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.settings.HintSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.StreamSettings
import com.riffle.core.domain.launcher.settings.coerced
import com.riffle.core.domain.launcher.settings.withReducedTransparency

internal class LauncherSettingsStateReducer(
    private val homeLayoutRepository: HomeLayoutRepository,
    private val launcherSettingsRepository: LauncherSettingsRepository,
    private val appVisibilityRepository: AppVisibilityRepository,
    private val feedArticleCacheRepository: FeedArticleCacheRepository = NoopFeedArticleCacheRepository,
) {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState =
        when (val route = action.launcherSettingsActionRoute()) {
            is LauncherSettingsActionRoute.SettingsState -> reduceSettingsStateAction(state, route.action)
            else -> state
        }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
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
            state.withAppDrawerSettingsAction(action, launcherSettingsRepository) ?: when (action) {
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

                is LauncherShellAction.SelectReducedMotionPreference,
                is LauncherShellAction.SelectMotionPerformanceTargetFps,
                ->
                    state.withMotionSettingsAction(
                        action = action,
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                is LauncherShellAction.SelectReducedTransparency ->
                    state.withLauncherSettings(
                        settings = state.launcherSettings.withReducedTransparency(action.reduced),
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                is LauncherShellAction.SelectLiquidGlassSpotlightFlow ->
                    state.withLauncherSettings(
                        settings =
                            state.launcherSettings.copy(
                                liquidGlass =
                                    state.launcherSettings.liquidGlass
                                        .copy(spotlightFlowMillis = action.millis)
                                        .coerced(),
                            ),
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                is LauncherShellAction.SelectReturnBehavior ->
                    state.withLauncherSettings(
                        settings =
                            state.launcherSettings.copy(
                                home = state.launcherSettings.home.copy(returnBehavior = action.behavior),
                            ),
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                is LauncherShellAction.SetScreenContentProtection ->
                    state.withLauncherSettings(
                        settings =
                            state.launcherSettings.copy(
                                privacy = state.launcherSettings.privacy.copy(protectScreenContent = action.enabled),
                            ),
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                is LauncherShellAction.SelectSearchResultPresentation ->
                    state.withLauncherSettings(
                        settings =
                            state.launcherSettings.copy(
                                search = state.launcherSettings.search.copy(resultPresentation = action.presentation),
                            ),
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                is LauncherShellAction.SelectStreamCardTint ->
                    state.withStreamSettings(launcherSettingsRepository) { it.copy(cardTintPercent = action.percent) }

                is LauncherShellAction.SetStreamCardTopGlow ->
                    state.withStreamSettings(launcherSettingsRepository) { it.copy(cardTopGlow = action.enabled) }

                is LauncherShellAction.SelectOpenCardPlacement ->
                    state.withStreamSettings(launcherSettingsRepository) {
                        it.copy(openCardPlacement = action.placement)
                    }

                is LauncherShellAction.SelectCardActionsPlacement ->
                    state.withStreamSettings(launcherSettingsRepository) {
                        it.copy(cardActionsPlacement = action.placement)
                    }

                is LauncherShellAction.SetDockMotion ->
                    state.withLauncherSettings(
                        settings =
                            state.launcherSettings.copy(
                                motion = state.launcherSettings.motion.copy(dock = action.settings.coerced()),
                            ),
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                is LauncherShellAction.DismissDockPressHint ->
                    state.withLauncherSettings(
                        settings = state.launcherSettings.copy(hints = HintSettings(dockPressSeen = true)),
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

                is LauncherShellAction.AddRssFeed,
                is LauncherShellAction.RemoveRssFeed,
                is LauncherShellAction.SetRssFeedEnabled,
                is LauncherShellAction.SelectRssRefreshInterval,
                is LauncherShellAction.SetRssBackgroundWifiOnly,
                is LauncherShellAction.SetRssBackgroundChargingOnly,
                ->
                    state.withRssSettingsAction(
                        action = action,
                        launcherSettingsRepository = launcherSettingsRepository,
                        feedArticleCacheRepository = feedArticleCacheRepository,
                    )

                is LauncherShellAction.AddNotificationHideRule,
                is LauncherShellAction.RemoveNotificationHideRule,
                ->
                    state.withNotificationHidingAction(
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

private fun LauncherShellState.withAppDrawerSettingsAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState? =
    when (action) {
        is LauncherShellAction.SelectLibraryView ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        appDrawer =
                            launcherSettings.appDrawer.withLibraryView(
                                deviceClass = action.deviceClass ?: homeLayoutSet.activeKey.deviceClass,
                                view = action.view,
                            ),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        is LauncherShellAction.SelectAppDrawerIconGridColumns ->
            withLauncherSettings(
                settings =
                    launcherSettings.copy(
                        appDrawer = launcherSettings.appDrawer.copy(iconGridColumns = action.columns).coerced(),
                    ),
                launcherSettingsRepository = launcherSettingsRepository,
            )

        else -> null
    }

private val LauncherShellAction.isAppearanceSettingsAction: Boolean
    get() =
        when (this) {
            is LauncherShellAction.SelectWallpaperSource,
            is LauncherShellAction.SelectLauncherThemeMode,
            is LauncherShellAction.SelectLauncherThemeAccent,
            is LauncherShellAction.SelectCustomAccent,
            is LauncherShellAction.SelectLauncherThemeCornerStyle,
            is LauncherShellAction.SelectLauncherThemeTypography,
            is LauncherShellAction.SelectWallpaperScrollMode,
            is LauncherShellAction.SelectFullscreenHomeEnabled,
            is LauncherShellAction.SelectHomeStatusBarHidden,
            is LauncherShellAction.SelectHomeNavigationBarHidden,
            -> true

            else -> false
        }

/** The state with its Stream settings changed by [change] (held to their ranges) and saved. */
private fun LauncherShellState.withStreamSettings(
    repository: LauncherSettingsRepository,
    change: (StreamSettings) -> StreamSettings,
): LauncherShellState =
    withLauncherSettings(
        settings = launcherSettings.copy(stream = change(launcherSettings.stream).coerced()),
        launcherSettingsRepository = repository,
    )
