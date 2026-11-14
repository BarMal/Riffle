package com.riffle.app.launcher.stream

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LocalDockMotionTuning
import com.riffle.app.launcher.LocalLauncherHaptics
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.stream.OpenCardHandoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What moves a stack's one position, shared by the stack when it is at rest and by the open card's layer when a card is
 * open: the platform's scrollable state (ADR 0002: drag detection, slop, velocity and the choice of axis are the
 * platform's), the stack's own release (a coast and a magnet in place of the platform's decay) and the steps to a
 * card. Every move without a finger runs inside the scrollable's scroll, so a finger that lands cuts it short and
 * carries on from where it had got to.
 */
@Stable
@Suppress("LongParameterList")
internal class StackMover(
    val state: StreamStackState,
    private val scope: CoroutineScope,
    private val density: Float,
    private val slopDp: Float,
    private val count: () -> Int,
    private val tuning: () -> DockMotionTuning,
    private val reduced: () -> Boolean,
    private val tick: () -> Unit,
) {
    /** The platform's delta is the finger's, in px, positive downward. */
    val scrollable: ScrollableState =
        ScrollableState { delta ->
            state.dragBy(deltaDp = delta / density, slopDp = slopDp, count = count())
            delta
        }

    /**
     * The release of a drag: the stack coasts to the card the velocity would reach and settles on it. A fling that no
     * drag of the stack's came before (the finger only ran a scrolling body past its edge) never moves it: a card is
     * not turned by momentum alone.
     */
    val flingBehavior: FlingBehavior =
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (!state.inMotion) return initialVelocity
                state.release(
                    velocityCards = cardsPerSecond(initialVelocity, density),
                    count = count(),
                    tuning = tuning(),
                    reduced = reduced(),
                    onTick = tick,
                )
                return 0f
            }
        }

    /** Moves to card [target] without a finger; false when there is nothing to move to. */
    fun goTo(target: Int): Boolean {
        val moves = target in 0 until count() && target != state.heading
        if (moves) {
            scope.launch { scrollable.scroll { state.glideTo(target, tuning(), reduced(), tick) } }
        }
        return moves
    }

    /** One card older ([older]) or newer than where the stack is heading; false at an end. */
    fun step(older: Boolean): Boolean = state.stepFromHeading(older, count())?.let(::goTo) ?: false

    /** Moves by [deltaDp] of finger travel without the platform's drag (the hand-off from an open card's body). */
    fun dragBy(deltaDp: Float) {
        if (deltaDp != 0f) state.dragBy(deltaDp = deltaDp, slopDp = 0f, count = count())
    }
}

/**
 * The hand-off from an open card's scrolling body to the stack (platform nested scrolling): the body consumes a drag
 * first; the part it cannot take at its top or bottom is accumulated by [OpenCardHandoff] and the stack moves only
 * once it passes the threshold. A body with nothing to scroll hands the drag over at once (the connection stands aside
 * and the stack's own scrollable takes it), and what a body's fling leaves over never moves the stack.
 */
internal class OpenCardHandoffConnection(
    private val mover: StackMover,
    private val density: Float,
    /** The scroll state of the open card's body that is in front, or null. */
    private val bodyScroll: () -> ScrollState?,
) : NestedScrollConnection {
    private val handoff = OpenCardHandoff()

    private fun bodyScrolls(): Boolean = bodyScroll()?.let { it.canScrollForward || it.canScrollBackward } == true

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val back =
            if (source == NestedScrollSource.UserInput && handoff.active) {
                handoff.retract(available.y / density)
            } else {
                OpenCardHandoff.Retraction(0f, 0f)
            }
        mover.dragBy(back.stackDp)
        return Offset(0f, back.consumedDp * density)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y == 0f || !bodyScrolls()) return Offset.Zero
        // What a body's fling leaves over at its edge is no drag of the finger's: it is swallowed, never momentum.
        if (source == NestedScrollSource.UserInput) mover.dragBy(handoff.leftover(available.y / density))
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        handoff.reset()
        return Velocity.Zero
    }
}

/** The mover of the stack whose position is [state]: its cards number [count] and it jumps under [reducedMotion]. */
@Composable
internal fun rememberStackMover(
    state: StreamStackState,
    count: Int,
    reducedMotion: Boolean,
): StackMover {
    val density = LocalDensity.current
    val slopDp = with(density) { LocalViewConfiguration.current.touchSlop.toDp().value }
    val scope = rememberCoroutineScope()
    val latestCount by rememberUpdatedState(count)
    val latestReduced by rememberUpdatedState(reducedMotion)
    val latestHaptics by rememberUpdatedState(LocalLauncherHaptics.current)
    val latestTuning by rememberUpdatedState(LocalDockMotionTuning.current)
    return remember(state, density.density, slopDp) {
        StackMover(
            state = state,
            scope = scope,
            density = density.density,
            slopDp = slopDp,
            count = { latestCount },
            tuning = { latestTuning },
            reduced = { latestReduced },
            tick = { latestHaptics.perform(LauncherHapticEvent.DETENT) },
        )
    }
}
