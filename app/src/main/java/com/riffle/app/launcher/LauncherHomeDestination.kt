@file:Suppress("LongParameterList")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.cards.CardsStageSelector
import com.riffle.core.domain.launcher.home.DockPosition

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

/**
 * Keeps the stage clear of the standard Dock [StandardHomeDockOnlySurface] draws over it, on
 * whichever physical edge the dock sits on -- an absolute edge, so this uses [absolutePadding]
 * rather than [Modifier.padding]'s start/end, which would mirror in RTL.
 */
private fun Modifier.dockInteractionPadding(
    position: DockPosition,
    extent: Dp,
): Modifier =
    when (position) {
        DockPosition.TOP -> absolutePadding(top = extent)
        DockPosition.BOTTOM -> absolutePadding(bottom = extent)
        DockPosition.LEFT -> absolutePadding(left = extent)
        DockPosition.RIGHT -> absolutePadding(right = extent)
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
    val dockInteractionExtentPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // state.homeLayout.dock is the device class's one shared dock (#1205) -- the same model the grid
    // modes draw. The dock stays mode-agnostic (Decision 2): everything Cards-specific about it --
    // the dynamic section being the stage selector, "Show stage" on a pinned icon -- is built and
    // interpreted in CardsDockInterpreter.kt, and the dock only sees neutral entries and intents.
    // Single source of truth for where the dock sits, mirroring StandardHome's own resolution --
    // the dock and the stage must agree on the edge just as they agree on what exists below.
    val dockPosition =
        resolveDockPosition(state.homeLayout.dock.position, state.settingsLayoutDeviceClass.templateDockPosition)
    // Reconciled once for the whole surface, so the dock and the stage agree on what exists -- the
    // reconciler carries the previous snapshot, so a second one would quietly keep its own history.
    val shellState = rememberAppStageShellState(state)
    val stages = shellState.snapshot.stages
    val selection = cardsStageSelection(adaptiveStageContext, shellState.snapshot.selectedStage?.id)
    val selectorEntries = remember(stages, selection) { CardsStageSelector.entries(stages, selection) }
    val dockDynamicEntries = cardsStageSelectorDockEntries(selectorEntries, state)
    val dockItemMenuExtras =
        remember(state.homeLayout.dock, stages) { cardsDockItemMenuExtras(state.homeLayout.dock, stages) }
    // Selecting a stage from anywhere -- the selector, "Show stage", a spine chip -- leaves "All".
    val cardsOnAction: (LauncherShellAction) -> Unit = { action ->
        interpretCardsAction(action, adaptiveStageContext, onAdaptiveStageContextChanged, onAction)
    }
    // When the dock is off or hidden it cannot host the selector, so the surface falls back to the
    // spine rather than stranding a stage.
    val dockHostsStageSelector = state.homeLayout.visibleTo(state.installedApps).shouldShowDock()
    val dockInteractionExtent =
        maxOf(
            state.homeLayout.dockInteractionRegionExtentDp(dockPosition).dp,
            with(density) { dockInteractionExtentPx.intValue.toDp() },
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
                    onDockInteractionExtentChanged = { extentPx ->
                        dockInteractionExtentPx.intValue = extentPx
                    },
                ),
            position = dockPosition,
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
            onAction = cardsOnAction,
            // In Cards the dynamic section is the stage selector: "All" first, then every stage.
            dynamicEntries = dockDynamicEntries,
            onDynamicEntryDelegated = { key ->
                interpretCardsDockEntry(
                    key = key,
                    stages = stages,
                    context = adaptiveStageContext,
                    onContextChanged = onAdaptiveStageContextChanged,
                    onAction = onAction,
                )
            },
            // A pinned icon opens its app, as in every mode; its stage is one long-press away.
            staticItemMenuExtras = dockItemMenuExtras,
            // The stages already are the notifications, so the expanded shelf is a panel-only
            // mini-home surface here -- the card row would just show them a second time.
            showExpandedNotificationShelf = false,
        )
        AdaptiveStageAppStageSurface(
            state = state,
            shellState = shellState,
            modifier = Modifier.dockInteractionPadding(dockPosition, dockInteractionExtent),
            windowInsets = cardsPanelInsetPolicy(state).safeDrawingPanelInsets(),
            windowLayout = adaptiveStageWindowLayout,
            context = adaptiveStageContext,
            onContextChanged = onAdaptiveStageContextChanged,
            onAction = cardsOnAction,
            appIconLoader = appIconLoader,
            dockHostsStageSelector = dockHostsStageSelector,
        )
    }
}

@Composable
private fun StandardHomeSurface(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    onDockInteractionExtentChanged: (Int) -> Unit = {},
    onBottomControlsHeightChanged: (Int) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
) {
    StandardHome(
        layout = state.homeLayout,
        installedApps = state.installedApps,
        interactions =
            StandardHomeInteractions(
                haptics = haptics,
                onDockInteractionExtentChanged = onDockInteractionExtentChanged,
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
        deviceClass = state.settingsLayoutDeviceClass,
        onAction = onAction,
    )
}

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}
