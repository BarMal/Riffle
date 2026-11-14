@file:Suppress("TooManyFunctions")

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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.core.domain.launcher.dock.DockChoiceStart
import com.riffle.core.domain.launcher.dock.DockPress
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
import kotlin.math.abs

/**
 * What an expanded posture's pull needs to know where it starts (ADR 0003, Folded and unfolded): the mode it starts
 * from, the last-used shown mode a press settles at, and whether the screen has room for a split at all.
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

    /** The press that toggles the Home and the contextual mode (a drag toward the Dock's edge, then release). */
    val press: DockPressState

    /** Whether a press may start now: a Stream to toggle to or from, and a pull may start. */
    val canPress: Boolean

    /** Whether a press may turn into choosing (the folded phone's Dock; an expanded posture settles by the split). */
    val canChoose: Boolean

    /** How long a press is held still past the threshold before it turns into choosing, in milliseconds. */
    val holdMillis: Long

    /**
     * Whether holding still past the threshold turns a press into choosing: from Home (the Stream is not out). With the
     * Stream out a finger that rests is just a slow press that closes it, so only sliding along the run chooses.
     */
    val holdChooses: Boolean

    /** The dynamic entry a touch that landed at [position] (in the gesture's own coordinates) is on, or null. */
    fun pressEntryAt(position: Offset): String?

    /** The selectable Dock item nearest a touch that landed at [position] (the gesture's coordinates), or null. */
    fun pressNearestSelectableAt(position: Offset): String?

    /** A pull is taking over: whatever the trampoline is running goes to its end at once. */
    fun settleMotion() {}

    /** The split a pull on an expanded posture moves through, or null where the Stream and Home swap completely. */
    fun splitSetup(): SplitPullSetup? = null
}

/**
 * The Dock's pull and press gestures on the dock body, in the dock edge's natural pull [direction].
 *
 * On the Main pass, after the dock's own children: a section scroll along the run or an item's long-press drag
 * consumes its touches first and the gesture yields. A drag only becomes one once it leaves touch slop and before the
 * long-press timeout, so a held press stays a long-press: mostly along [direction] it is a pull, which opens the
 * Library
 * ([dockPullClaimFor]); mostly the opposite way, toward the Dock's own edge, it is a press ([DockPress.claimFor]),
 * which
 * toggles the Home and the contextual mode when released past its threshold, and drags along the run are left to the
 * Dock's scroll. A finger landing on a settling pull catches it at once. Deltas are handed over in dp.
 *
 * Place it before any translation of the dock, so the finger is measured in the dock's resting coordinates rather than
 * ones that move with the finger. This is the one pointer loop the Dock needs: it claims by direction, in both
 * directions, which the platform's drag detectors cannot express (ADR 0002). The Dock's row scroll and fling are the
 * platform's own.
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
            if (host.state.catchSettle()) {
                trackPull(down.id, tracker, host.state)
                return@awaitEachGesture
            }
            if (!host.canPull) return@awaitEachGesture
            val claimed = PressSample(down.position, down.uptimeMillis)
            when (awaitClaim(down, direction, tracker, host, claimed)) {
                DockClaim.PULL -> trackPull(down.id, tracker, host.state)
                DockClaim.PRESS -> trackPress(down, direction, host, claimed)
                DockClaim.NONE -> Unit
            }
        }
    }

/** What a touch on the Dock turned out to be. */
private enum class DockClaim { PULL, PRESS, NONE }

/** Where the finger was, and when, at the latest event a press has seen. */
private class PressSample(
    var position: Offset,
    var timeMillis: Long,
)

/**
 * Waits for the touch to become a pull or a press, or something else; starts the pull or the press on a claim. The
 * claiming event is left in [claimed].
 */
private suspend fun AwaitPointerEventScope.awaitClaim(
    down: PointerInputChange,
    direction: DockPullDirection,
    tracker: VelocityTracker,
    host: DockPullGestureHost,
    claimed: PressSample,
): DockClaim {
    val touchSlopPx = viewConfiguration.touchSlop
    val decided =
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var decision: DockClaim? = null
            while (decision == null) {
                // A press is decided ahead of the Dock's children, on the Initial pass: a drag that leads toward the
                // Dock's edge is the press's from the moment it leaves slop, however far it also strays along the
                // run, so the row's scroll (which claims along the run) and a tile's click cannot take it first.
                val early = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (early != null && claimsPress(direction, down, early, host)) {
                    tracker.addPosition(early.uptimeMillis, early.position)
                    early.consume()
                    beginPress(early, down, direction, host, claimed)
                    decision = DockClaim.PRESS
                } else {
                    val change = awaitPointerEvent().changes.firstOrNull { candidate -> candidate.id == down.id }
                    decision = decideOnMain(change, down, direction, tracker, host, claimed, touchSlopPx)
                }
            }
            decision
        }
    return decided ?: DockClaim.NONE
}

/** Whether [change] (the finger's latest place) makes the touch a press: it left slop toward the Dock's edge. */
private fun AwaitPointerEventScope.claimsPress(
    direction: DockPullDirection,
    down: PointerInputChange,
    change: PointerInputChange,
    host: DockPullGestureHost,
): Boolean {
    if (!host.canPress || !change.pressed || change.isConsumed) return false
    val drag = change.position - down.position
    return DockPress.claimFor(direction, drag.x, drag.y, viewConfiguration.touchSlop) == DockPullClaim.CLAIM
}

/** Starts the press at [change]: the host and the press learn where it landed and how far it already went. */
private fun AwaitPointerEventScope.beginPress(
    change: PointerInputChange,
    down: PointerInputChange,
    direction: DockPullDirection,
    host: DockPullGestureHost,
    claimed: PressSample,
) {
    val drag = change.position - down.position
    claimed.position = change.position
    claimed.timeMillis = change.uptimeMillis
    host.press.begin(host.pressEntryAt(down.position), host.pressNearestSelectableAt(down.position))
    host.press.update(DockPress.travelDp(direction, drag.x / density, drag.y / density))
}

/** One event on the Main pass, after the children: a pull is claimed here (a child's own drag wins over it). */
@Suppress("LongParameterList")
private fun AwaitPointerEventScope.decideOnMain(
    change: PointerInputChange?,
    down: PointerInputChange,
    direction: DockPullDirection,
    tracker: VelocityTracker,
    host: DockPullGestureHost,
    claimed: PressSample,
    touchSlopPx: Float,
): DockClaim? {
    if (change == null || !change.pressed || change.isConsumed) return DockClaim.NONE
    tracker.addPosition(change.uptimeMillis, change.position)
    val drag = change.position - down.position
    val decision = claimFor(direction, drag, touchSlopPx, host)
    if (decision == DockClaim.PULL) {
        change.consume()
        host.settleMotion()
        host.state.start(
            destination = DockPullDestination.LIBRARY,
            direction = direction,
            travelDp = host.travelDp(direction),
            reducedMotion = host.reducedMotion,
            split = null,
        )
        // From where the finger landed, so the sheet tracks it 1:1 from the start.
        host.state.drag(drag.x / density, drag.y / density)
    } else if (decision == DockClaim.PRESS) {
        change.consume()
        beginPress(change, down, direction, host, claimed)
    }
    return decision
}

/** What a drag of [drag] px from the landing is so far: undecided (null), a pull, a press, or neither. */
private fun claimFor(
    direction: DockPullDirection,
    drag: Offset,
    touchSlopPx: Float,
    host: DockPullGestureHost,
): DockClaim? {
    val pull = dockPullClaimFor(direction, drag.x, drag.y, touchSlopPx)
    val press =
        if (host.canPress) DockPress.claimFor(direction, drag.x, drag.y, touchSlopPx) else DockPullClaim.REJECT
    return when {
        pull == DockPullClaim.CLAIM -> DockClaim.PULL
        press == DockPullClaim.CLAIM -> DockClaim.PRESS
        pull == DockPullClaim.UNDECIDED -> null
        else -> DockClaim.NONE
    }
}

/**
 * Follows a claimed press until the finger lifts (release, committing when it passed the threshold) or goes away
 * (cancel). Each change is also consumed on the Initial pass, before the Dock's children see it, so a tile the press
 * started on cannot take the release for a tap.
 *
 * Also where a press turns into choosing (ADR 0003, Amendment 3, third depth): every sample goes to [DockChoiceStart],
 * and while the finger rests past the threshold the wait for the next event is bounded by the moment holding starts
 * choosing, so a finger that does not move still gets there. Once choosing, the finger's place is all that is followed.
 */
private suspend fun AwaitPointerEventScope.trackPress(
    down: PointerInputChange,
    direction: DockPullDirection,
    host: DockPullGestureHost,
    claimed: PressSample,
) {
    val press = host.press
    val hold = host.holdMillis
    val timing = ChoiceTiming(hold, host.holdChooses)
    var start = DockChoiceStart()
    var sample = claimed
    var finished = false
    try {
        start = feedChoice(start, sample, down, direction, timing, press, host.canChoose)
        while (!finished) {
            val deadline = start.deadlineMillis(hold)
            val first =
                if (deadline == null) {
                    awaitPointerEvent(PointerEventPass.Initial)
                } else {
                    withTimeoutOrNull((deadline - sample.timeMillis).coerceAtLeast(0L)) {
                        awaitPointerEvent(PointerEventPass.Initial)
                    }
                }
            if (first == null) {
                // The finger rested for the hold time: it is a selection now.
                sample = PressSample(sample.position, requireNotNull(deadline))
                start = feedChoice(start, sample, down, direction, timing, press, host.canChoose)
                continue
            }
            first.changes.firstOrNull { it.id == down.id }?.consume()
            val change = awaitPointerEvent().changes.firstOrNull { candidate -> candidate.id == down.id }
            if (change != null) {
                sample = PressSample(change.position, change.uptimeMillis)
                start = feedChoice(start, sample, down, direction, timing, press, host.canChoose)
            }
            finished = trackPressStep(change, down, direction, press)
        }
    } finally {
        // The gesture was torn down mid-press (the dock left composition): never leave the press stranded.
        if (!finished) press.cancel()
    }
}

/** How long a rest chooses, and whether it does at all (not with the Stream out, where only a slide chooses). */
private class ChoiceTiming(
    val holdMillis: Long,
    val holdStarts: Boolean,
)

/**
 * Feeds the finger's [sample] to [start] and, when that turns the press into choosing, tells [press]. The lift is a
 * sample too, so a finger that rested for the hold time and lifted in the same moment still has a selection to choose.
 */
private fun AwaitPointerEventScope.feedChoice(
    start: DockChoiceStart,
    sample: PressSample,
    down: PointerInputChange,
    direction: DockPullDirection,
    timing: ChoiceTiming,
    press: DockPressState,
    canChoose: Boolean,
): DockChoiceStart {
    if (start.choosing || !canChoose) return start
    val drag = sample.position - down.position
    val travelDp = DockPress.travelDp(direction, drag.x / density, drag.y / density)
    val alongDp = (if (direction.unitY != 0f) drag.x else drag.y) / density
    val next = start.next(sample.timeMillis, travelDp, alongDp, timing.holdMillis, timing.holdStarts)
    if (next.choosing) {
        val slid = abs(alongDp) > next.slideDp(timing.holdStarts)
        press.beginChoosing(sample.position, if (slid) "slide" else "hold", travelDp, alongDp)
    }
    return next
}

/** One event of a tracked press; true once the press has ended. */
private fun AwaitPointerEventScope.trackPressStep(
    change: PointerInputChange?,
    down: PointerInputChange,
    direction: DockPullDirection,
    press: DockPressState,
): Boolean {
    if (change == null) {
        press.cancel()
        return true
    }
    val drag = change.position - down.position
    val travelDp = DockPress.travelDp(direction, drag.x / density, drag.y / density)
    press.update(travelDp)
    press.choose(change.position)
    change.consume()
    if (!change.pressed) press.release(travelDp)
    return !change.pressed
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
