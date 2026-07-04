package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.LauncherShellState

@Composable
fun HomeDestination(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
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
                        widgetViewFactory = widgetViewFactory,
                        widgetPicker =
                            StandardHomeWidgetPickerState(
                                providers = state.installedWidgetProviders,
                                isOpen = state.isWidgetPickerOpen,
                            ),
                    ),
                appIconLoader = appIconLoader,
                onAction = onAction,
            )
    }
}
