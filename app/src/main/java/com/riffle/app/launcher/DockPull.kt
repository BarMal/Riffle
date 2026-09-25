package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import com.riffle.core.domain.launcher.dockpull.DockPullClaim
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionController
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState
import com.riffle.core.domain.launcher.dockpull.SettleOutcome
import com.riffle.core.domain.launcher.dockpull.dockPullClaimFor
import com.riffle.core.domain.launcher.dockpull.switchNow
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A switch the dock pull committed and asked the shell for, which the shell has not shown yet.
 * [fromSet] is the layout set the switch was asked from: once the shell hands back another set, or
 * shows [mode], the switch has landed (or been refused) and the pull stops standing in for it.
 */
internal data class DockPullPendingSwitch(
    val mode: LauncherViewMode,
    val fromSet: HomeLayoutSet,
)

/**
 * The UI side of the dock pull (#1206): holds the pure [DockPullTransitionController]'s state,
 * animates its settles and reports a committed switch through [onCommitted].
 *
 * Nothing here decides anything: whether a release commits, where the dock goes and how the
 * surfaces are drawn are all the domain's (DockPullTransitionController.kt, DockPullFrame.kt).
 */
@Stable
internal class DockPullState(
    private val scope: CoroutineScope,
) {
    private val controller = DockPullTransitionController()
    private var settleJob: Job? = null

    /** Where the transition is. Read it in draw or layout blocks, not in composition, while it tracks. */
    var transition: DockPullTransitionState by mutableStateOf(DockPullTransitionState.Idle(ModeSurface.HOME))
        private set

    /** A committed switch still on its way through the shell. */
    var pending: DockPullPendingSwitch? by mutableStateOf(null)

    /** Called when a settle commits, with the surface it switched to; the host dispatches the switch. */
    var onCommitted: (ModeSurface) -> Unit = {}

    /**
     * A finger landed on the dock while it was settling: catch it where it is and track from there.
     * Returns whether there was a settle to catch.
     */
    fun catchSettle(reducedMotion: Boolean): Boolean {
        val settling = transition as? DockPullTransitionState.Settling ?: return false
        settleJob?.cancel()
        transition =
            controller.start(
                state = settling,
                direction = settling.direction,
                travelDp = settling.travelDp,
                reducedMotion = reducedMotion,
            )
        return true
    }

    /** A fresh pull out of [surface], [travelDp] long, in [direction]. */
    fun start(
        surface: ModeSurface,
        direction: DockPullDirection,
        travelDp: Float,
        reducedMotion: Boolean,
    ) {
        settleJob?.cancel()
        transition =
            controller.start(
                state = DockPullTransitionState.Idle(surface = surface, reducedMotion = reducedMotion),
                direction = direction,
                travelDp = travelDp,
                reducedMotion = reducedMotion,
            )
    }

    fun drag(
        dxDp: Float,
        dyDp: Float,
    ) {
        transition = controller.drag(transition, dxDp, dyDp)
    }

    fun release(
        velocityXDpPerSecond: Float,
        velocityYDpPerSecond: Float,
    ) {
        transition = controller.release(transition, velocityXDpPerSecond, velocityYDpPerSecond)
        settle()
    }

    /** The gesture was taken away: spring back. */
    fun cancel() {
        transition = controller.cancel(transition)
        settle()
    }

    /**
     * The pull's accessibility and keyboard equivalents (Decision 12): the same commit, run as a
     * settle from rest. Ignored while a transition or a switch is already under way.
     */
    fun switchNow(
        surface: ModeSurface,
        direction: DockPullDirection,
        reducedMotion: Boolean,
    ): Boolean {
        if (transition !is DockPullTransitionState.Idle || pending != null) return false
        transition =
            controller.switchNow(
                state = DockPullTransitionState.Idle(surface = surface, reducedMotion = reducedMotion),
                direction = direction,
                reducedMotion = reducedMotion,
            )
        settle()
        return true
    }

    private fun settle() {
        val settling = transition as? DockPullTransitionState.Settling ?: return
        settleJob?.cancel()
        settleJob =
            scope.launch {
                Animatable(settling.progress).animateTo(
                    targetValue = settling.targetProgress,
                    animationSpec = dockPullSettleSpec(settling.reducedMotion),
                ) {
                    transition = controller.settleProgress(transition, value)
                }
                val finished = transition
                if (finished is DockPullTransitionState.Settling && finished.outcome == SettleOutcome.COMMIT) {
                    onCommitted(finished.to)
                }
                transition = controller.finish(finished)
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

/** What the dock pull gesture needs from its host, read fresh at each gesture. */
internal interface DockPullGestureHost {
    val state: DockPullState

    /** Whether a pull may start now (browsing, nothing pending, the window measured). */
    val canPull: Boolean

    val surface: ModeSurface

    val reducedMotion: Boolean

    /** The pull's full travel in dp: the window's extent along [direction]'s axis. */
    fun travelDp(direction: DockPullDirection): Float
}

/**
 * The dock pull gesture on the dock body (Decision 3), in the dock edge's natural [direction].
 *
 * On the Main pass, after the dock's own children, like the shelf gesture it replaces in that
 * direction: a section scroll along the run or an item's long-press drag consumes its touches first
 * and the pull yields. A drag only becomes a pull once it leaves touch slop mostly along
 * [direction] ([dockPullClaimFor]) and before the long-press timeout, so a held press stays a
 * long-press. A finger landing on a settling dock catches it at once. Deltas are handed to the
 * controller in dp; only their component along the pull moves it.
 *
 * Place it before any translation of the dock, so the finger is measured in the dock's resting
 * coordinates rather than ones that move with the finger.
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
            val caught = host.state.catchSettle(host.reducedMotion)
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
                                surface = host.surface,
                                direction = direction,
                                travelDp = host.travelDp(direction),
                                reducedMotion = host.reducedMotion,
                            )
                            // From where the finger landed, so the dock tracks it 1:1 from the start.
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
        // The gesture was torn down mid-pull (the dock left composition, its edge changed): never
        // leave the transition stranded in Tracking.
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

/** The arrow key that, with Ctrl, performs the pull in [this] direction (Decision 12). */
internal val DockPullDirection.shortcutKey: Key
    get() =
        when (this) {
            DockPullDirection.UP -> Key.DirectionUp
            DockPullDirection.DOWN -> Key.DirectionDown
            DockPullDirection.LEFT -> Key.DirectionLeft
            DockPullDirection.RIGHT -> Key.DirectionRight
        }

/** The dock's accessibility action for a pull out of [surface]. */
internal fun dockPullActionLabel(surface: ModeSurface): String =
    when (surface) {
        ModeSurface.HOME -> DOCK_PULL_TO_LIBRARY_LABEL
        ModeSurface.LIBRARY -> DOCK_PULL_TO_HOME_LABEL
    }

/**
 * The shell state as [mode]'s surface would see it, for drawing that surface before the shell has
 * switched to it: during a pull (the incoming surface) and in the frame or two between a commit and
 * the shell's new state. Never written back; the switch itself is the shell's
 * [LauncherShellAction.SelectLauncherViewMode].
 */
internal fun LauncherShellState.dockPullPreviewFor(mode: LauncherViewMode): LauncherShellState {
    val previewSet = homeLayoutSet.withActiveLayout(homeLayout).selectMode(mode)
    return copy(
        homeLayout = previewSet.activeLayout.withHomeScreenLibraryApps(installedApps),
        homeLayoutSet = previewSet,
    )
}

internal const val DOCK_PULL_TO_LIBRARY_LABEL = "Switch to Library"
internal const val DOCK_PULL_TO_HOME_LABEL = "Switch to Home"
internal const val HOME_DOCK_PULL_TEST_TAG = "home-dock-pull"

private const val DOCK_PULL_PROGRESS_THRESHOLD = 0.001f
