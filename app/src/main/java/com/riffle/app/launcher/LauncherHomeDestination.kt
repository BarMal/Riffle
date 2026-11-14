@file:Suppress("LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.glance.GlanceHost
import com.riffle.app.launcher.glance.LocalGlanceHost
import com.riffle.app.launcher.library.LibraryPagedView
import com.riffle.app.launcher.library.LibrarySheet
import com.riffle.app.launcher.library.libraryState
import com.riffle.app.launcher.search.DockSearchSurface
import com.riffle.app.launcher.stream.StreamDestination
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.dockpull.pullDirection
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeHandle
import com.riffle.core.domain.launcher.home.HomeHandleLayout
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeDockEdges
import com.riffle.core.domain.launcher.home.ModeSurface
import com.riffle.core.domain.launcher.home.dockEdgeFor
import com.riffle.core.domain.launcher.home.handleAxis
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import com.riffle.core.domain.launcher.home.modeSurface
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.settings.EffectiveFeatures
import com.riffle.core.domain.launcher.settings.HomeMode
import com.riffle.core.domain.launcher.settings.LayoutPosture
import com.riffle.core.domain.launcher.settings.PostureLayoutSettings
import com.riffle.core.domain.launcher.settings.effectiveFeatures
import com.riffle.core.domain.launcher.settings.forAppLibraryHome
import com.riffle.core.domain.launcher.settings.layoutPosture
import com.riffle.core.domain.launcher.settings.without
import com.riffle.core.domain.launcher.stream.StreamColumnRect
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import kotlinx.coroutines.delay

/**
 * Home and what stands on it (the Dock, the Stream, the Library). The Stream switch (and Simple mode) is applied
 * here: a Stream that is switched off is never handed on, so no press, live page, tile or hint is left to reach it.
 */
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
    /**
     * Where the first-run hint ("press the Dock down to see what's live") tells the shell it is done with, by its
     * button or by the Stream having opened by any route. Null where the host shows no hint (the hint is not drawn).
     */
    onDockPressHintSeen: (() -> Unit)? = null,
    onAction: (LauncherShellAction) -> Unit,
) {
    val streamOn = state.launcherSettings.effectiveFeatures().stream
    HomeDestinationContent(
        state = state,
        appIconLoader = appIconLoader,
        widgetRenderers = widgetRenderers,
        haptics = haptics,
        adaptiveStageWindowLayout = adaptiveStageWindowLayout,
        stream = stream.takeIf { streamOn },
        streamShown = streamShown && streamOn,
        onDockPressHintSeen = onDockPressHintSeen,
        onAction = onAction,
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun HomeDestinationContent(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    haptics: LauncherHaptics = NoopLauncherHaptics,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout? = null,
    /** The Stream (ADR 0003); with [streamShown] it replaces the Home surface and the Dock stays under it. */
    stream: StreamHost? = null,
    streamShown: Boolean = false,
    /**
     * Where the first-run hint ("press the Dock down to see what's live") tells the shell it is done with, by its
     * button or by the Stream having opened by any route. Null where the host shows no hint (the hint is not drawn).
     */
    onDockPressHintSeen: (() -> Unit)? = null,
    onAction: (LauncherShellAction) -> Unit,
) {
    // The feature switches (Simple mode applied): a feature that is off is not offered, so it has no entry point left.
    val features = state.launcherSettings.effectiveFeatures()
    val dockHost = rememberHomeDockHostState()
    val dockPull = rememberDockPullState()
    // The trampoline and the fan: the Dock's own motion between Home and the Stream (not on an expanded posture).
    val motion = rememberDockMotionState()
    // The adjustable motion (Settings > Motion): the springs, the press and the stack read it; defaults change nothing.
    val dockMotionSettings = state.launcherSettings.motion.dock
    val dockTuning = remember(dockMotionSettings) { dockMotionSettings.tuning() }
    SideEffect { motion.tuning = dockTuning }
    // The light ticks (a card landing, an icon passed while choosing) have their own switch on top of the strength.
    val tickHaptics = remember(haptics, dockMotionSettings.ticks) { haptics.withTicks(dockMotionSettings.ticks) }
    // Hold to choose: the selection while a press on the Dock is choosing.
    val choice = remember { DockChoiceState() }
    // "Connect Todoist" in the Tasks section: Settings > Stream > Tasks.
    val openTasksSettings = { onAction(LauncherShellAction.OpenSettingsPage(SettingsPage.STREAM_TASKS)) }
    // Unfolded, the Stream is the same contextual mode as folded, drawn as an upright column attached to the
    // Dock over a Home that stays in place (ADR 0003, Amendment 2): there is no split pane and no second layout.
    val deviceClass = state.homeLayoutSet.activeKey.deviceClass
    val unfolded = deviceClass.layoutPosture() == LayoutPosture.UNFOLDED
    val columnMode = stream != null && unfolded
    val plan = rememberHomeSurfacePlan(state)
    // A committed pull stands in for the shell's answer until the shell shows it, so the surfaces do not flicker back.
    val shownStream = rememberStreamShown(dockPull, streamShown)
    // Unfolded with the Stream out, the column stands where the handle does: the handle is not drawn then.
    val presentation = standardHomePresentation(state, widgetRenderers).copy(handleHidden = columnMode && shownStream)
    // What the shell shows moving away from where the motion is heading (Home pressed, say) ends it where it is.
    LaunchedEffect(shownStream) { motion.syncShell(shownStream) }
    // The Library is a sheet over Home or the Stream; a committed pull to it stands in for the shell the same way.
    val shownLibrary = rememberLibraryShown(dockPull, state.destination.isLibrary)
    // Glance widgets on Home read the Stream's provider and gateway, and open the Stream the way the Dock does.
    val openStreamFromGlance by rememberUpdatedState<(StreamFocusTarget) -> Unit> { target ->
        openStream(
            stream,
            StreamSwitch(motion),
            target,
            shownStream,
            state.reducedMotion(),
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
                        StreamSwitch(motion),
                        target,
                        shownStream,
                        state.reducedMotion(),
                    )
                }
            },
            modeInterpreter = HomeDockInterpreter(),
            appShortcuts = state.appShortcutsByApp,
            choosing = choice.choosing,
            pinnedApps = state.homeLayout.visibleTo(state.installedApps).pinnedApps(),
            showLiveApps = features.dockLiveApps,
        )
    val pull =
        rememberHomeDockPullBinding(
            state = state,
            dockEdge = plan.dockEdge,
            stream =
                StreamPullInputs(
                    available = stream != null,
                    shown = shownStream,
                    homeStays = columnMode,
                    focus = stream?.focus,
                    // Pressing the Dock down and releasing toggles the Home and the contextual mode: from Home it opens
                    // the Stream on the page of the icon the press started on (else the last page), from the Stream it
                    // goes back to Home.
                    onPress = { entryKey ->
                        if (shownStream) {
                            // With a card open the press is the card's first: it closes and the Stream stays, the
                            // Dock springing back from its dip without the Stream leaving.
                            if (stream?.dock?.openCards?.closeByPress() == true) {
                                motion.revert()
                            } else {
                                StreamSwitch(motion).close(state.reducedMotion())
                            }
                        } else {
                            openStream(
                                stream,
                                StreamSwitch(motion),
                                entryKey?.let(::dockDynamicEntryTarget),
                                shownStream,
                                state.reducedMotion(),
                            )
                        }
                    },
                    // Choosing while the Stream is out: it stays and lands on the chosen page, as a tap on a tile does.
                    onSwitchPage = { entryKey -> dockInterpreter.onDynamicEntryDelegated(entryKey) },
                    // The press's accessible equivalents close an open card before they close the Stream.
                    openCard =
                        OpenCardPress(
                            isOpen = stream?.dock?.openCards?.state?.isOpen == true,
                            close = { stream?.dock?.openCards?.closeByPress() == true },
                        ),
                ),
            dockPull = dockPull,
            motion = motion,
            hostState = dockHost,
            choice = choice,
            haptics = tickHaptics,
            onAction = onAction,
        )

    // The dock draws from the same state the surfaces do; there is no longer a switch in flight between two of them.
    val dockHostState = plan.stateFor(plan.shownMode)
    val dockPresentation = standardHomePresentation(dockHostState, widgetRenderers)
    // Choosing from Home composes the Stream, unseen, so the Dock can draw a tile for each of its pages.
    val streamForTiles = pull.isChoosingFromHome && !shownStream
    val showStream = stream != null && (shownStream || pull.isTransitioning || streamForTiles)
    // Unfolded, Home stays under and beside the column whatever the Stream does.
    val showHome = columnMode || !shownStream || pull.isTransitioning || pull.motionShowsHome
    val showLibrary = shownLibrary || pull.isLibraryTransitioning
    // Search is its own surface, opened from the Dock: it is the Library's destination but not its sheet.
    val searchOpen = state.destination == ShellDestination.SEARCH
    val hintSeen = state.launcherSettings.hints.dockPressSeen
    val hintShown =
        shouldShowDockPressHint(
            seen = hintSeen,
            streamAvailable = stream != null && onDockPressHintSeen != null,
            streamShown = shownStream,
            libraryShown = showLibrary,
            motionRunning = motion.running,
            choosing = choice.choosing,
            simpleMode = features.simpleMode,
        )
    // The Stream opening by any route (the press, a tap on a live app, an accessibility action) means it was found.
    LaunchedEffect(shownStream, hintSeen) {
        if (shownStream && !hintSeen) onDockPressHintSeen?.invoke()
    }
    // Once the Library's sheet covers Home or the Stream they are invisible, so assistive technology skips them too.
    val covered = shownLibrary && !pull.isLibraryTransitioning
    val coveredSemantics = if (covered) Modifier.clearAndSetSemantics {} else Modifier
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    // The Stream's column and the fade of Home's grid beneath it (see StreamColumn and GridFadeField). Folded, the same
    // rectangle stands for the stack when apps show behind the cards.
    // The Dock's body as it stands at rest: the column touches that edge, so a press's dip does not move it.
    val dockBodyNow = dockHost.bodyBounds.value
    val restDockBody = remember { mutableStateOf<Rect?>(null) }
    SideEffect { if (!motion.running) restDockBody.value = dockBodyNow }
    val column =
        rememberStreamColumnGeometry(
            columnMode = columnMode,
            streamShown = shownStream,
            closing = motion.isClosing,
            cardOpen = stream?.dock?.openCards?.state?.isOpen == true,
            peekThrough = state.postureLayout().appPeekThrough,
            reducedMotion = state.reducedMotion(),
            dockEdge = plan.dockEdge,
            dockExtent =
                dockHost.reservedExtent(
                    dockHostState.homeLayout.visibleTo(dockHostState.installedApps),
                    plan.dockEdge,
                ),
            insets = cardsPanelInsetPolicy(state).safeDrawingPanelInsets().asPaddingValues(),
            dockBounds = dockBodyNow,
            dockRestBounds = restDockBody.value ?: dockBodyNow,
            hinges =
                adaptiveStageWindowLayout?.separatingHinges.orEmpty().filter { it.isVertical }.map {
                    StreamColumnRect(
                        it.leftDp.toFloat(),
                        it.topDp.toFloat(),
                        it.rightDp.toFloat(),
                        it.bottomDp.toFloat(),
                    )
                },
            rootSize = rootSize,
        )
    // The shared backdrop every liquid-glass surface on Home samples from (see `LiquidGlassBackdrop.kt`): installed
    // here, around this content specifically, since this is exactly the wallpaper-plus-surface subtree every glass
    // surface on Home floats above.
    CompositionLocalProvider(
        LocalGlanceHost provides glanceHost,
        LocalDockFan provides pull.fanSource,
        LocalLauncherHaptics provides tickHaptics,
        LocalDockMotionTuning provides dockTuning,
    ) {
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
                    dockDipDp = pull::dockDipDp,
                    choice = choice,
                )
                // Home first, then the Stream over it: while the Dock's change runs both are composed, and the
                // Stream's cards rise out of the Dock over Home. Both stay direct siblings of the dock, so their zIndex
                // ranks them against it (see HOME_CONTENT_Z_INDEX) and the dock keeps the touches where they overlap.
                val homeContent: @Composable () -> Unit = {
                    CompositionLocalProvider(LocalGridFade provides column.fade.takeIf { columnMode }) {
                        key(plan.currentMode, features.homeMode) {
                            if (features.homeMode == HomeMode.AppLibrary) {
                                AppLibraryHome(
                                    state = plan.stateFor(plan.currentMode),
                                    features = features,
                                    dockHost = dockHost,
                                    dockPosition = plan.edges.edgeFor(plan.currentMode.modeSurface),
                                    surfaceModifier =
                                        pull.homeModifier(libraryShown = shownLibrary).then(coveredSemantics),
                                    handleHidden = presentation.handleHidden,
                                    appIconLoader = appIconLoader,
                                    haptics = haptics,
                                    onAction = onAction,
                                )
                            } else {
                                ModeSurfaceContent(
                                    state = plan.stateFor(plan.currentMode),
                                    dockEdge = plan.edges.edgeFor(plan.currentMode.modeSurface),
                                    surfaceModifier =
                                        pull.homeModifier(libraryShown = shownLibrary).then(coveredSemantics),
                                    dockHost = dockHost,
                                    presentation = presentation,
                                    appIconLoader = appIconLoader,
                                    widgetRenderers = widgetRenderers,
                                    haptics = haptics,
                                    onAction = onAction,
                                )
                            }
                        }
                    }
                }
                if (showHome) homeContent()
                if (showStream && stream != null) {
                    val streamSurfaceModifier =
                        pull.streamModifier(libraryShown = shownLibrary)
                            .then(if (streamForTiles) Modifier.clearAndSetSemantics {} else coveredSemantics)
                    if (columnMode) {
                        // Unfolded: the same Stream, in a column attached to the Dock, over the Home that stays.
                        StreamColumnSurface(
                            stream = stream,
                            state = dockHostState,
                            bounds = column.bounds,
                            appIconLoader = appIconLoader,
                            onConnectTasks = openTasksSettings,
                            modifier = streamSurfaceModifier,
                        )
                    } else {
                        StreamHomeSurface(
                            stream = stream,
                            state = dockHostState,
                            dockHost = dockHost,
                            dockPosition = plan.dockEdge,
                            appIconLoader = appIconLoader,
                            onConnectTasks = openTasksSettings,
                            modifier = streamSurfaceModifier,
                        )
                    }
                }
                if (showLibrary) {
                    LibraryHomeSurface(
                        search = searchOpen,
                        // Unfolded, search stands in the Stream column's own rectangle (against the Dock's inner edge).
                        searchColumn = if (unfolded && rootSize != IntSize.Zero) column.restBounds else null,
                        tuning = dockTuning,
                        state = dockHostState,
                        dockHost = dockHost,
                        dockPosition = plan.dockEdge,
                        appIconLoader = appIconLoader,
                        haptics = haptics,
                        pull = pull,
                        onAction = onAction,
                    )
                }
                // The first-run hint: shown once on Home, gone for good once dismissed or once the Stream has opened.
                if (hintShown) {
                    DockPressHint(
                        dockBounds = dockHost.bounds.value,
                        pull = plan.dockEdge.pullDirection,
                        onDismiss = { onDockPressHintSeen?.invoke() },
                        // The Home handle stands just above a bottom Dock; the hint sits above the handle.
                        clearance =
                            if (plan.dockEdge == DockPosition.BOTTOM) {
                                val layout = state.postureLayout()
                                HomeHandleLayout.reservedDp(
                                    DockPosition.BOTTOM,
                                    layout.pageScroll.handleAxis,
                                    state.homeLayout.pages.size,
                                    layout.showsDots && HomeHandle.showsDots(state.homeLayout.pages.size),
                                ).dp
                            } else {
                                0.dp
                            },
                    )
                }
            }
        }
    }
}

private fun LauncherShellState.reducedMotion(): Boolean = launcherSettings.motion.reducedMotion

/** How the Stream is opened and closed from outside a gesture's own release: the Dock's trampoline. */
private class StreamSwitch(
    private val motion: DockMotionState,
) {
    /** Whether a closing is already under way, so a request to open takes it back. */
    val closing: Boolean get() = motion.isClosing

    fun open(reducedMotion: Boolean) = motion.run(open = true, reducedMotion = reducedMotion, shown = false)

    fun close(reducedMotion: Boolean) = motion.run(open = false, reducedMotion = reducedMotion, shown = true)
}

/**
 * Opens the Stream from a Dock entry: records where it should land, then, when it is not showing (or is on its way
 * out), runs the Dock's change to it. When it already shows, only the landing moves.
 */
private fun openStream(
    stream: StreamHost?,
    switch: StreamSwitch,
    target: StreamFocusTarget?,
    shown: Boolean,
    reducedMotion: Boolean,
) {
    if (stream == null) return
    target?.let(stream.focus::request)
    if (!shown || switch.closing) switch.open(reducedMotion)
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
    search: Boolean,
    searchColumn: (() -> StreamColumnRect?)?,
    tuning: DockMotionTuning,
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
    if (search) {
        // Search rises from the Dock (the handle above it, or the Dock's own action), field on the Dock's edge. Its
        // press catcher covers the whole window; the panel keeps clear of the Dock's room and the insets itself.
        val ltr = LayoutDirection.Ltr

        fun extra(edge: DockPosition): Dp = if (dockPosition == edge) dockExtent else 0.dp
        val room =
            PaddingValues.Absolute(
                left = insets.calculateLeftPadding(ltr) + extra(DockPosition.LEFT),
                top = insets.calculateTopPadding() + extra(DockPosition.TOP),
                right = insets.calculateRightPadding(ltr) + extra(DockPosition.RIGHT),
                bottom = insets.calculateBottomPadding() + extra(DockPosition.BOTTOM),
            )
        Box(modifier = Modifier.fillMaxSize().zIndex(HOME_CONTENT_Z_INDEX)) {
            DockSearchSurface(
                state = libraryState,
                context = context,
                reducedMotion = state.reducedMotion(),
                direction = dockPosition.pullDirection,
                tuning = tuning,
                onClose = close,
                contentPadding = room,
                column = searchColumn,
            )
        }
        return
    }
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
            // The paged View has no sheet to inset: its grid starts where Home's does.
            modifier =
                if (libraryState.view == LibraryView.Paged) {
                    Modifier.padding(insets)
                } else {
                    Modifier.padding(insets).padding(RiffleSpacing.s)
                },
            state = libraryState,
            context = context,
            reducedMotion = state.reducedMotion(),
            direction = dockPosition.pullDirection,
            awayFraction = pull::libraryAwayFraction,
            onClose = close,
            dockEdge = dockPosition,
        )
    }
}

/**
 * Home in the App library Home mode: the paged Library View in Home's place, laid out as Home is (inside what the Dock
 * reserves, under it in the draw order, inside the system bar insets). The placed layout is untouched and comes back
 * with the other mode. Home's gestures still work, except the ones that act on the placed pages. Long-pressing
 * the empty space offers Settings and, outside Simple mode, a way back to the placed items.
 */
@Composable
private fun AppLibraryHome(
    state: LauncherShellState,
    features: EffectiveFeatures,
    dockHost: HomeDockHostState,
    dockPosition: DockPosition,
    surfaceModifier: Modifier,
    handleHidden: Boolean,
    appIconLoader: AppIconLoader,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
) {
    val visibleLayout = state.homeLayout.visibleTo(state.installedApps)
    val dockExtent = dockHost.reservedExtent(visibleLayout, dockPosition)
    val insets = cardsPanelInsetPolicy(state).safeDrawingPanelInsets().asPaddingValues()
    val libraryState = remember(state) { state.libraryState() }
    val latestOnAction = rememberUpdatedState(onAction)
    val context =
        AppListContext(
            homeLayout = state.homeLayout,
            overlayDock = state.launcherSettings.overlayDock,
            notificationGroupsByApp = state.notificationGroupsByApp,
            appShortcutsByApp = state.appShortcutsByApp,
            appIconLoader = appIconLoader,
            haptics = haptics,
            onAction = { action -> latestOnAction.value(action) },
        )
    val placedLabel = stringResource(R.string.home_mode_show_placed)
    val settingsLabel = stringResource(R.string.home_mode_settings)
    val actions =
        listOfNotNull(
            CustomAccessibilityAction(placedLabel) {
                onAction(LauncherShellAction.SelectHomeMode(HomeMode.PlacedItems))
                true
            }.takeIf { !features.simpleMode },
            CustomAccessibilityAction(settingsLabel) {
                onAction(LauncherShellAction.OpenSettings)
                true
            },
        )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(HOME_CONTENT_Z_INDEX)
                .then(surfaceModifier)
                .dockInteractionPadding(dockPosition, dockExtent)
                .homeGestureInput(
                    enabled = state.homeLayout.editMode == HomeEditMode.Browsing,
                    settings =
                        state.launcherSettings.gestures.homeGestures.without(features).forAppLibraryHome(),
                    onAction = onAction,
                )
                .padding(insets),
    ) {
        LibraryPagedView(
            state = libraryState,
            context = context,
            reducedMotion = state.reducedMotion(),
            onOpenSearch = { onAction(LauncherShellAction.OpenSearch) },
            backgroundActions = actions,
            dockEdge = dockPosition,
            handleHidden = handleHidden,
        ) { dismiss ->
            if (!features.simpleMode) {
                DropdownMenuItem(
                    text = { Text(placedLabel) },
                    onClick = {
                        dismiss()
                        onAction(LauncherShellAction.SelectHomeMode(HomeMode.PlacedItems))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(settingsLabel) },
                onClick = {
                    dismiss()
                    onAction(LauncherShellAction.OpenSettings)
                },
            )
        }
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
            pinnedApps = visibleLayout.pinnedApps(),
            appShortcuts = state.appShortcutsByApp,
            launcherRole = state.homeRoleStatus != HomeRoleStatus.NOT_DEFAULT_HOME,
            streamSettings = state.launcherSettings.stream,
            dockCanHoldActions = dockPosition.isHorizontalEdge,
        )
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
        homeGestures = state.launcherSettings.gestures.homeGestures.without(state.launcherSettings.effectiveFeatures()),
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
        layout = state.postureLayout(),
        libraryEnabled = state.launcherSettings.effectiveFeatures().library,
    )

/** The layout of the posture the device is being held in (ADR 0003, Amendment 2). */
internal fun LauncherShellState.postureLayout(): PostureLayoutSettings =
    launcherSettings.layout.forDeviceClass(homeLayoutSet.activeKey.deviceClass)

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}

/** How long a committed switch stands in for the shell's answer before giving up on it. */
private const val DOCK_PULL_PENDING_TIMEOUT_MILLIS = 2_000L
