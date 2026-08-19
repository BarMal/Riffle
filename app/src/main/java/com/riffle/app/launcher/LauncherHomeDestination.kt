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
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout

@Composable
fun HomeDestination(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    haptics: LauncherHaptics = NoopLauncherHaptics,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout? = null,
    adaptiveStageContext: AdaptiveStageInteractionContext = AdaptiveStageInteractionContext(),
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
) {
    when (state.homeLayout.viewMode.homeSurfaceKind()) {
        HomeSurfaceKind.CARDS ->
            CardsHomeSurface(
                state = state,
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                haptics = haptics,
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                adaptiveStageContext = adaptiveStageContext,
                onAdaptiveStageContextChanged = onAdaptiveStageContextChanged,
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
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout?,
    adaptiveStageContext: AdaptiveStageInteractionContext,
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val dockInteractionHeightPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Reconciled once for the whole surface. The dock's dynamic side is the stage list now, so it
    // and the stage itself have to be looking at the same stages -- and the reconciler carries the
    // previous snapshot, so a second one would quietly keep a history of its own.
    val shellState = rememberAppStageShellState(state)
    val stageEntries =
        shellState.snapshot.stages.stageDockDynamicEntries(
            state = state,
            selectedStageId =
                shellState.snapshot.selectedStage?.id.takeUnless { adaptiveStageContext.allNotificationsSelected },
            badgeCounts =
                shellState.notificationCards.groupingBy { card -> card.content.stageId }.eachCount(),
            allNotificationsSelected = adaptiveStageContext.allNotificationsSelected,
        )
    val dockInteractionHeight =
        maxOf(
            state.homeLayout.dockInteractionRegionHeightDp().dp,
            with(density) { dockInteractionHeightPx.intValue.toDp() },
        )

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
                    adaptiveStageAppearance = state.launcherSettings.cards.adaptiveStageAppearance,
                ),
            appIconLoader = appIconLoader,
            onAction = onAction,
            // The stages, rather than the apps with notifications the dock is not already showing.
            // In Cards mode this side is how the user moves between stages, so it has to hold all
            // of them -- including those of apps pinned to the static side, which mean something
            // different there (that one opens the app; this one brings its cards forward).
            dynamicEntries = stageEntries,
            // The merged page is not a stage, so there is no action to send: it is a choice about
            // what this surface is showing, which is what the interaction context holds.
            onShowAllNotifications = {
                onAdaptiveStageContextChanged(adaptiveStageContext.copy(allNotificationsSelected = true))
            },
        )
        AdaptiveStageAppStageSurface(
            state = state,
            shellState = shellState,
            modifier = Modifier.padding(bottom = dockInteractionHeight),
            windowInsets = cardsPanelInsetPolicy(state).safeDrawingPanelInsets(),
            windowLayout = adaptiveStageWindowLayout,
            context = adaptiveStageContext,
            onContextChanged = onAdaptiveStageContextChanged,
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
    onBottomControlsHeightChanged: (Int) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
) {
    StandardHome(
        layout = state.homeLayout,
        installedApps = state.installedApps,
        interactions =
            StandardHomeInteractions(
                haptics = haptics,
                onDockInteractionHeightChanged = onDockInteractionHeightChanged,
                onBottomControlsHeightChanged = onBottomControlsHeightChanged,
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
                        isTargetingDockPanel = state.isWidgetPickerTargetingDockPanel,
                    ),
                homeInsetPolicy = homeInsetPolicy(state.launcherSettings.appearance),
                adaptiveStageAppearance = state.launcherSettings.cards.adaptiveStageAppearance,
            ),
        appIconLoader = appIconLoader,
        widgetPreviewImageLoader = widgetRenderers.previewImageLoader,
        onAction = onAction,
    )
}

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}
