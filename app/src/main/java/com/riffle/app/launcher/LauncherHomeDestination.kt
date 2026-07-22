package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            Box(modifier = Modifier.fillMaxSize()) {
                StandardHomeSurface(
                    state = state,
                    appIconLoader = appIconLoader,
                    widgetRenderers = widgetRenderers,
                    haptics = haptics,
                    onAction = onAction,
                )
                TimeScapeAppStageSurface(
                    state = state,
                    windowInsets =
                        cardsPanelInsetPolicy(state).safeDrawingPanelInsets(),
                    windowLayout = timeScapeWindowLayout,
                    onAction = onAction,
                )
                StandardHomeDockOverlay(
                    state = state,
                    appIconLoader = appIconLoader,
                    haptics = haptics,
                    onAction = onAction,
                )
            }

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
private fun StandardHomeSurface(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
) {
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

@Composable
private fun StandardHomeDockOverlay(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
) {
    val visibleLayout = state.homeLayout.visibleTo(state.installedApps)
    val presentation =
        StandardHomePresentation(
            notificationGroupsByApp = state.notificationGroupsByApp,
            notificationAccessStatus = state.notificationAccessStatus,
            installedApps = state.installedApps,
            appShortcutsByApp = state.appShortcutsByApp,
            homeGestures = state.launcherSettings.gestures.homeGestures,
            reducedMotion = state.launcherSettings.motion.reducedMotion,
            motionPerformanceTargetFps = state.launcherSettings.motion.performanceTargetFps,
            homeInsetPolicy = homeInsetPolicy(state.launcherSettings.appearance),
            timeScapeAppearance = state.launcherSettings.cards.timeScapeAppearance,
        )
    val notificationShelfState =
        dockNotificationShelfState(
            showNotificationCards = visibleLayout.dock.showNotificationCards,
            groups = presentation.notificationGroupsByApp,
            notificationAccessStatus = presentation.notificationAccessStatus,
            apps = presentation.installedApps,
        )
    val isDockShelfExpanded = remember { mutableStateOf(false) }
    val hasDockShelfContent =
        dockHasExpandedContent(
            hasOverflow =
                dockHasOverflow(
                    capacity = visibleLayout.dock.capacity,
                    itemCount = visibleLayout.dock.items.size,
                ),
            notificationShelfState = notificationShelfState,
        )

    LaunchedEffect(hasDockShelfContent) {
        isDockShelfExpanded.value =
            dockShelfExpandedStateForContent(
                isExpanded = isDockShelfExpanded.value,
                hasContent = hasDockShelfContent,
            )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        StandardHomeDockArea(
            layout = visibleLayout,
            presentation = presentation,
            notificationShelfState = notificationShelfState,
            isDockShelfExpanded = isDockShelfExpanded.value,
            onDockShelfExpandedChange = { isDockShelfExpanded.value = it },
            appIconLoader = appIconLoader,
            actions =
                HomeWorkspaceActions(
                    onFolderOpen = {},
                    onDragSessionChanged = {},
                    haptics = haptics,
                    onAction = onAction,
                ),
        )
    }
}

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}
