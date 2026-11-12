package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
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
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import com.riffle.core.domain.launcher.dockpull.DockPullClaim
import com.riffle.core.domain.launcher.dockpull.DockPullDestination
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.dockpull.DockPullDrag
import com.riffle.core.domain.launcher.dockpull.SettleOutcome
import com.riffle.core.domain.launcher.dockpull.dockPullClaimFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The UI side of the Dock pull (ADR 0003, The Dock is the handle): holds a [DockPullDrag] while the finger is down,
 * animates the settle after it and reports a committed destination through [onCommitted].
 *
 * Nothing here decides anything: which side a pull started on, what it opens, whether a release commits and how the
 * Stream and Home are drawn along the way are the domain's (`dockpull/`). A pull that opens the Library does not
 * move anything with the finger ([running] stays false); one that opens or closes the Stream tracks the finger 1:1
 * ([progress]), can be caught again mid-settle, and commits by travel or velocity.
 */
@Stable
internal class DockPullState(
    private val scope: CoroutineScope,
) {
    private var settleJob: Job? = null
    private var drag: DockPullDrag? = null
    private var destination: DockPullDestination = DockPullDestination.LIBRARY
    private var reducedMotion: Boolean = false
    private var settledTravelDp: Float = 1f

    /** Whether the Stream is being pulled in or out, or is settling after it. Changes at a pull's start and end. */
    var running: Boolean by mutableStateOf(false)
        private set

    /** How far the Stream and Home have moved from one to the other, 0..1. Read it in draw blocks, not composition. */
    var progress: Float by mutableFloatStateOf(0f)
        private set

    /** Whether the running pull opens the Stream (true) or returns to Home from it (false). */
    var opening: Boolean by mutableStateOf(true)
        private set

    /** The direction the running pull moves in: away from the Dock's edge. */
    var direction: DockPullDirection by mutableStateOf(DockPullDirection.UP)
        private set

    /**
     * A committed switch the shell has not shown yet: the Stream (true) or Home (false). While it is set it stands in
     * for the shell's own answer, so the surfaces do not flicker back for the frame or two in between.
     */
    var pendingStreamShown: Boolean? by mutableStateOf(null)

    /** Called when a pull commits, with where it leads; the host dispatches the shell action. */
    var onCommitted: (DockPullDestination) -> Unit = {}

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
    ) {
        settleJob?.cancel()
        this.destination = destination
        this.reducedMotion = reducedMotion
        this.direction = direction
        settledTravelDp = travelDp
        drag = DockPullDrag(direction, travelDp)
        if (destination != DockPullDestination.LIBRARY) {
            opening = destination == DockPullDestination.STREAM
            progress = 0f
            running = true
        }
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
        settle(current.release(velocityXDpPerSecond, velocityYDpPerSecond))
    }

    /** The gesture was taken away: spring back. */
    fun cancel() {
        if (drag == null) return
        drag = null
        settle(SettleOutcome.CANCEL)
    }

    /**
     * The pull's accessibility, keyboard and button equivalents: the same commit, run as a settle from rest. Ignored
     * while a pull or a committed switch is already under way.
     */
    fun open(
        destination: DockPullDestination,
        direction: DockPullDirection,
        reducedMotion: Boolean,
    ): Boolean {
        val free = !running && drag == null && pendingStreamShown == null
        if (free && destination == DockPullDestination.LIBRARY) {
            onCommitted(destination)
        } else if (free) {
            start(destination, direction, travelDp = 1f, reducedMotion = reducedMotion)
            drag = null
            settle(SettleOutcome.COMMIT)
        }
        return free
    }

    private fun settle(outcome: SettleOutcome) {
        if (!running) {
            // The Library: nothing moved, so there is nothing to animate; the outcome is the whole answer.
            if (outcome == SettleOutcome.COMMIT) onCommitted(destination)
            return
        }
        val target = if (outcome == SettleOutcome.COMMIT) 1f else 0f
        settleJob?.cancel()
        settleJob =
            scope.launch {
                Animatable(progress).animateTo(target, animationSpec = dockPullSettleSpec(reducedMotion)) {
                    progress = value
                }
                if (outcome == SettleOutcome.COMMIT) {
                    pendingStreamShown = destination == DockPullDestination.STREAM
                }
                running = false
                progress = 0f
                if (outcome == SettleOutcome.COMMIT) onCommitted(destination)
            }
    }
}

@Composable
internal fun rememberDockPullState(): DockPullState {
    val scope = rememberCoroutineScope()
    return remember(scope) { DockPullState(scope) }
}

/** The settle: the smooth spring, or under reduced motion a short crossfade to the same end. */
private fun dockPullSettleSpec(reducedMotion: Boolean): AnimationSpec<Float> =
    if (reducedMotion) {
        tween(durationMillis = RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS * 2)
    } else {
        RiffleMotion.smooth(visibilityThreshold = DOCK_PULL_PROGRESS_THRESHOLD)
    }

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
                            host.state.start(
                                destination = host.destinationAt(down.position),
                                direction = direction,
                                travelDp = host.travelDp(direction),
                                reducedMotion = host.reducedMotion,
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
