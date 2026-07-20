package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.LauncherShellState

@Composable
fun HomeDestination(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    haptics: LauncherHaptics = NoopLauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (state.homeLayout.viewMode.homeSurfaceKind()) {
        HomeSurfaceKind.CARDS ->
            TimeScapeAppStageSurface(
                state = state,
                windowInsets =
                    cardsPanelInsetPolicy(state).safeDrawingPanelInsets(),
                onAction = onAction,
            )

        HomeSurfaceKind.GRID ->
            StandardHome(
                layout = state.homeLayout,
                installedApps = state.installedApps,
                interactions =
                    StandardHomeInteractions(
                        haptics = haptics,
                    ),
                presentation =
                    StandardHomePresentation(
                        notificationGroupsByApp = state.notificationGroupsByApp,
                        notificationAccessStatus = state.notificationAccessStatus,
                        installedApps = state.installedApps,
                        appShortcutsByApp = state.appShortcutsByApp,
                        homeGestures = state.launcherSettings.gestures.homeGestures,
                        reducedMotion = state.launcherSettings.motion.reducedMotion,
                        motionPerformanceTargetFps = state.launcherSettings.motion.performanceTargetFps,
                        widgetViewFactory = widgetRenderers.viewFactory,
                        widgetPicker =
                            StandardHomeWidgetPickerState(
                                providers = state.installedWidgetProviders,
                                isOpen = state.isWidgetPickerOpen,
                            ),
                        homeInsetPolicy = homeInsetPolicy(state.launcherSettings.appearance),
                        timeScapeAppearance = state.launcherSettings.cards.timeScapeAppearance,
                    ),
                appIconLoader = appIconLoader,
                widgetPreviewImageLoader = widgetRenderers.previewImageLoader,
                onAction = onAction,
            )
    }
}

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}
