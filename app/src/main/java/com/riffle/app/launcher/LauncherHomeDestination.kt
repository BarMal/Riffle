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
                        notificationCountsByPackage = state.notificationCountsByPackage,
                        appShortcutsByApp = state.appShortcutsByApp,
                        homeSwipeGestures = state.launcherSettings.gestures.homeSwipe,
                        widgetViewFactory = widgetRenderers.viewFactory,
                        widgetPicker =
                            StandardHomeWidgetPickerState(
                                providers = state.installedWidgetProviders,
                                isOpen = state.isWidgetPickerOpen,
                            ),
                    ),
                appIconLoader = appIconLoader,
                widgetPreviewImageLoader = widgetRenderers.previewImageLoader,
                onAction = onAction,
            )
    }
}
