package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.core.domain.launcher.dockpull.DockPullClaim
import com.riffle.core.domain.launcher.dockpull.DockPullDestination
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.dockpull.DockPullDrag
import com.riffle.core.domain.launcher.dockpull.SettleOutcome
import com.riffle.core.domain.launcher.dockpull.dockPullClaimFor
import com.riffle.core.domain.launcher.split.SplitMode
import com.riffle.core.domain.launcher.split.SplitModes
import com.riffle.core.domain.launcher.split.SplitReveal
import com.riffle.core.domain.launcher.split.SplitSettle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What an expanded posture's pull needs to know where it starts (ADR 0003, Folded and unfolded): the mode it starts
 * from, the last-used shown mode a Stream button settles at, and whether the screen has room for a split at all.
 */
internal class SplitPullSetup(
    val startMode: SplitMode,
    val lastShown: SplitMode,
    val splitAvailable: Boolean,
)

/**
 * The UI side of the Dock pull (ADR 0003, The Dock is the handle): holds a [DockPullDrag] while the finger is down,
 * animates the settle after it and reports a committed destination through [onCommitted].
 *
 * Nothing here decides anything: which side a pull started on, what it opens, whether a release commits and how the
 * Stream and Home are drawn along the way are the domain's (`dockpull/`). A pull that opens the Library raises its
 * sheet from the Dock's edge and one that opens or closes the Stream slides it; both track the finger 1:1 ([progress]),
 * can be caught again mid-settle, and commit by travel or velocity ([libraryRunning], [streamRunning] tell them
 * apart).
 */
@Stable
internal class DockPullState(
    private val scope: CoroutineScope,
) {
    private var settleJob: Job? = null
    private var drag: DockPullDrag? = null
    private var destination: DockPullDestination by mutableStateOf(DockPullDestination.LIBRARY)
    private var reducedMotion: Boolean = false
    private var settledTravelDp: Float = 1f
    private var split: SplitPullSetup? = null

    /** Whether a pull is under way or settling after it. Changes at a pull's start and end. */
    var running: Boolean by mutableStateOf(false)
        private set

    /** Whether the Library's sheet is being pulled up, or is settling after it. */
    val libraryRunning: Boolean
        get() = running && destination == DockPullDestination.LIBRARY

    /** Whether the Stream is being pulled in or out, or is settling after it. */
    val streamRunning: Boolean
        get() = running && destination != DockPullDestination.LIBRARY

    /** How far the Stream and Home have moved from one to the other, 0..1. Read it in draw blocks, not composition. */
    var progress: Float by mutableFloatStateOf(0f)
        private set

    /** Whether the running pull opens the Stream (true) or returns to Home from it (false). */
    var opening: Boolean by mutableStateOf(true)
        private set

    /** The direction the running pull moves in: away from the Dock's edge. */
    var direction: DockPullDirection by mutableStateOf(DockPullDirection.UP)
        private set

    /** Whether the running pull moves between Home only, a split and Stream only (an expanded posture). */
    var splitting: Boolean by mutableStateOf(false)
        private set

    /**
     * How much Stream a splitting pull shows, 0 (Home only) to 1 (Stream only), a split at half: the pull's progress
     * mapped through where it started. Read it in layout and draw blocks, not composition.
     */
    val reveal: Float
        get() = SplitReveal.reveal(progress, splitStartReveal, opening)

    private val splitStartReveal: Float
        get() = split?.let { SplitSettle.anchorOf(it.startMode) } ?: 0f

    /** Called, before a splitting pull's surfaces rest, with the mode it settled in (even when it did not change). */
    var onSplitSettled: (SplitMode) -> Unit = {}

    /**
     * A committed switch the shell has not shown yet: the Stream (true) or Home (false). While it is set it stands in
     * for the shell's own answer, so the surfaces do not flicker back for the frame or two in between.
     */
    var pendingStreamShown: Boolean? by mutableStateOf(null)

    /** A committed pull to the Library the shell has not shown yet, so the sheet does not drop away for a frame. */
    var pendingLibraryShown: Boolean by mutableStateOf(false)

    /** Called when a pull commits, with where it leads; the host dispatches the shell action. */
    var onCommitted: (DockPullDestination) -> Unit = {}

    /** Called when a pull ends without committing (it sprang back), so what it prepared can be dropped. */
    var onCancelled: () -> Unit = {}

    /**
     * A finger landed on a settling pull: catch it where it is and track from there. Returns whether there was a
     * settle to catch.
     */
    fun catchSettle(): Boolean {
        val job = settleJob?.takeIf { it.isActive && running } ?: return false
        job.cancel()
        drag = DockPullDrag(direction, settledTravelDp, startProgress = progress)
        return true
    }

    /** A fresh pull toward [destination], [travelDp] long, in [direction]. */
    fun start(
        destination: DockPullDestination,
        direction: DockPullDirection,
        travelDp: Float,
        reducedMotion: Boolean,
        split: SplitPullSetup? = null,
    ) {
        settleJob?.cancel()
        // Going to the Stream or Home supersedes a Library the shell has not shown yet.
        if (destination != DockPullDestination.LIBRARY) pendingLibraryShown = false
        this.split = split.takeIf { destination != DockPullDestination.LIBRARY }
        splitting = this.split != null
        this.destination = destination
        this.reducedMotion = reducedMotion
        this.direction = direction
        settledTravelDp = travelDp
        drag = DockPullDrag(direction, travelDp)
        opening = destination != DockPullDestination.HOME
        progress = 0f
        running = true
    }

    fun drag(
        dxDp: Float,
        dyDp: Float,
    ) {
        val current = drag ?: return
        val next = current.dragged(dxDp, dyDp)
        drag = next
        if (running) progress = next.progress
    }

    fun release(
        velocityXDpPerSecond: Float,
        velocityYDpPerSecond: Float,
    ) {
        val current = drag ?: return
        drag = null
        val setup = split
        if (setup == null || !running) {
            settle(current.release(velocityXDpPerSecond, velocityYDpPerSecond))
            return
        }
        // An expanded posture settles to the nearest of the three states by where it stopped and how fast it was going.
        val startReveal = SplitSettle.anchorOf(setup.startMode)
        val progressVelocity = current.direction.along(velocityXDpPerSecond, velocityYDpPerSecond) / current.travelDp
        val mode =
            SplitSettle.settlePull(
                reveal = SplitReveal.reveal(current.progress, startReveal, opening),
                revealVelocityPerSecond = SplitReveal.revealVelocity(progressVelocity, startReveal, opening),
                gestureDistanceDp = current.gestureDistanceDp,
                startMode = setup.startMode,
                splitAvailable = setup.splitAvailable,
            )
        settleSplit(setup, mode)
    }

    /** The gesture was taken away: spring back. */
    fun cancel() {
        if (drag == null) return
        drag = null
        val setup = split
        if (setup != null && running) settleSplit(setup, setup.startMode) else settle(SettleOutcome.CANCEL)
    }

    /**
     * The pull's accessibility, keyboard and button equivalents: the same commit, run as a settle from rest. Ignored
     * while a pull or a committed switch is already under way.
     */
    fun open(
        destination: DockPullDestination,
        direction: DockPullDirection,
        reducedMotion: Boolean,
        split: SplitPullSetup? = null,
    ): Boolean {
        val free = !running && drag == null && pendingStreamShown == null
        if (free) {
            // The Library never splits: it is a sheet over whatever shows.
            val setup = split.takeIf { destination != DockPullDestination.LIBRARY }
            start(destination, direction, travelDp = 1f, reducedMotion = reducedMotion, split = setup)
            drag = null
            if (setup == null) {
                settle(SettleOutcome.COMMIT)
            } else {
                val mode =
                    if (destination == DockPullDestination.STREAM) {
                        SplitModes.shownMode(setup.lastShown, setup.splitAvailable)
                    } else {
                        SplitMode.HOME_ONLY
                    }
                settleSplit(setup, mode)
            }
        }
        return free
    }

    /** Animates a splitting pull to the reveal that [mode] rests at, then tells the host where it ended. */
    private fun settleSplit(
        setup: SplitPullSetup,
        mode: SplitMode,
    ) {
        val startReveal = SplitSettle.anchorOf(setup.startMode)
        val target = SplitReveal.progressFor(SplitSettle.anchorOf(mode), startReveal, opening)
        val shownChanges = mode.showsStream != setup.startMode.showsStream
        settleJob?.cancel()
        settleJob =
            scope.launch {
                animateProgressTo(target)
                onSplitSettled(mode)
                if (shownChanges) pendingStreamShown = mode.showsStream
                running = false
                splitting = false
                progress = 0f
                if (shownChanges) {
                    onCommitted(if (mode.showsStream) DockPullDestination.STREAM else DockPullDestination.HOME)
                } else {
                    onCancelled()
                }
            }
    }

    /** The settle: the smooth spring, or under reduced motion an instant cut to the end (no frame in between). */
    private suspend fun animateProgressTo(target: Float) {
        if (reducedMotion) {
            progress = target
        } else {
            Animatable(progress).animateTo(target, animationSpec = dockPullSettleSpec()) { progress = value }
        }
    }

    private fun settle(outcome: SettleOutcome) {
        if (!running) return
        val target = if (outcome == SettleOutcome.COMMIT) 1f else 0f
        settleJob?.cancel()
        settleJob =
            scope.launch {
                animateProgressTo(target)
                if (outcome == SettleOutcome.COMMIT) {
                    if (destination == DockPullDestination.LIBRARY) {
                        pendingLibraryShown = true
                    } else {
                        pendingStreamShown = destination == DockPullDestination.STREAM
                    }
                }
                running = false
                splitting = false
                progress = 0f
                if (outcome == SettleOutcome.COMMIT) onCommitted(destination) else onCancelled()
            }
    }
}

@Composable
internal fun rememberDockPullState(): DockPullState {
    val scope = rememberCoroutineScope()
    return remember(scope) { DockPullState(scope) }
}

/** The animated settle: the smooth spring. (Under reduced motion there is no animation: [DockPullState] cuts.) */
private fun dockPullSettleSpec(): AnimationSpec<Float> =
    RiffleMotion.smooth(visibilityThreshold = DOCK_PULL_PROGRESS_THRESHOLD)

/** What the Dock pull gesture needs from its host, read fresh at each gesture. */
internal interface DockPullGestureHost {
    val state: DockPullState

    /** Whether a pull may start now (browsing, nothing pending, the window measured). */
    val canPull: Boolean

    val reducedMotion: Boolean

    /** The pull's full travel in dp: the window's extent along [direction]'s axis. */
    fun travelDp(direction: DockPullDirection): Float

    /** Where a pull that landed at [position] (in the gesture's own coordinates) leads. */
    fun destinationAt(position: Offset): DockPullDestination

    /**
     * A pull toward [destination] has just been claimed; it landed at [position] (the gesture's own coordinates). The
     * host prepares what the destination needs before the surfaces move, such as where the Stream lands.
     */
    fun pullStarted(
        destination: DockPullDestination,
        position: Offset,
    ) = Unit

    /** The split a pull on an expanded posture moves through, or null where the Stream and Home swap completely. */
    fun splitSetup(): SplitPullSetup? = null
}

/**
 * The Dock pull gesture on the dock body, in the dock edge's natural [direction].
 *
 * On the Main pass, after the dock's own children: a section scroll along the run or an item's long-press drag
 * consumes its touches first and the pull yields. A drag only becomes a pull once it leaves touch slop mostly along
 * [direction] ([dockPullClaimFor]) and before the long-press timeout, so a held press stays a long-press. Where it
 * leads is decided by which side the finger landed on ([DockPullGestureHost.destinationAt]). A finger landing on a
 * settling pull catches it at once. Deltas are handed over in dp; only their component along the pull moves it.
 *
 * Place it before any translation of the dock, so the finger is measured in the dock's resting coordinates rather than
 * ones that move with the finger. This is the one pointer loop the Dock pull needs: it claims by direction, which the
 * platform's drag detectors cannot express (ADR 0002).
 */
internal fun Modifier.dockPullInput(
    direction: DockPullDirection,
    host: DockPullGestureHost,
): Modifier =
    pointerInput(direction, host) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            val caught = host.state.catchSettle()
            if (!caught) {
                if (!host.canPull) return@awaitEachGesture
                val claimed = awaitPullClaim(down, direction, tracker, host)
                if (!claimed) return@awaitEachGesture
            }
            trackPull(down.id, tracker, host.state)
        }
    }

/** Waits for the touch to become a pull (true) or something else (false); starts the pull on a claim. */
private suspend fun AwaitPointerEventScope.awaitPullClaim(
    down: PointerInputChange,
    direction: DockPullDirection,
    tracker: VelocityTracker,
    host: DockPullGestureHost,
): Boolean {
    val touchSlopPx = viewConfiguration.touchSlop
    val claimed =
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var decision: Boolean? = null
            while (decision == null) {
                val change = awaitPointerEvent().changes.firstOrNull { candidate -> candidate.id == down.id }
                if (change == null || !change.pressed || change.isConsumed) {
                    decision = false
                } else {
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val drag = change.position - down.position
                    when (dockPullClaimFor(direction, drag.x, drag.y, touchSlopPx)) {
                        DockPullClaim.UNDECIDED -> Unit
                        DockPullClaim.REJECT -> decision = false
                        DockPullClaim.CLAIM -> {
                            change.consume()
                            val destination = host.destinationAt(down.position)
                            host.pullStarted(destination, down.position)
                            host.state.start(
                                destination = destination,
                                direction = direction,
                                travelDp = host.travelDp(direction),
                                reducedMotion = host.reducedMotion,
                                split = host.splitSetup(),
                            )
                            // From where the finger landed, so the Stream tracks it 1:1 from the start.
                            host.state.drag(drag.x / density, drag.y / density)
                            decision = true
                        }
                    }
                }
            }
            decision
        }
    return claimed == true
}

/** Follows the claimed pointer until it lifts (release) or goes away (cancel). */
private suspend fun AwaitPointerEventScope.trackPull(
    pointerId: PointerId,
    tracker: VelocityTracker,
    state: DockPullState,
) {
    var finished = false
    try {
        while (!finished) {
            val change = awaitPointerEvent().changes.firstOrNull { candidate -> candidate.id == pointerId }
            finished = trackPullStep(change, tracker, state)
        }
    } finally {
        // The gesture was torn down mid-pull (the dock left composition, its edge changed): never leave the pull
        // stranded while tracking.
        if (!finished) state.cancel()
    }
}

/** One event of a tracked pull; true once the pull has ended. */
private fun AwaitPointerEventScope.trackPullStep(
    change: PointerInputChange?,
    tracker: VelocityTracker,
    state: DockPullState,
): Boolean {
    if (change == null) {
        state.cancel()
        return true
    }
    tracker.addPosition(change.uptimeMillis, change.position)
    val delta = change.positionChange()
    change.consume()
    if (delta.x != 0f || delta.y != 0f) state.drag(delta.x / density, delta.y / density)
    if (!change.pressed) {
        val velocity = tracker.calculateVelocity()
        state.release(velocity.x / density, velocity.y / density)
    }
    return !change.pressed
}

/** The arrow key that, with Ctrl, performs the pull in [this] direction. */
internal val DockPullDirection.shortcutKey: Key
    get() =
        when (this) {
            DockPullDirection.UP -> Key.DirectionUp
            DockPullDirection.DOWN -> Key.DirectionDown
            DockPullDirection.LEFT -> Key.DirectionLeft
            DockPullDirection.RIGHT -> Key.DirectionRight
        }

internal const val HOME_DOCK_PULL_TEST_TAG = "home-dock-pull"

private const val DOCK_PULL_PROGRESS_THRESHOLD = 0.001f
