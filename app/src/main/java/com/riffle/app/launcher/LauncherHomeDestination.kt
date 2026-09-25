@file:Suppress("LongParameterList")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.riffle.app.launcher.notifications.AppStageShellState
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
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
    val dockHost = rememberHomeDockHostState()
    val presentation = standardHomePresentation(state, widgetRenderers)
    // Single source of truth for where the one dock sits (#1205): the user's configured edge if any,
    // else the device class's template default. Every mode's content and the dock agree on it.
    val dockPosition =
        resolveDockPosition(state.homeLayout.dock.position, state.settingsLayoutDeviceClass.templateDockPosition)
    // Cards' stages feed both its surface and its reading of the dock, so they are reconciled here,
    // once -- the reconciler carries the previous snapshot, so a second one would keep its own history.
    val cardsShellState =
        if (state.homeLayout.viewMode.homeSurfaceKind() == HomeSurfaceKind.CARDS) {
            rememberAppStageShellState(state)
        } else {
            null
        }
    // The one interpreter contract (Decision 2): grid modes take the default reading, Cards supplies
    // its own from CardsDockInterpreter.kt. The dock itself only ever sees neutral entries and intents.
    val dockInterpreter =
        if (cardsShellState != null) {
            rememberCardsDockInterpreter(
                state = state,
                shellState = cardsShellState,
                adaptiveStageContext = adaptiveStageContext,
                onAdaptiveStageContextChanged = onAdaptiveStageContextChanged,
                onAction = onAction,
            )
        } else {
            HomeDockInterpreter()
        }

    Box(modifier = Modifier.fillMaxSize()) {
        // The one dock, outside the mode surface: composed at the same place whichever surface runs
        // below, so a mode switch keeps this instance and its position (#1205, Decision 2). What
        // differs by mode reaches it only through [dockInterpreter]. Composed first so the mode's
        // overlays cover it; the grid's own frame sits beneath it (see HOME_CONTENT_Z_INDEX).
        HomeDockHost(
            layout = state.homeLayout,
            installedApps = state.installedApps,
            presentation = presentation,
            position = dockPosition,
            hostState = dockHost,
            appIconLoader = appIconLoader,
            onAction = onAction,
            interpreter = dockInterpreter,
            haptics = haptics,
        )
        if (cardsShellState != null) {
            CardsHomeSurface(
                state = state,
                shellState = cardsShellState,
                dockHost = dockHost,
                dockPosition = dockPosition,
                appIconLoader = appIconLoader,
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                adaptiveStageContext = adaptiveStageContext,
                onAdaptiveStageContextChanged = onAdaptiveStageContextChanged,
                onAction = onAction,
            )
        } else {
            StandardHomeSurface(
                state = state,
                presentation = presentation,
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                haptics = haptics,
                dockHost = dockHost,
                onAction = onAction,
            )
        }
    }
}

/**
 * Keeps the stage clear of the shared dock [HomeDockHost] draws over it, on
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
    shellState: AppStageShellState,
    dockHost: HomeDockHostState,
    dockPosition: DockPosition,
    appIconLoader: AppIconLoader,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout?,
    adaptiveStageContext: AdaptiveStageInteractionContext,
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    // The shared dock is drawn by HomeDockHost over this surface; the stage lays out inside the
    // room it reserves, on whichever edge it sits. Cards' reading of that dock (the stage selector,
    // "Show stage") is [rememberCardsDockInterpreter]'s, built beside this surface by HomeDestination.
    // Selecting a stage from anywhere -- the selector, "Show stage", a spine chip -- leaves "All".
    val cardsOnAction: (LauncherShellAction) -> Unit = { action ->
        interpretCardsAction(action, adaptiveStageContext, onAdaptiveStageContextChanged, onAction)
    }
    val visibleLayout = state.homeLayout.visibleTo(state.installedApps)
    // When the dock is off or hidden it cannot host the selector, so the surface falls back to the
    // spine rather than stranding a stage.
    val dockHostsStageSelector = visibleLayout.shouldShowDock()
    val dockInteractionExtent = dockHost.reservedExtent(visibleLayout, dockPosition)

    Box(modifier = Modifier.fillMaxSize()) {
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
    presentation: StandardHomePresentation,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    dockHost: HomeDockHostState,
    onAction: (LauncherShellAction) -> Unit,
) {
    StandardHome(
        layout = state.homeLayout,
        installedApps = state.installedApps,
        interactions = StandardHomeInteractions(haptics = haptics),
        presentation = presentation,
        appIconLoader = appIconLoader,
        widgetPreviewImageLoader = widgetRenderers.previewImageLoader,
        deviceClass = state.settingsLayoutDeviceClass,
        dockHost = dockHost,
        onAction = onAction,
    )
}

/** What the home grid and the shared dock both render from, whichever mode is showing. */
private fun standardHomePresentation(
    state: LauncherShellState,
    widgetRenderers: LauncherWidgetRenderers,
): StandardHomePresentation =
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
    )

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}
