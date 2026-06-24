package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.LauncherShellState

@Composable
fun HomeDestination(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val haptics = rememberLauncherHaptics(state.launcherSettings.haptics.feedbackStrength)

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
                        homeSwipeGestures = state.launcherSettings.gestures.homeSwipe,
                        haptics = haptics,
                    ),
                notificationCountsByPackage = state.notificationCountsByPackage,
                appShortcutsByApp = state.appShortcutsByApp,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )
    }
}
