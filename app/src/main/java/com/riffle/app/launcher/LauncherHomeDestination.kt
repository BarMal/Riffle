@file:Suppress("LongParameterList")

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
import com.riffle.core.domain.launcher.cards.TimeScapeInteractionContext
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout
import kotlin.math.roundToInt

@Composable
fun HomeDestination(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    haptics: LauncherHaptics = NoopLauncherHaptics,
    timeScapeWindowLayout: TimeScapeWindowLayout? = null,
    timeScapeContext: TimeScapeInteractionContext = TimeScapeInteractionContext(),
    onTimeScapeContextChanged: (TimeScapeInteractionContext) -> Unit = {},
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
                timeScapeContext = timeScapeContext,
                onTimeScapeContextChanged = onTimeScapeContextChanged,
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
    timeScapeContext: TimeScapeInteractionContext,
    onTimeScapeContextChanged: (TimeScapeInteractionContext) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val dockInteractionHeightPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val dockInteractionHeight =
        maxOf(
            state.homeLayout.dockInteractionRegionHeightDp().dp,
            with(density) { dockInteractionHeightPx.intValue.toDp() },
        )
    // TimeScapeAppStageSurface is given `dockInteractionHeight` less real Compose height below (via
    // the `.padding(bottom = dockInteractionHeight)` modifier), so its own pane-layout math must see
    // the same reduced height -- otherwise it lays out the spine against the full window height and
    // overflows past the real, smaller Surface bounds onto the Dock beneath. Subtract the same
    // live-measured dock height from the externally-supplied window metrics here, at the source,
    // rather than papering over the mismatch with a clip inside TimeScapeAppStageSurface.
    val dockInteractionHeightDp = dockInteractionHeight.value.roundToInt()
    val timeScapeWindowLayoutBelowDock =
        timeScapeWindowLayout?.let { layout ->
            layout.copy(heightDp = (layout.heightDp - dockInteractionHeightDp).coerceAtLeast(0))
        }

    Box(modifier = Modifier.fillMaxSize()) {
        // Cards mode reuses the standard Dock but must not show the standard grid pages (and any
        // icons placed on them) underneath TimeScape's own canvas -- see StandardHomeDockOnlySurface.
        StandardHomeDockOnlySurface(
            layout = state.homeLayout,
            installedApps = state.installedApps,
            interactions =
                StandardHomeInteractions(
                    haptics = haptics,
                    onDockInteractionHeightChanged = { heightPx ->
                        dockInteractionHeightPx.intValue = heightPx
                    },
                ),
            presentation =
                StandardHomePresentation(
                    notificationGroupsByApp = state.notificationGroupsByApp,
                    notificationAccessStatus = state.notificationAccessStatus,
                    installedApps = state.installedApps,
                    appShortcutsByApp = state.appShortcutsByApp,
                    homeGestures = state.launcherSettings.gestures.homeGestures,
                    dockGestures = state.launcherSettings.gestures.dockGestures,
                    reducedMotion = state.launcherSettings.motion.reducedMotion,
                    motionPerformanceTargetFps = state.launcherSettings.motion.performanceTargetFps,
                    widgetViewFactory = widgetRenderers.viewFactory,
                    homeInsetPolicy = homeInsetPolicy(state.launcherSettings.appearance),
                    timeScapeAppearance = state.launcherSettings.cards.timeScapeAppearance,
                ),
            appIconLoader = appIconLoader,
            onAction = onAction,
        )
        TimeScapeAppStageSurface(
            state = state,
            modifier = Modifier.padding(bottom = dockInteractionHeight),
            windowInsets = cardsPanelInsetPolicy(state).safeDrawingPanelInsets(),
            windowLayout = timeScapeWindowLayoutBelowDock,
            context = timeScapeContext,
            onContextChanged = onTimeScapeContextChanged,
            onAction = onAction,
            appIconLoader = appIconLoader,
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
                dockGestures = state.launcherSettings.gestures.dockGestures,
                reducedMotion = state.launcherSettings.motion.reducedMotion,
                motionPerformanceTargetFps = state.launcherSettings.motion.performanceTargetFps,
                widgetViewFactory = widgetRenderers.viewFactory,
                widgetPicker =
                    StandardHomeWidgetPickerState(
                        providers = state.installedWidgetProviders,
                        profileContentVisibility = state.profileContentVisibility,
                        catalogStatus = state.widgetProviderCatalogStatus,
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
