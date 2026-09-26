@file:Suppress("LongParameterList")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.riffle.app.launcher.notifications.AppStageShellState
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState
import com.riffle.core.domain.launcher.dockpull.dockPullCounterpartMode
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeDockEdges
import com.riffle.core.domain.launcher.home.ModeSurface
import com.riffle.core.domain.launcher.home.dockEdgeFor
import com.riffle.core.domain.launcher.home.modeSurface
import kotlinx.coroutines.delay

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
    val dockPull = rememberDockPullState()
    val presentation = standardHomePresentation(state, widgetRenderers)
    // Which surfaces are composed, each with the state it sees and the edge its dock takes: the
    // one on screen, plus the other one while the dock is pulled between them (#1206, #1207).
    val plan = rememberHomeSurfacePlan(state, dockPull)
    // Cards' stages feed both its surface and its reading of the dock, so they are reconciled here,
    // once -- the reconciler carries the previous snapshot, so a second one would keep its own
    // history. The same call serves a Cards surface pulled in from Library and then shown.
    val cardsShellState = plan.cardsState?.let { cardsState -> rememberAppStageShellState(cardsState) }
    // The one interpreter contract (Decision 2): grid modes take the default reading, Cards supplies
    // its own from CardsDockInterpreter.kt. The dock itself only ever sees neutral entries and intents.
    val dockInterpreter =
        if (cardsShellState != null && plan.shownMode.homeSurfaceKind() == HomeSurfaceKind.CARDS) {
            rememberCardsDockInterpreter(
                state = plan.stateFor(plan.shownMode),
                shellState = cardsShellState,
                adaptiveStageContext = adaptiveStageContext,
                onAdaptiveStageContextChanged = onAdaptiveStageContextChanged,
                onAction = onAction,
            )
        } else {
            HomeDockInterpreter()
        }
    val pull =
        rememberHomeDockPullBinding(
            state = state,
            plan = HomeDockPullPlan(plan.currentMode, plan.counterpartMode, plan.shownMode, plan.edges),
            dockPull = dockPull,
            onAction = onAction,
        )

    // The same state [dockEdge] and [dockInterpreter] are already drawn for: [plan.shownMode], not
    // necessarily [plan.currentMode]. Once a commit lands, [plan.shownMode] stands in for the
    // destination mode (through [dockPull]'s pending switch) a beat before the shell itself catches
    // up and [state] reflects it (#1206). Reading the dock's own content and installed-app list from
    // [state] directly -- as opposed to this -- would draw the settled-in-place dock against
    // still-outgoing data for that beat, then pop to the incoming data the instant the shell answers:
    // the position jump and item-order flicker this fixes (see AGENTS.md dock-transition-consistency
    // follow-up to #1206/#1278). [dockHostState] is [state] itself once the shell has caught up, so
    // this changes nothing outside that beat.
    val dockHostState = plan.stateFor(plan.shownMode)

    Box(modifier = Modifier.fillMaxSize().then(pull.rootModifier)) {
        // The one dock, outside the mode surface: composed at the same place whichever surface runs
        // below, so a mode switch keeps this instance (#1205, Decision 2). What differs by mode
        // reaches it only through [dockInterpreter]; the dock pull moves it between the surfaces'
        // edges. Composed first so the mode's overlays cover it; the grid's own frame sits beneath
        // it (see HOME_CONTENT_Z_INDEX). While a pull runs it is lifted over both surfaces.
        HomeDockHost(
            layout = dockHostState.homeLayout,
            installedApps = dockHostState.installedApps,
            presentation = presentation,
            position = pull.dockEdge,
            hostState = dockHost,
            appIconLoader = appIconLoader,
            onAction = onAction,
            modifier = Modifier.zIndex(if (pull.isTransitioning) DOCK_PULL_DOCK_Z_INDEX else 0f),
            interpreter = dockInterpreter,
            haptics = haptics,
            dockModifier = pull.dockModifier,
            dockBackgroundAlpha = pull.dockBackgroundAlpha,
            dockContentRevealAlpha = pull.dockContentRevealAlpha,
        )
        // The screen-wide reorient frost -- see [HomeDockPullBinding.screenFrostModifier] -- is
        // composed onto each surface's own modifier chain below, not a wrapping Box around them:
        // an intervening Box here would put every mode surface one layout level deeper than
        // [HomeDockHost], so StandardHome's `Modifier.zIndex(HOME_CONTENT_Z_INDEX)` -- which yields
        // hit-test priority to the dock by ranking below it among *its own* siblings -- would no
        // longer be comparing against the dock at all: it would rank inside the wrapping Box, which
        // would itself tie the dock's zIndex and win hit-testing on placement order, swallowing the
        // dock-pull gesture (see DockPullInteractionTest regressions). Composed per-surface instead,
        // every mode surface stays a direct sibling of [HomeDockHost] exactly as before this frost
        // was added, so that precedence still holds.
        plan.composedModes.forEach { mode ->
            // Keyed by mode, so the surface a pull brings in is the very composition shown once
            // the switch lands, and a cancelled pull leaves the outgoing one untouched.
            key(mode) {
                ModeSurfaceContent(
                    mode = mode,
                    state = plan.stateFor(mode),
                    dockEdge = plan.edges.edgeFor(mode.modeSurface),
                    surfaceModifier =
                        pull.surfaceModifier(isOutgoing = mode == plan.currentMode).then(pull.screenFrostModifier),
                    cardsShellState = cardsShellState,
                    dockHost = dockHost,
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                    widgetRenderers = widgetRenderers,
                    haptics = haptics,
                    cards =
                        CardsSurfaceInputs(
                            windowLayout = adaptiveStageWindowLayout,
                            context = adaptiveStageContext,
                            onContextChanged = onAdaptiveStageContextChanged,
                        ),
                    onAction = onAction,
                )
            }
        }
    }
}

/** What a Cards surface takes from [HomeDestination] beyond the shell state. */
private class CardsSurfaceInputs(
    val windowLayout: AdaptiveStageWindowLayout?,
    val context: AdaptiveStageInteractionContext,
    val onContextChanged: (AdaptiveStageInteractionContext) -> Unit,
)

@Composable
private fun ModeSurfaceContent(
    mode: LauncherViewMode,
    state: LauncherShellState,
    dockEdge: DockPosition,
    surfaceModifier: Modifier,
    cardsShellState: AppStageShellState?,
    dockHost: HomeDockHostState,
    presentation: StandardHomePresentation,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    cards: CardsSurfaceInputs,
    onAction: (LauncherShellAction) -> Unit,
) {
    if (mode.homeSurfaceKind() == HomeSurfaceKind.CARDS) {
        if (cardsShellState != null) {
            CardsHomeSurface(
                state = state,
                shellState = cardsShellState,
                dockHost = dockHost,
                dockPosition = dockEdge,
                appIconLoader = appIconLoader,
                adaptiveStageWindowLayout = cards.windowLayout,
                adaptiveStageContext = cards.context,
                onAdaptiveStageContextChanged = cards.onContextChanged,
                onAction = onAction,
                modifier = surfaceModifier,
            )
        }
    } else {
        StandardHome(
            layout = state.homeLayout,
            installedApps = state.installedApps,
            interactions = StandardHomeInteractions(haptics = haptics),
            presentation = presentation,
            appIconLoader = appIconLoader,
            widgetPreviewImageLoader = widgetRenderers.previewImageLoader,
            deviceClass = state.settingsLayoutDeviceClass,
            dockHost = dockHost,
            dockEdge = dockEdge,
            surfaceModifier = surfaceModifier,
            onAction = onAction,
        )
    }
}

/**
 * What [HomeDestination] composes for one shell state: [currentMode] (the shell's), and while the
 * dock is pulled or a committed switch is on its way through the shell, the [counterpartMode] too.
 */
private class HomeSurfacePlan(
    val currentMode: LauncherViewMode,
    val counterpartMode: LauncherViewMode,
    /** The mode on screen when no pull runs: the shell's, or a committed switch it has not shown yet. */
    val shownMode: LauncherViewMode,
    /** The modes composed, in drawing order: the outgoing surface first while a pull runs. */
    val composedModes: List<LauncherViewMode>,
    /** Each surface's dock edge: Home's is the shared dock's, Library's its own (ModeDockEdges.kt). */
    val edges: ModeDockEdges,
    private val state: LauncherShellState,
    private val preview: LauncherShellState?,
) {
    /** The shell state [mode]'s surface draws from. */
    fun stateFor(mode: LauncherViewMode): LauncherShellState = if (mode == currentMode) state else preview ?: state

    /** The state a composed Cards surface draws from, if one is composed. */
    val cardsState: LauncherShellState?
        get() =
            composedModes
                .firstOrNull { mode -> mode.homeSurfaceKind() == HomeSurfaceKind.CARDS }
                ?.let(::stateFor)
}

@Composable
private fun rememberHomeSurfacePlan(
    state: LauncherShellState,
    dockPull: DockPullState,
): HomeSurfacePlan {
    val currentMode = state.homeLayout.viewMode
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val counterpartMode = state.homeLayoutSet.dockPullCounterpartMode(deviceClass, currentMode)
    val edges =
        ModeDockEdges(
            // Home's edge is the shared dock's: the user's configured edge if any, else the device
            // class's template default, exactly as every mode read it before the edge was per mode.
            home =
                resolveDockPosition(
                    state.homeLayout.dock.position,
                    state.settingsLayoutDeviceClass.templateDockPosition,
                ),
            library = state.homeLayoutSet.dockEdgeFor(deviceClass, ModeSurface.LIBRARY),
        )
    val pending = dockPull.pending
    // A committed switch stands in until the shell shows it -- or answers with anything else.
    val pendingMode =
        pending
            ?.takeIf { switch -> switch.mode != currentMode && switch.fromSet === state.homeLayoutSet }
            ?.mode
    if (pending != null && pendingMode == null) {
        SideEffect { dockPull.pending = null }
    }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(DOCK_PULL_PENDING_TIMEOUT_MILLIS)
            dockPull.pending = null
        }
    }
    val isTransitioning by remember(dockPull) {
        derivedStateOf { dockPull.transition !is DockPullTransitionState.Idle }
    }
    val previewMode = pendingMode ?: counterpartMode.takeIf { isTransitioning }
    val preview =
        previewMode?.let { mode ->
            remember(mode, state.homeLayoutSet, state.homeLayout, state.installedApps) {
                state.dockPullPreviewFor(mode)
            }
        }
    val composedModes =
        when {
            pendingMode != null -> listOf(pendingMode)
            isTransitioning -> listOf(currentMode, counterpartMode)
            else -> listOf(currentMode)
        }
    return HomeSurfacePlan(
        currentMode = currentMode,
        counterpartMode = counterpartMode,
        shownMode = pendingMode ?: currentMode,
        composedModes = composedModes,
        edges = edges,
        state = state,
        preview = preview,
    )
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
    modifier: Modifier = Modifier,
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

    Box(modifier = Modifier.fillMaxSize().then(modifier)) {
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

/** How long a committed switch stands in for the shell's answer before giving up on it. */
private const val DOCK_PULL_PENDING_TIMEOUT_MILLIS = 2_000L

/** Over both surfaces while a pull runs, so neither slides across the dock being pulled. */
private const val DOCK_PULL_DOCK_Z_INDEX = 1f
