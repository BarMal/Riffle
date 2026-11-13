package com.riffle.app.launcher

import androidx.compose.runtime.Composable
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
     * Called with the key of the dynamic entry the press started on, or null.
     */
    val onPress: (entryKey: String?) -> Unit = {},
)

/**
 * How [HomeDestination] wires the Dock's pull (the Library) and press (Home and the contextual mode) into the Dock and
 * the two surfaces (ADR 0003, Amendment 3).
 *
 * Every per-frame value (the Stream's slide, Home's fade) is read inside a graphics layer from [frame], so a pull
 * redraws without recomposing; composition only reads what changes at a pull's start and end ([isTransitioning]).
 */
internal class HomeDockPullBinding(
    /** For the destination's root: measures the window, carries the keyboard shortcuts and accessibility actions. */
    val rootModifier: Modifier,
    /** For the Dock body: the pull gesture and the accessibility actions. */
    val dockModifier: Modifier,
    private val dockPull: DockPullState,
    /**
     * The Dock's press, for whatever draws it (ADR 0003, Amendment 3, Motion): [DockPressState.progress] runs 0 to 1
     * while
     * the Dock is pressed down and [DockPressState.onReleased] says how it ended.
     */
    val press: DockPressState,
    private val frame: State<StreamPullFrame>,
    /** What Home is drawn at once no pull runs: 0 where a committed switch left the Stream over it, else 1. */
    private val homeAlphaAtRest: () -> Float = { 1f },
    /** What the Stream is drawn at once no pull runs: 1 where it is shown, else 0. */
    private val streamAlphaAtRest: () -> Float = { 1f },
) {
    /** Whether the Stream is being pulled in or out (or is settling after it): both surfaces are composed then. */
    val isTransitioning: Boolean
        get() = dockPull.streamRunning

    /** Whether the Library's sheet is being pulled up (or is settling after it): it is composed then. */
    val isLibraryTransitioning: Boolean
        get() = dockPull.libraryRunning

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
            .graphicsLayer { alpha = homeAlpha(libraryShown) }

    /**
     * For the Stream: it slides in from the Dock's edge (or out toward the interior); reduced motion fades it. Under
     * the Library's sheet it fades out like Home does.
     */
    fun streamModifier(libraryShown: Boolean = false): Modifier =
        Modifier
            .testTag(STREAM_PULL_SURFACE_TEST_TAG)
            .semantics { drawnSurface = DrawnSurface(streamAlpha(libraryShown), streamTravel()) }
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
            else -> homeAlphaAtRest()
        }

    /** What the Stream is drawn at right now: the mirror of [homeAlpha], so neither flashes as the pull ends. */
    private fun streamAlpha(libraryShown: Boolean): Float =
        when {
            dockPull.streamRunning && !dockPull.splitting -> frame.value.streamAlpha
            dockPull.libraryRunning -> 1f - dockPull.progress.coerceIn(0f, 1f)
            libraryShown -> 0f
            else -> streamAlphaAtRest()
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
    hostState: HomeDockHostState,
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
    BindPullCallbacks(dockPull, press, rememberUpdatedState(onAction), latestStream)
    val frameState =
        remember(dockPull, reducedMotion) {
            derivedStateOf { streamPullFrame(dockPull.progress, dockPull.opening, reducedMotion) }
        }
    val dockCoordinates = remember { DockCoordinates() }
    val host =
        rememberDockPullGestureHost(
            dockPull = dockPull,
            canPull = canPull,
            reducedMotion = reducedMotion,
            rootSize = rootSize,
            direction = direction,
            stream = stream,
            press = press,
            hostState = hostState,
            dockCoordinates = dockCoordinates,
        )
    val actions = dockPullActions(dockPull, canPull, stream, direction, reducedMotion)
    val dockModifier =
        Modifier
            .testTag(HOME_DOCK_PULL_TEST_TAG)
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
        press = press,
        frame = frameState,
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
 * Connects the pull's and the press's end to the shell: a commit sends its action, a spring-back drops the landing it
 * asked for, and a committed press hands the entry it started on to the host, which toggles the mode.
 */
@Composable
private fun BindPullCallbacks(
    dockPull: DockPullState,
    press: DockPressState,
    latestOnAction: State<(LauncherShellAction) -> Unit>,
    latestStream: State<StreamPullInputs>,
) {
    SideEffect {
        press.onReleased = { release ->
            if (release.committed) latestStream.value.onPress(release.startedOnEntryKey)
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
        dockPull.open(destination, direction, reducedMotion, stream.split?.invoke())
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
    hostState: HomeDockHostState,
    dockCoordinates: DockCoordinates,
): DockPullGestureHost {
    val latestCanPull = rememberUpdatedState(canPull)
    val latestReducedMotion = rememberUpdatedState(reducedMotion)
    val latestDensity = rememberUpdatedState(LocalDensity.current.density)
    val latestStream = rememberUpdatedState(stream)
    val latestDirection = rememberUpdatedState(direction)
    return remember(dockPull, rootSize, hostState, dockCoordinates) {
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

            override val canPress: Boolean
                get() = latestStream.value.available && this.canPull

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
        }
    }
}

/** What a surface is drawn at, for tests: its opacity and how far it is from rest (fraction of the window). */
internal data class DrawnSurface(val alpha: Float, val travelFraction: Float)

internal val DrawnSurfaceKey = SemanticsPropertyKey<DrawnSurface>("DrawnSurface")
private var SemanticsPropertyReceiver.drawnSurface by DrawnSurfaceKey

internal const val HOME_SURFACE_TEST_TAG = "home-surface"
internal const val STREAM_PULL_SURFACE_TEST_TAG = "stream-pull-surface"
