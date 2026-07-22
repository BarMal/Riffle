package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout

@Composable
fun HomeDestination(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    haptics: LauncherHaptics = NoopLauncherHaptics,
    timeScapeWindowLayout: TimeScapeWindowLayout? = null,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (state.homeLayout.viewMode.homeSurfaceKind()) {
        HomeSurfaceKind.CARDS ->
            CardsHomeSurface(
                state = state,
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                haptics = haptics,
                timeScapeWindowLayout = timeScapeWindowLayout,
                onAction = onAction,
            )

        HomeSurfaceKind.GRID ->
            StandardHomeSurface(
                state = state,
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                haptics = haptics,
                onAction = onAction,
            )
    }
}

@Composable
private fun CardsHomeSurface(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    timeScapeWindowLayout: TimeScapeWindowLayout?,
    onAction: (LauncherShellAction) -> Unit,
) {
    val dockInteractionHeightPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val dockInteractionHeight =
        maxOf(
            state.homeLayout.dockInteractionRegionHeightDp().dp,
            with(density) { dockInteractionHeightPx.intValue.toDp() },
        )

    Box(modifier = Modifier.fillMaxSize()) {
        StandardHomeSurface(
            state = state,
            appIconLoader = appIconLoader,
            widgetRenderers = widgetRenderers,
            haptics = haptics,
            onDockInteractionHeightChanged = { heightPx ->
                dockInteractionHeightPx.intValue = heightPx
            },
            onAction = onAction,
        )
        TimeScapeAppStageSurface(
            state = state,
            modifier =
                Modifier.padding(bottom = dockInteractionHeight),
            windowInsets =
                cardsPanelInsetPolicy(state).safeDrawingPanelInsets(),
            windowLayout = timeScapeWindowLayout,
            onAction = onAction,
        )
    }
}

@Composable
private fun StandardHomeSurface(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    onDockInteractionHeightChanged: (Int) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
) {
    StandardHome(
        layout = state.homeLayout,
        installedApps = state.installedApps,
        interactions =
            StandardHomeInteractions(
                haptics = haptics,
                onDockInteractionHeightChanged = onDockInteractionHeightChanged,
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

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}
