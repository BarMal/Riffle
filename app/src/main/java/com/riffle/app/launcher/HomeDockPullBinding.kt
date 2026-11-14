package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.riffle.app.R
import com.riffle.app.launcher.stream.StreamFocusRequests
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.dock.DockChoice
import com.riffle.core.domain.launcher.dock.DockChoiceItem
import com.riffle.core.domain.launcher.dock.DockPressOutcome
import com.riffle.core.domain.launcher.dock.FanFrame
import com.riffle.core.domain.launcher.dockpull.DockPullDestination
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.dockpull.DockPullEntryExtent
import com.riffle.core.domain.launcher.dockpull.StreamPullFrame
import com.riffle.core.domain.launcher.dockpull.dockPullEntryAt
import com.riffle.core.domain.launcher.dockpull.pullDirection
import com.riffle.core.domain.launcher.dockpull.streamPullFrame
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeEditMode

/** What [rememberHomeDockPullBinding] needs to know about the Stream. */
internal class StreamPullInputs(
    /** Whether the shell has a Stream; without one the Dock has no dynamic side and every pull opens the Library. */
    val available: Boolean,
    /** Whether the Stream is showing now, counting a committed switch the shell has not shown yet. */
    val shown: Boolean,
    /** Where a pull starts on an expanded posture (Home only, split, Stream only), read fresh; null on the phone. */
    val split: (() -> SplitPullSetup?)? = null,
    /** Where the Stream lands; a landing a pull asked for and that sprang back is dropped. */
    val focus: StreamFocusRequests? = null,
    /**
     * A committed press of the Dock (down, then release past the threshold): toggles the Home and the contextual mode.
     * Called with the key of the dynamic entry the press started on, or null; from Home, choosing ends here too, with
     * the key of the entry chosen.
     */
    val onPress: (entryKey: String?) -> Unit = {},
    /** A press that chose an entry while the Stream was out: the Stream stays and switches to that entry's page. */
    val onSwitchPage: (entryKey: String) -> Unit = {},
)

/**
 * How [HomeDestination] wires the Dock's pull (the Library) and press (Home and the contextual mode) into the Dock and
 * the two surfaces (ADR 0003, Amendment 3).
 *
 * Every per-frame value (the Stream's slide, Home's fade) is read inside a graphics layer from [frame], so a pull
 * redraws without recomposing; composition only reads what changes at a pull's start and end ([isTransitioning]).
 */
@Suppress("LongParameterList")
internal class HomeDockPullBinding(
    /** For the destination's root: measures the window, carries the keyboard shortcuts and accessibility actions. */
    val rootModifier: Modifier,
    /** For the Dock body: the pull gesture and the accessibility actions. */
    val dockModifier: Modifier,
    private val dockPull: DockPullState,
    /**
     * The Dock's trampoline and fan (ADR 0003, Amendment 3, Motion): the press's dip and Home's first fade, the bounce
     * on release, the cards fanning out and the closing played backwards. It drives Home, the Stream's cards and the
     * Dock's dip while it runs; the pull's own slide drives them otherwise (an expanded posture, the Library).
     */
    private val motion: DockMotionState,
    /** The press, for the gesture; the motion draws from it through callbacks. */
    val press: DockPressState,
    /** The selection while a press is choosing: the tiles, the row and the label read it. */
    val choice: DockChoiceState,
    private val frame: State<StreamPullFrame>,
    /** The direction the Dock is pulled in, away from its edge. */
    private val direction: DockPullDirection,
    /** What Home is drawn at once no pull runs: 0 where a committed switch left the Stream over it, else 1. */
    private val homeAlphaAtRest: () -> Float = { 1f },
    /** What the Stream is drawn at once no pull runs: 1 where it is shown, else 0. */
    private val streamAlphaAtRest: () -> Float = { 1f },
) {
    /** Whether the Stream is, or is on its way to being, shown: a committed opening counts. */
    private fun streamShownNow(): Boolean = dockPull.pendingStreamShown ?: (streamAlphaAtRest() > 0f)

    /** Whether the Stream is being pulled in or out (or is settling after it): both surfaces are composed then. */
    val isTransitioning: Boolean
        get() = dockPull.streamRunning

    /** Whether the Library's sheet is being pulled up (or is settling after it): it is composed then. */
    val isLibraryTransitioning: Boolean
        get() = dockPull.libraryRunning

    /**
     * Whether the trampoline needs Home composed: anything is under way except a press from the open Stream, where
     * Home is long gone and only the dock dips.
     */
    val motionShowsHome: Boolean
        get() = motion.running && !motion.pressingFromOpen

    /**
     * Whether a press from Home is choosing: the Dock shows the contextual row early (its tiles come from the Stream's
     * pages, so the Stream is composed, unseen) while Home is dimmed further.
     */
    val isChoosingFromHome: Boolean
        get() = choice.choosing && !choice.fromContextual

    /** How far the dock is pressed toward its edge right now, in dp. Read it in a graphics layer. */
    fun dockDipDp(): Float = if (motion.running) motion.dip else 0f

    /** What the Stream's cards draw their fan from. */
    val fanSource: DockFanSource =
        object : DockFanSource {
            override fun frame(index: Int): FanFrame? = motion.fanFrame(index)

            override val pull: DockPullDirection get() = direction
        }

    /**
     * How far the Library's sheet still is from fully open while a pull runs, 0 (open) to 1 (away at the Dock's edge),
     * or null when no pull to the Library runs. Read it in a graphics layer so a pull redraws without recomposing.
     */
    fun libraryAwayFraction(): Float? = if (dockPull.libraryRunning) 1f - dockPull.progress.coerceIn(0f, 1f) else null

    /**
     * For the Home surface: it fades as the Stream comes in, and back in as it leaves; it fades out as the Library's
     * sheet rises and stays out while that covers it ([libraryShown]). Identity at rest.
     */
    fun homeModifier(libraryShown: Boolean = false): Modifier =
        Modifier
            .testTag(HOME_SURFACE_TEST_TAG)
            .semantics { drawnSurface = DrawnSurface(homeAlpha(libraryShown), 0f) }
            .graphicsLayer {
                alpha = homeAlpha(libraryShown)
                val scale = homeScale(libraryShown)
                scaleX = scale
                scaleY = scale
            }

    /**
     * For the Stream: while the trampoline runs it is drawn in place and its cards fan themselves ([fanSource]); a
     * pull slides it in from the Dock's edge (an expanded posture's settle). Under the Library's sheet it fades out
     * like Home does.
     */
    fun streamModifier(libraryShown: Boolean = false): Modifier =
        Modifier
            .testTag(STREAM_PULL_SURFACE_TEST_TAG)
            .semantics { drawnSurface = drawnStream(libraryShown) }
            .graphicsLayer {
                if (dockPull.streamRunning && !dockPull.splitting) {
                    val travel = streamTravel()
                    translationX = dockPull.direction.unitX * travel * size.width
                    translationY = dockPull.direction.unitY * travel * size.height
                }
                alpha = streamAlpha(libraryShown)
            }

    /**
     * What Home is drawn at right now. Read it in a draw block or a semantics block.
     *
     * Once the pull is over, Home is drawn as it rests: gone behind the Stream when that is where the switch ended,
     * else fully shown. It is not left at full opacity for the frame between the pull ending and the composition
     * catching up (that frame showed the app grid flashing over the Stream).
     */
    private fun homeAlpha(libraryShown: Boolean): Float =
        when {
            dockPull.streamRunning && !dockPull.splitting -> frame.value.homeAlpha
            dockPull.libraryRunning -> 1f - dockPull.progress.coerceIn(0f, 1f)
            libraryShown -> 0f
            else -> motion.homeFrame()?.alpha ?: homeAlphaAtRest()
        }

    /** Home's size: it scales down a little as the trampoline fades it, and is full size otherwise. */
    private fun homeScale(libraryShown: Boolean): Float =
        if (libraryShown || dockPull.streamRunning || dockPull.libraryRunning) 1f else motion.homeFrame()?.scale ?: 1f

    /** What the Stream is drawn at right now: the mirror of [homeAlpha], so neither flashes as the pull ends. */
    private fun streamAlpha(libraryShown: Boolean): Float =
        when {
            dockPull.streamRunning && !dockPull.splitting -> frame.value.streamAlpha
            dockPull.libraryRunning -> 1f - dockPull.progress.coerceIn(0f, 1f)
            libraryShown -> 0f
            // Composed only so the Dock has the Stream's pages to draw its tiles from: nothing of it shows.
            isChoosingFromHome && !streamShownNow() -> 0f
            // The cards, or the page as one piece, fade themselves; the surface under them stays.
            motion.running -> 1f
            else -> streamAlphaAtRest()
        }

    /** What the Stream draws, for tests: under the trampoline, how far its front card has come out. */
    private fun drawnStream(libraryShown: Boolean): DrawnSurface {
        val front = if (libraryShown) null else motion.fanFrame(0)
        return if (front == null) {
            DrawnSurface(streamAlpha(libraryShown), streamTravel())
        } else {
            DrawnSurface(front.alpha, front.rise)
        }
    }

    /** How far the Stream is from rest along the pull axis, as a fraction of the window (0 at rest). */
    private fun streamTravel(): Float =
        if (dockPull.streamRunning && !dockPull.splitting) frame.value.streamOffsetFraction else 0f
}

@Composable
@Suppress("LongParameterList")
internal fun rememberHomeDockPullBinding(
    state: LauncherShellState,
    dockEdge: DockPosition,
    stream: StreamPullInputs,
    dockPull: DockPullState,
    motion: DockMotionState,
    hostState: HomeDockHostState,
    choice: DockChoiceState,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
): HomeDockPullBinding {
    val rootSize = remember { mutableStateOf(IntSize.Zero) }
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val direction = dockEdge.pullDirection
    // No pull while the layout is being edited (long-press + drag moves dock items then) or a widget is being placed.
    // The Library is already open then: it closes by Back, Home, a pull down on its header or its Close action.
    val canPull =
        state.homeLayout.editMode == HomeEditMode.Browsing && !state.isWidgetPickerOpen && !state.destination.isLibrary
    val latestStream = rememberUpdatedState(stream)
    val press = remember { DockPressState() }
    val diagnostics = remember { mutableStateOf(DockPressDiagnostics()) }
    val dockCoordinates = remember { DockCoordinates() }
    BindPullCallbacks(
        dockPull = dockPull,
        motion = motion,
        press = press,
        choice = choice,
        diagnostics = diagnostics,
        latestOnAction = rememberUpdatedState(onAction),
        latestStream = latestStream,
        reducedMotion = reducedMotion,
    )
    BindChoice(choice, haptics, direction, hostState, dockCoordinates)
    val frameState =
        remember(dockPull, reducedMotion) {
            derivedStateOf { streamPullFrame(dockPull.progress, dockPull.opening, reducedMotion) }
        }
    val host =
        rememberDockPullGestureHost(
            dockPull = dockPull,
            canPull = canPull,
            reducedMotion = reducedMotion,
            rootSize = rootSize,
            direction = direction,
            stream = stream,
            press = press,
            motion = motion,
            hostState = hostState,
            dockCoordinates = dockCoordinates,
        )
    val actions = dockPullActions(dockPull, motion, canPull, stream, direction, reducedMotion)
    val dockModifier =
        Modifier
            .testTag(HOME_DOCK_PULL_TEST_TAG)
            .semantics {
                dockDip = if (motion.running) motion.dip else 0f
                pressDiagnostics = diagnostics.value
            }
            .then(actions.modifier)
            .onGloballyPositioned { coordinates -> dockCoordinates.coordinates = coordinates }
            .dockPullInput(direction, host)
    val rootModifier =
        Modifier
            // The surface root carries the actions too, so they exist when a workspace hides the Dock.
            .then(actions.modifier)
            .onSizeChanged { size -> rootSize.value = size }
            .onPreviewKeyEvent { event ->
                val isShortcut =
                    event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == direction.shortcutKey
                isShortcut && canPull && actions.openFromShortcut(libraryInstead = event.isShiftPressed)
            }
    return HomeDockPullBinding(
        rootModifier = rootModifier,
        dockModifier = dockModifier,
        dockPull = dockPull,
        motion = motion,
        press = press,
        choice = choice,
        frame = frameState,
        direction = direction,
        homeAlphaAtRest = { restAlpha(dockPull, latestStream.value, streamSurface = false) },
        streamAlphaAtRest = { restAlpha(dockPull, latestStream.value, streamSurface = true) },
    )
}

/**
 * What a surface is drawn at once no pull runs: Home gone behind the Stream and the Stream fully shown where a switch
 * to the Stream ended (a committed one counts at once), the other way round where it ended at Home. An expanded
 * posture rests in a split or Stream-only layout the split panes draw themselves, so both stay at 1 there.
 */
private fun restAlpha(
    dockPull: DockPullState,
    stream: StreamPullInputs,
    streamSurface: Boolean,
): Float {
    if (stream.split != null) return 1f
    val streamShown = dockPull.pendingStreamShown ?: stream.shown
    return if (streamShown == streamSurface) 1f else 0f
}

/**
 * Connects the pull's, the press's and the trampoline's ends to the shell: a commit sends its action, a spring-back
 * drops the landing it asked for, a press is drawn by the [motion] (the dip and Home's first fade) unless an expanded
 * posture settles it by the split, a committed press hands the entry it started on to the host, which opens or closes
 * the Stream, and a press that stopped short only bounces back. The motion tells the shell when an opening starts and
 * a closing has finished.
 */
@Composable
@Suppress("LongParameterList")
private fun BindPullCallbacks(
    dockPull: DockPullState,
    motion: DockMotionState,
    press: DockPressState,
    choice: DockChoiceState,
    diagnostics: MutableState<DockPressDiagnostics>,
    latestOnAction: State<(LauncherShellAction) -> Unit>,
    latestStream: State<StreamPullInputs>,
    reducedMotion: Boolean,
) {
    val latestReduced = rememberUpdatedState(reducedMotion)
    SideEffect {
        // An expanded posture settles a press by the split (the pull's own settle); there the press is not drawn.
        press.onBegan = {
            choice.prepare(fromContextual = latestStream.value.shown)
            diagnostics.value =
                DockPressDiagnostics(
                    pressesClaimed = diagnostics.value.pressesClaimed + 1,
                    fromContextual = latestStream.value.shown,
                    startedOnKey = press.startedOnEntryKey,
                    landingNearKey = press.startedNearKey,
                    landingEntryX = choice.entryX(press.startedNearKey),
                )
            if (latestStream.value.split == null) {
                motion.beginPress(fromOpen = latestStream.value.shown, reducedMotion = latestReduced.value)
            }
        }
        press.onMoved = { travelDp -> motion.updatePress(travelDp) }
        // Hold to choose: the Dock and Home hold where they are, Home dims further, and the selection takes over.
        press.onChoosingBegan = { position ->
            motion.beginChoosing()
            choice.begin(position)
        }
        press.onChoosingMoved = { position -> choice.moved(position) }
        press.onReleased = { release ->
            val outcome =
                DockChoice.outcome(
                    fromContextual = choice.fromContextual,
                    committed = release.committed,
                    choosing = release.choosing,
                    selectedKey = choice.selectedKey,
                    cancelling = choice.cancelling,
                    startedOnEntryKey = release.startedOnEntryKey,
                    startedNearKey = release.startedNearKey,
                )
            diagnostics.value =
                diagnostics.value.copy(
                    choosingTrigger = press.choosingTrigger,
                    choosingAtTravelDp = press.choosingTravelDp,
                    choosingAtAlongDp = press.choosingAlongDp,
                    lifted = true,
                    selectedKeyAtLift = choice.selectedKey,
                    cancellingAtLift = choice.cancelling,
                    committedAtLift = release.committed,
                    travelDpAtLift = release.travelDp,
                    entryXAtLift = choice.entryX(diagnostics.value.landingNearKey),
                    outcome = outcome.toString(),
                )
            choice.end()
            motion.endChoosing()
            when (outcome) {
                DockPressOutcome.Revert -> motion.revert()
                is DockPressOutcome.OpenPage -> latestStream.value.onPress(outcome.entryKey)
                DockPressOutcome.Close -> latestStream.value.onPress(null)
                is DockPressOutcome.SwitchPage -> {
                    // The cards stay out: the Dock springs back and the Stream lands on the chosen page.
                    motion.revert()
                    latestStream.value.onSwitchPage(outcome.entryKey)
                }
            }
        }
        motion.onOpening = {
            dockPull.pendingStreamShown = true
            latestOnAction.value(LauncherShellAction.OpenStream)
        }
        motion.onClosed = {
            dockPull.pendingStreamShown = false
            latestOnAction.value(LauncherShellAction.OpenHome)
        }
        dockPull.onCommitted = { destination ->
            latestOnAction.value(
                when (destination) {
                    DockPullDestination.LIBRARY -> LauncherShellAction.OpenAppDrawer
                    DockPullDestination.STREAM -> LauncherShellAction.OpenStream
                    DockPullDestination.HOME -> LauncherShellAction.OpenHome
                },
            )
        }
        // A pull that sprang back must not leave its landing behind for the next time the Stream opens.
        dockPull.onCancelled = { latestStream.value.focus?.cancel() }
    }
}

/**
 * Gives the selection its place: where the Dock and its entries are, the light tick of each move, and the frames that
 * scroll the row and keep the selection under a finger that holds still while the row moves.
 */
@Composable
private fun BindChoice(
    choice: DockChoiceState,
    haptics: LauncherHaptics,
    direction: DockPullDirection,
    hostState: HomeDockHostState,
    dockCoordinates: DockCoordinates,
) {
    val density = LocalDensity.current.density
    val latestHaptics = rememberUpdatedState(haptics)
    SideEffect {
        choice.environment =
            DockChoiceState.Environment(
                toRoot = { position -> dockCoordinates.coordinates?.localToRoot(position) ?: position },
                entryBounds = { hostState.dynamicEntryBounds },
                dockBounds = { hostState.bounds.value },
                pull = direction,
                density = density,
            )
        // A light tick (the haptics setting applies: off sounds nothing): choosing began, or another item landed.
        choice.onTick = { latestHaptics.value.perform(LauncherHapticEvent.DETENT) }
    }
    DockChoiceFrames(choice)
}

/** Where the Dock body is laid out, so a touch in its own coordinates can be placed against the dynamic section. */
private class DockCoordinates {
    var coordinates: LayoutCoordinates? = null
}

/**
 * The Dock's gesture equivalents: "Open Library" for the pull, and "Show live pages" or "Back to Home" for the press;
 * and the same on the keyboard.
 */
private class DockPullActions(
    val modifier: Modifier,
    private val open: (DockPullDestination) -> Boolean,
    private val streamDestination: DockPullDestination?,
) {
    /** Ctrl + the pull arrow opens the Stream (or returns Home from it); with Shift, the Library. */
    fun openFromShortcut(libraryInstead: Boolean): Boolean =
        when {
            libraryInstead || streamDestination == null -> open(DockPullDestination.LIBRARY)
            else -> open(streamDestination)
        }
}

@Composable
private fun dockPullActions(
    dockPull: DockPullState,
    motion: DockMotionState,
    canPull: Boolean,
    stream: StreamPullInputs,
    direction: DockPullDirection,
    reducedMotion: Boolean,
): DockPullActions {
    val libraryLabel = stringResource(R.string.dock_open_library_action)
    val streamLabel =
        stringResource(if (stream.shown) R.string.stream_dock_close_action else R.string.stream_dock_open_action)
    val streamDestination =
        if (stream.available) {
            if (stream.shown) DockPullDestination.HOME else DockPullDestination.STREAM
        } else {
            null
        }
    val open: (DockPullDestination) -> Boolean = { destination ->
        val split = stream.split?.invoke()
        when {
            // The trampoline: the same change as the press, without a finger.
            destination != DockPullDestination.LIBRARY && split == null -> {
                motion.run(open = destination == DockPullDestination.STREAM, reducedMotion, shown = stream.shown)
                true
            }
            else -> {
                motion.finishNow()
                dockPull.open(destination, direction, reducedMotion, split)
            }
        }
    }
    // One customActions list: two semantics modifiers on a node do not merge their lists, the later one is dropped.
    val modifier =
        if (canPull) {
            Modifier.semantics {
                customActions =
                    listOfNotNull(
                        CustomAccessibilityAction(libraryLabel) { open(DockPullDestination.LIBRARY) }
                            .takeIf { canPull },
                        streamDestination?.takeIf { canPull }?.let { destination ->
                            CustomAccessibilityAction(streamLabel) { open(destination) }
                        },
                    )
            }
        } else {
            Modifier
        }
    return DockPullActions(modifier, open, streamDestination)
}

@Composable
@Suppress("LongParameterList")
private fun rememberDockPullGestureHost(
    dockPull: DockPullState,
    canPull: Boolean,
    reducedMotion: Boolean,
    rootSize: State<IntSize>,
    direction: DockPullDirection,
    stream: StreamPullInputs,
    press: DockPressState,
    motion: DockMotionState,
    hostState: HomeDockHostState,
    dockCoordinates: DockCoordinates,
): DockPullGestureHost {
    val latestCanPull = rememberUpdatedState(canPull)
    val latestReducedMotion = rememberUpdatedState(reducedMotion)
    val latestDensity = rememberUpdatedState(LocalDensity.current.density)
    val latestStream = rememberUpdatedState(stream)
    val latestDirection = rememberUpdatedState(direction)
    return remember(dockPull, motion, rootSize, hostState, dockCoordinates) {
        object : DockPullGestureHost {
            override val state: DockPullState = dockPull

            override val canPull: Boolean
                get() =
                    latestCanPull.value && dockPull.pendingStreamShown == null && !dockPull.pendingLibraryShown &&
                        rootSize.value != IntSize.Zero

            override val reducedMotion: Boolean
                get() = latestReducedMotion.value

            override fun travelDp(direction: DockPullDirection): Float {
                val window = rootSize.value
                val extentPx = if (direction.unitY != 0f) window.height else window.width
                return extentPx / latestDensity.value
            }

            override fun splitSetup(): SplitPullSetup? = latestStream.value.split?.invoke()

            override val press: DockPressState = press

            override fun settleMotion() = motion.finishNow()

            override val canPress: Boolean
                get() = latestStream.value.available && this.canPull

            // Choosing draws the Dock's early extension and Home's dimming: folded phone only, like the fan.
            override val canChoose: Boolean
                get() = latestStream.value.split == null

            override val holdMillis: Long
                get() = motion.tuning.holdMillis.toLong()

            override val holdChooses: Boolean
                get() = !latestStream.value.shown

            override fun pressEntryAt(position: Offset): String? {
                val inRoot = dockCoordinates.coordinates?.localToRoot(position) ?: position
                val alongRun = latestDirection.value.unitY != 0f
                val entries =
                    hostState.dynamicEntryBounds.map { (key, bounds) ->
                        DockPullEntryExtent(
                            key = key,
                            start = if (alongRun) bounds.left else bounds.top,
                            end = if (alongRun) bounds.right else bounds.bottom,
                        )
                    }
                // Only along the run: the section is as thick as its tiles, and a touch on it is on a tile or a gap.
                return dockPullEntryAt(if (alongRun) inRoot.x else inRoot.y, entries)
            }

            override fun pressNearestSelectableAt(position: Offset): String? {
                val inRoot = dockCoordinates.coordinates?.localToRoot(position) ?: position
                val alongRun = latestDirection.value.unitY != 0f
                return DockChoice.nearestSelectable(
                    positionPx = if (alongRun) inRoot.x else inRoot.y,
                    items =
                        hostState.dynamicEntryBounds.map { (key, bounds) ->
                            DockChoiceItem(
                                key = key,
                                startPx = if (alongRun) bounds.left else bounds.top,
                                endPx = if (alongRun) bounds.right else bounds.bottom,
                            )
                        },
                )
            }
        }
    }
}

/** What a surface is drawn at, for tests: its opacity and how far it is from rest (fraction of the window). */
internal data class DrawnSurface(val alpha: Float, val travelFraction: Float)

internal val DrawnSurfaceKey = SemanticsPropertyKey<DrawnSurface>("DrawnSurface")
private var SemanticsPropertyReceiver.drawnSurface by DrawnSurfaceKey

/** How far the Dock is pressed toward its edge right now, in dp, for tests (negative while it has sprung past rest). */
private var SemanticsPropertyReceiver.pressDiagnostics by DockPressDiagnosticsKey

internal val DockDipKey = SemanticsPropertyKey<Float>("DockDip")
private var SemanticsPropertyReceiver.dockDip by DockDipKey

internal const val HOME_SURFACE_TEST_TAG = "home-surface"
internal const val STREAM_PULL_SURFACE_TEST_TAG = "stream-pull-surface"
