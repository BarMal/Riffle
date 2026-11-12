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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.riffle.app.R
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.dockpull.DockPullDestination
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.dockpull.StreamPullFrame
import com.riffle.core.domain.launcher.dockpull.dockPullDestinationFor
import com.riffle.core.domain.launcher.dockpull.dockPullSideAt
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
)

/**
 * How [HomeDestination] wires the Dock pull into the Dock and the two surfaces (ADR 0003, The Dock is the handle).
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
    private val frame: State<StreamPullFrame>,
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
        Modifier.graphicsLayer {
            alpha = coveredAlpha(libraryShown, streamAlpha = frame.value.homeAlpha)
        }

    /**
     * For the Stream: it slides in from the Dock's edge (or out toward the interior); reduced motion fades it. Under
     * the Library's sheet it fades out like Home does.
     */
    fun streamModifier(libraryShown: Boolean = false): Modifier =
        Modifier.graphicsLayer {
            if (dockPull.streamRunning && !dockPull.splitting) {
                val current = frame.value
                val direction = dockPull.direction
                translationX = direction.unitX * current.streamOffsetFraction * size.width
                translationY = direction.unitY * current.streamOffsetFraction * size.height
                alpha = current.streamAlpha
            } else {
                alpha = coveredAlpha(libraryShown, streamAlpha = 1f)
            }
        }

    /** What a surface under the Library shows at: [streamAlpha] while a Stream pull runs, else faded by the sheet. */
    private fun coveredAlpha(
        libraryShown: Boolean,
        streamAlpha: Float,
    ): Float =
        when {
            dockPull.streamRunning && !dockPull.splitting -> streamAlpha
            dockPull.libraryRunning -> 1f - dockPull.progress.coerceIn(0f, 1f)
            libraryShown -> 0f
            else -> 1f
        }
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
    /** The workspace menu stays reachable by its accessibility action until P8 removes it; null when there is none. */
    onOpenWorkspaceMenu: (() -> Unit)? = null,
): HomeDockPullBinding {
    val rootSize = remember { mutableStateOf(IntSize.Zero) }
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val direction = dockEdge.pullDirection
    // No pull while the layout is being edited (long-press + drag moves dock items then) or a widget is being placed.
    // The Library is already open then: it closes by Back, Home, a pull down on its header or its Close action.
    val canPull =
        state.homeLayout.editMode == HomeEditMode.Browsing && !state.isWidgetPickerOpen && !state.destination.isLibrary
    val latestOnAction = rememberUpdatedState(onAction)
    SideEffect {
        dockPull.onCommitted = { destination ->
            latestOnAction.value(
                when (destination) {
                    DockPullDestination.LIBRARY -> LauncherShellAction.OpenAppDrawer
                    DockPullDestination.STREAM -> LauncherShellAction.OpenStream
                    DockPullDestination.HOME -> LauncherShellAction.OpenHome
                },
            )
        }
    }
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
            hostState = hostState,
            dockCoordinates = dockCoordinates,
        )
    val actions = dockPullActions(dockPull, canPull, stream, direction, reducedMotion, onOpenWorkspaceMenu)
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
        frame = frameState,
    )
}

/** Where the Dock body is laid out, so a touch in its own coordinates can be placed against the dynamic section. */
private class DockCoordinates {
    var coordinates: LayoutCoordinates? = null
}

/** The Dock's pull equivalents: "Open Library", and "Open Stream" or "Back to Home"; and the same on the keyboard. */
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
    onOpenWorkspaceMenu: (() -> Unit)?,
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
        if (canPull || onOpenWorkspaceMenu != null) {
            Modifier.semantics {
                customActions =
                    listOfNotNull(
                        CustomAccessibilityAction(libraryLabel) { open(DockPullDestination.LIBRARY) }
                            .takeIf { canPull },
                        streamDestination?.takeIf { canPull }?.let { destination ->
                            CustomAccessibilityAction(streamLabel) { open(destination) }
                        },
                        onOpenWorkspaceMenu?.let { onOpen ->
                            CustomAccessibilityAction(WORKSPACE_MENU_ACTION_LABEL) {
                                onOpen()
                                true
                            }
                        },
                    )
            }
        } else {
            Modifier
        }
    return DockPullActions(modifier, open, streamDestination)
}

/** The "Workspace menu" accessibility action, kept reachable until P8 removes the menu; identity when there is none. */
internal fun workspaceMenuActionModifier(onOpen: (() -> Unit)?): Modifier =
    if (onOpen == null) {
        Modifier
    } else {
        Modifier.semantics {
            customActions =
                listOf(
                    CustomAccessibilityAction(WORKSPACE_MENU_ACTION_LABEL) {
                        onOpen()
                        true
                    },
                )
        }
    }

internal const val WORKSPACE_MENU_ACTION_LABEL = "Workspace menu"

@Composable
@Suppress("LongParameterList")
private fun rememberDockPullGestureHost(
    dockPull: DockPullState,
    canPull: Boolean,
    reducedMotion: Boolean,
    rootSize: State<IntSize>,
    direction: DockPullDirection,
    stream: StreamPullInputs,
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

            override fun destinationAt(position: Offset): DockPullDestination {
                val inRoot = dockCoordinates.coordinates?.localToRoot(position) ?: position
                // A vertical pull belongs to a dock that runs along the window's width, and the other way round.
                val alongRun = latestDirection.value.unitY != 0f
                val dock = hostState.bounds.value
                val dynamic = hostState.dynamicBounds.value
                val side =
                    dockPullSideAt(
                        position = if (alongRun) inRoot.x else inRoot.y,
                        dockStart = if (alongRun) dock?.left ?: 0f else dock?.top ?: 0f,
                        dockEnd = if (alongRun) dock?.right ?: 0f else dock?.bottom ?: 0f,
                        dynamicStart = dynamic?.let { if (alongRun) it.left else it.top },
                        dynamicEnd = dynamic?.let { if (alongRun) it.right else it.bottom },
                    )
                return dockPullDestinationFor(side, latestStream.value.shown, latestStream.value.available)
            }
        }
    }
}
