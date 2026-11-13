@file:Suppress("LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.glance.GlanceHost
import com.riffle.app.launcher.glance.LocalGlanceHost
import com.riffle.app.launcher.library.LibrarySheet
import com.riffle.app.launcher.library.libraryState
import com.riffle.app.launcher.split.SplitPaneState
import com.riffle.app.launcher.split.SplitPanes
import com.riffle.app.launcher.split.rememberSplitPaneState
import com.riffle.app.launcher.split.streamPaneInsets
import com.riffle.app.launcher.stream.StreamDestination
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.dockpull.DockPullDestination
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.dockpull.pullDirection
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeDockEdges
import com.riffle.core.domain.launcher.home.ModeSurface
import com.riffle.core.domain.launcher.home.dockEdgeFor
import com.riffle.core.domain.launcher.home.modeSurface
import com.riffle.core.domain.launcher.split.SplitMode
import com.riffle.core.domain.launcher.split.SplitWindow
import com.riffle.core.domain.launcher.split.supportsSplit
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import kotlinx.coroutines.delay

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun HomeDestination(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    haptics: LauncherHaptics = NoopLauncherHaptics,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout? = null,
    /** The Stream (ADR 0003); with [streamShown] it replaces the Home surface and the Dock stays under it. */
    stream: StreamHost? = null,
    streamShown: Boolean = false,
    onAction: (LauncherShellAction) -> Unit,
) {
    val dockHost = rememberHomeDockHostState()
    val dockPull = rememberDockPullState()
    // "Connect Todoist" in the Tasks section: Settings > Stream > Tasks.
    val openTasksSettings = { onAction(LauncherShellAction.OpenSettingsPage(SettingsPage.STREAM_TASKS)) }
    val presentation = standardHomePresentation(state, widgetRenderers)
    val plan = rememberHomeSurfacePlan(state)
    // A committed pull stands in for the shell's answer until the shell shows it, so the surfaces do not flicker back.
    val shownStream = rememberStreamShown(dockPull, streamShown)
    // The Library is a sheet over Home or the Stream; a committed pull to it stands in for the shell the same way.
    val shownLibrary = rememberLibraryShown(dockPull, state.destination.isLibrary)
    // On an expanded posture Home and the Stream can share the screen (ADR 0003, Folded and unfolded).
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val splitting = stream != null && deviceClass.supportsSplit()
    val splitPane = rememberSplitPaneState()
    val splitSetup: () -> SplitPullSetup? = { if (splitting) splitPane.pullSetup() else null }
    // Glance widgets on Home read the Stream's provider and gateway, and open the Stream the way the Dock does.
    val openStreamFromGlance by rememberUpdatedState<(StreamFocusTarget) -> Unit> { target ->
        openStream(
            stream,
            dockPull,
            target,
            shownStream,
            plan.dockEdge.pullDirection,
            state.reducedMotion(),
            splitSetup(),
        )
    }
    val glanceHost =
        remember(stream, state.installedApps, appIconLoader, state.reducedMotion()) {
            stream?.let { host ->
                GlanceHost(
                    stream = host,
                    installedApps = state.installedApps,
                    appIconLoader = appIconLoader,
                    reducedMotion = state.reducedMotion(),
                    openStream = { target -> openStreamFromGlance(target) },
                )
            }
        }
    // The dynamic side is the Stream's: live apps and now playing, and in the contextual mode a tile per page.
    val dockInterpreter =
        rememberDockDynamicSide(
            stream = stream,
            shown = shownStream,
            liveAppSlots = state.homeLayout.dock.liveAppSlots,
            installedApps = state.installedApps,
            deviceClass = state.homeLayoutSet.activeKey.deviceClass,
            onOpenStream = { target ->
                if (state.destination.isLibrary) {
                    // The Library is open: a tile or a live app leaves it for the Stream, no transition to animate.
                    target?.let { stream?.focus?.request(it) }
                    onAction(LauncherShellAction.OpenStream)
                } else {
                    openStream(
                        stream,
                        dockPull,
                        target,
                        shownStream,
                        plan.dockEdge.pullDirection,
                        state.reducedMotion(),
                        splitSetup(),
                    )
                }
            },
            modeInterpreter = HomeDockInterpreter(),
            appShortcuts = state.appShortcutsByApp,
        )
    val pull =
        rememberHomeDockPullBinding(
            state = state,
            dockEdge = plan.dockEdge,
            stream =
                StreamPullInputs(
                    available = stream != null,
                    shown = shownStream,
                    split = if (splitting) splitSetup else null,
                    focus = stream?.focus,
                    // Pressing the Dock down and releasing toggles the Home and the contextual mode: from Home it opens
                    // the Stream on the page of the icon the press started on (else the last page), from the Stream it
                    // goes back to Home.
                    onPress = { entryKey ->
                        if (shownStream) {
                            dockPull.open(
                                DockPullDestination.HOME,
                                plan.dockEdge.pullDirection,
                                state.reducedMotion(),
                                splitSetup(),
                            )
                        } else {
                            openStream(
                                stream,
                                dockPull,
                                entryKey?.let(::dockDynamicEntryTarget),
                                shownStream,
                                plan.dockEdge.pullDirection,
                                state.reducedMotion(),
                                splitSetup(),
                            )
                        }
                    },
                ),
            dockPull = dockPull,
            hostState = dockHost,
            onAction = onAction,
        )

    // The dock draws from the same state the surfaces do; there is no longer a switch in flight between two of them.
    val dockHostState = plan.stateFor(plan.shownMode)
    val dockPresentation = standardHomePresentation(dockHostState, widgetRenderers)
    val showStream = stream != null && (shownStream || pull.isTransitioning)
    val showHome = !shownStream || pull.isTransitioning
    val showLibrary = shownLibrary || pull.isLibraryTransitioning
    // Once the Library's sheet covers Home or the Stream they are invisible, so assistive technology skips them too.
    val covered = shownLibrary && !pull.isLibraryTransitioning
    val coveredSemantics = if (covered) Modifier.clearAndSetSemantics {} else Modifier
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    if (splitting && stream != null) {
        RememberSplit(
            pane = splitPane,
            dockPull = dockPull,
            stream = stream,
            state = dockHostState,
            plan = plan,
            dockHost = dockHost,
            rootSize = rootSize,
            hinges = adaptiveStageWindowLayout?.separatingHinges.orEmpty(),
            shownStream = shownStream,
            onGoHome = {
                dockPull.pendingStreamShown = false
                onAction(LauncherShellAction.OpenHome)
            },
        )
    }

    // The shared backdrop every liquid-glass surface on Home samples from (see `LiquidGlassBackdrop.kt`): installed
    // here, around this content specifically, since this is exactly the wallpaper-plus-surface subtree every glass
    // surface on Home floats above.
    CompositionLocalProvider(LocalGlanceHost provides glanceHost) {
        ProvideLiquidGlassBackdrop { backdropCapture ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(pull.rootModifier)
                        .onSizeChanged { size -> rootSize = size }
                        .then(backdropCapture),
            ) {
                // The one dock, outside the mode surface: composed at the same place whichever surface runs below
                // (#1205, Decision 2). Composed first so the mode's overlays cover it; the grid's own frame sits
                // beneath it (see HOME_CONTENT_Z_INDEX). The dock never moves with a pull: it is the handle.
                HomeDockHost(
                    layout = dockHostState.homeLayout,
                    installedApps = dockHostState.installedApps,
                    presentation = dockPresentation,
                    position = plan.dockEdge,
                    hostState = dockHost,
                    appIconLoader = appIconLoader,
                    onAction = onAction,
                    interpreter = dockInterpreter,
                    haptics = haptics,
                    dockModifier = pull.dockModifier,
                )
                // Home first, then the Stream over it: while a pull runs both are composed and the Stream slides over
                // Home. Both stay direct siblings of the dock, so their zIndex ranks them against it (see
                // HOME_CONTENT_Z_INDEX) and the dock keeps the touches where they overlap.
                val homeContent: @Composable () -> Unit = {
                    key(plan.currentMode) {
                        ModeSurfaceContent(
                            state = plan.stateFor(plan.currentMode),
                            dockEdge = plan.edges.edgeFor(plan.currentMode.modeSurface),
                            surfaceModifier = pull.homeModifier(libraryShown = shownLibrary).then(coveredSemantics),
                            dockHost = dockHost,
                            presentation = presentation,
                            appIconLoader = appIconLoader,
                            widgetRenderers = widgetRenderers,
                            haptics = haptics,
                            onAction = onAction,
                        )
                    }
                }
                if (splitting && stream != null) {
                    // Home and the Stream side by side (or one over the other), with the divider between them.
                    SplitPanes(
                        pane = splitPane,
                        dockPull = dockPull,
                        homeContent = homeContent,
                        streamContent = {
                            StreamDestination(
                                host = stream,
                                deviceClass = deviceClass,
                                installedApps = dockHostState.installedApps,
                                appIconLoader = appIconLoader,
                                reducedMotion = state.reducedMotion(),
                                onConnectTasks = openTasksSettings,
                                liveAppSlots = dockHostState.homeLayout.dock.liveAppSlots,
                                contentPadding =
                                    streamPaneInsets(
                                        insets =
                                            cardsPanelInsetPolicy(dockHostState).safeDrawingPanelInsets()
                                                .asPaddingValues(),
                                        window = splitPane.window,
                                        split = splitPane.restMode == SplitMode.SPLIT,
                                    ),
                            )
                        },
                    )
                } else {
                    if (showHome) homeContent()
                    if (showStream && stream != null) {
                        StreamHomeSurface(
                            stream = stream,
                            state = dockHostState,
                            dockHost = dockHost,
                            dockPosition = plan.dockEdge,
                            appIconLoader = appIconLoader,
                            onConnectTasks = openTasksSettings,
                            modifier = pull.streamModifier(libraryShown = shownLibrary).then(coveredSemantics),
                        )
                    }
                }
                if (showLibrary) {
                    LibraryHomeSurface(
                        state = dockHostState,
                        dockHost = dockHost,
                        dockPosition = plan.dockEdge,
                        appIconLoader = appIconLoader,
                        haptics = haptics,
                        pull = pull,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

private fun LauncherShellState.reducedMotion(): Boolean = launcherSettings.motion.reducedMotion

/**
 * Opens the Stream from a Dock entry: records where it should land, then, when it is not showing, runs the same
 * settle a pull's commit would. When it already shows, only the landing moves.
 */
private fun openStream(
    stream: StreamHost?,
    dockPull: DockPullState,
    target: StreamFocusTarget?,
    shown: Boolean,
    direction: DockPullDirection,
    reducedMotion: Boolean,
    split: SplitPullSetup?,
) {
    if (stream == null) return
    target?.let(stream.focus::request)
    if (!shown) dockPull.open(DockPullDestination.STREAM, direction, reducedMotion, split)
}

/**
 * Whether the Stream shows, counting a committed pull that the shell has not shown yet. The stand-in clears the moment
 * the shell agrees (or answers with anything else) and after a timeout, so a refused switch cannot stick.
 */
@Composable
private fun rememberStreamShown(
    dockPull: DockPullState,
    shellShown: Boolean,
): Boolean {
    val pending = dockPull.pendingStreamShown
    if (pending != null && pending == shellShown) {
        SideEffect { dockPull.pendingStreamShown = null }
    }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(DOCK_PULL_PENDING_TIMEOUT_MILLIS)
            dockPull.pendingStreamShown = null
        }
    }
    return pending ?: shellShown
}

/**
 * Whether the Library shows, counting a committed pull to it that the shell has not shown yet. The stand-in clears the
 * moment the shell agrees and after a timeout, so a refused switch cannot stick.
 */
@Composable
private fun rememberLibraryShown(
    dockPull: DockPullState,
    shellShown: Boolean,
): Boolean {
    val pending = dockPull.pendingLibraryShown
    if (pending && shellShown) {
        SideEffect { dockPull.pendingLibraryShown = false }
    }
    LaunchedEffect(pending) {
        if (pending) {
            delay(DOCK_PULL_PENDING_TIMEOUT_MILLIS)
            dockPull.pendingLibraryShown = false
        }
    }
    return pending || shellShown
}

/**
 * The Library (ADR 0003) over the Dock's room, as the Stream is: laid out inside what the Dock reserves on its edge,
 * under the Dock in the draw order so the Dock stays tappable, and inside the system bar insets (the keyboard's too, so
 * the field stays visible while typing). Launching an app from it closes it.
 */
@Composable
private fun LibraryHomeSurface(
    state: LauncherShellState,
    dockHost: HomeDockHostState,
    dockPosition: DockPosition,
    appIconLoader: AppIconLoader,
    haptics: LauncherHaptics,
    pull: HomeDockPullBinding,
    onAction: (LauncherShellAction) -> Unit,
) {
    val visibleLayout = state.homeLayout.visibleTo(state.installedApps)
    val dockExtent = dockHost.reservedExtent(visibleLayout, dockPosition)
    val insets = cardsPanelInsetPolicy(state).safeDrawingPanelInsets().asPaddingValues()
    val libraryState = remember(state) { state.libraryState() }
    val latestOnAction = rememberUpdatedState(onAction)
    val close: () -> Unit = {
        latestOnAction.value(
            if (libraryState.overStream) LauncherShellAction.OpenStream else LauncherShellAction.OpenHome,
        )
    }
    val context =
        AppListContext(
            homeLayout = state.homeLayout,
            overlayDock = state.launcherSettings.overlayDock,
            notificationGroupsByApp = state.notificationGroupsByApp,
            appShortcutsByApp = state.appShortcutsByApp,
            appIconLoader = appIconLoader,
            haptics = haptics,
            onAction = { action ->
                latestOnAction.value(action)
                if (action.closesLibrary()) close()
            },
        )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(HOME_CONTENT_Z_INDEX)
                .dockInteractionPadding(dockPosition, dockExtent),
    ) {
        // The margin around the sheet belongs to it: a tap there closes the Library instead of reaching what is under.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = stringResource(R.string.library_close_action),
                        onClick = close,
                    ),
        )
        LibrarySheet(
            modifier = Modifier.padding(insets).padding(RiffleSpacing.s),
            state = libraryState,
            context = context,
            reducedMotion = state.reducedMotion(),
            direction = dockPosition.pullDirection,
            awayFraction = pull::libraryAwayFraction,
            onClose = close,
        )
    }
}

/** What launching does to the Library: starting an app, a shortcut or a web search closes it. */
private fun LauncherShellAction.closesLibrary(): Boolean =
    this is LauncherShellAction.LaunchApp ||
        this is LauncherShellAction.LaunchAppShortcut ||
        this is LauncherShellAction.SearchWeb

/**
 * The Stream over the Dock's room: laid out inside what the Dock reserves on its edge (as the Cards stage is),
 * under the
 * Dock in the draw order so the Dock stays tappable. Content scrolls inside the system bar insets.
 */
@Composable
private fun StreamHomeSurface(
    stream: StreamHost,
    state: LauncherShellState,
    dockHost: HomeDockHostState,
    dockPosition: DockPosition,
    appIconLoader: AppIconLoader,
    onConnectTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleLayout = state.homeLayout.visibleTo(state.installedApps)
    val dockExtent = dockHost.reservedExtent(visibleLayout, dockPosition)
    val insets = cardsPanelInsetPolicy(state).safeDrawingPanelInsets().asPaddingValues()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(HOME_CONTENT_Z_INDEX)
                .then(modifier)
                .dockInteractionPadding(dockPosition, dockExtent),
    ) {
        StreamDestination(
            host = stream,
            deviceClass = state.homeLayoutSet.activeKey.deviceClass,
            installedApps = state.installedApps,
            appIconLoader = appIconLoader,
            reducedMotion = state.launcherSettings.motion.reducedMotion,
            contentPadding = insets,
            liveAppSlots = state.homeLayout.dock.liveAppSlots,
            onConnectTasks = onConnectTasks,
        )
    }
}

/**
 * Keeps [pane] current for an expanded posture: the window (size, insets, the Dock's room and side, hinges), the
 * remembered split read from and written to the Stream's layout store, and the stand-ins the pane settles through.
 */
@Composable
private fun RememberSplit(
    pane: SplitPaneState,
    dockPull: DockPullState,
    stream: StreamHost,
    state: LauncherShellState,
    plan: HomeSurfacePlan,
    dockHost: HomeDockHostState,
    rootSize: IntSize,
    hinges: List<com.riffle.core.domain.launcher.cards.AdaptiveStageHingeBounds>,
    shownStream: Boolean,
    onGoHome: () -> Unit,
) {
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val density = LocalDensity.current.density
    val insets = cardsPanelInsetPolicy(state).safeDrawingPanelInsets().asPaddingValues()
    val dockExtent = dockHost.reservedExtent(state.homeLayout.visibleTo(state.installedApps), plan.dockEdge)
    val dockBounds = dockHost.bounds.value
    val dynamicBounds = dockHost.dynamicBounds.value
    val dynamicAtRight =
        if (dockBounds != null && dynamicBounds != null) dynamicBounds.center.x >= dockBounds.center.x else true
    val window =
        if (rootSize == IntSize.Zero) {
            null
        } else {
            SplitWindow(
                widthDp = rootSize.width / density,
                heightDp = rootSize.height / density,
                dockEdge = plan.dockEdge,
                dockExtentDp = dockExtent.value,
                safeLeftDp = insets.calculateLeftPadding(LayoutDirection.Ltr).value,
                safeTopDp = insets.calculateTopPadding().value,
                safeRightDp = insets.calculateRightPadding(LayoutDirection.Ltr).value,
                safeBottomDp = insets.calculateBottomPadding().value,
                hinges = hinges,
                dynamicSideAtRight = dynamicAtRight,
            )
        }
    val layouts by stream.layouts.layouts.collectAsState()
    val stored = layouts.forPosture(deviceClass).split
    val shell = rememberUpdatedState(shownStream)
    LaunchedEffect(stream) { stream.layouts.initialize() }
    LaunchedEffect(stored) { pane.split = stored }
    SideEffect {
        pane.window = window
        pane.reducedMotion = state.reducedMotion()
        pane.streamShown = { dockPull.pendingStreamShown ?: shell.value }
        pane.onGoHome = onGoHome
        pane.onSplitChanged = { next ->
            stream.layouts.update(deviceClass, StreamLayoutAction.SetSplit(next.fraction, next.lastShown))
        }
        dockPull.onSplitSettled = pane::onPullSettled
    }
}

@Composable
private fun ModeSurfaceContent(
    state: LauncherShellState,
    dockEdge: DockPosition,
    surfaceModifier: Modifier,
    dockHost: HomeDockHostState,
    presentation: StandardHomePresentation,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
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
        dockEdge = dockEdge,
        surfaceModifier = surfaceModifier,
        onAction = onAction,
    )
}

/** What [HomeDestination] composes for one shell state: the shell's mode, and the edge its dock stands on. */
private class HomeSurfacePlan(
    val currentMode: LauncherViewMode,
    /** Each surface's dock edge: Home's is the shared dock's, Library's its own (ModeDockEdges.kt). */
    val edges: ModeDockEdges,
    private val state: LauncherShellState,
) {
    val shownMode: LauncherViewMode get() = currentMode

    /** The edge the dock stands on: the shown surface's. */
    val dockEdge: DockPosition get() = edges.edgeFor(currentMode.modeSurface)

    /** The shell state [mode]'s surface draws from. */
    @Suppress("UnusedParameter")
    fun stateFor(mode: LauncherViewMode): LauncherShellState = state
}

@Composable
private fun rememberHomeSurfacePlan(state: LauncherShellState): HomeSurfacePlan {
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val edges =
        ModeDockEdges(
            // Home's edge is the shared dock's: the user's configured edge if any, else the device class's template
            // default, exactly as every mode read it before the edge was per mode.
            home =
                resolveDockPosition(
                    state.homeLayout.dock.position,
                    state.settingsLayoutDeviceClass.templateDockPosition,
                ),
            library = state.homeLayoutSet.dockEdgeFor(deviceClass, ModeSurface.LIBRARY),
        )
    return HomeSurfacePlan(currentMode = state.homeLayout.viewMode, edges = edges, state = state)
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
    )

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}

/** How long a committed switch stands in for the shell's answer before giving up on it. */
private const val DOCK_PULL_PENDING_TIMEOUT_MILLIS = 2_000L
