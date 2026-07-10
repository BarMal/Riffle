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
            NotificationOverviewSurface(
                title = "Cards",
                groups = state.notificationGroupsByApp,
                categoryCounts = state.notificationCountsByCategory,
                notificationAccessStatus = state.notificationAccessStatus,
                apps = state.installedApps,
                appIconLoader = appIconLoader,
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
                        widgetViewFactory = widgetRenderers.viewFactory,
                        widgetPicker =
                            StandardHomeWidgetPickerState(
                                providers = state.installedWidgetProviders,
                                isOpen = state.isWidgetPickerOpen,
                            ),
                        homeInsetPolicy = homeInsetPolicy(state.launcherSettings.appearance),
                    ),
                appIconLoader = appIconLoader,
                widgetPreviewImageLoader = widgetRenderers.previewImageLoader,
                onAction = onAction,
            )
    }
}
