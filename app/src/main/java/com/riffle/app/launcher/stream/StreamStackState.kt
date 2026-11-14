package com.riffle.app.launcher.stream

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.stream.StackDrag
import com.riffle.core.domain.launcher.stream.StackFling
import com.riffle.core.domain.launcher.stream.StackSettle
import com.riffle.core.domain.launcher.stream.StackSpring
import com.riffle.core.domain.launcher.stream.StackSpringState

/**
 * The card stack's one continuous position (a float card index) and what moves it: the finger, a release, a step to a
 * card. The decisions (rubber band, fling target, spring, magnet, tick) are the pure rules in `StackMotion.kt`; this
 * only runs them against the frame clock. Everything that moves the position runs inside the platform scrollable's
 * scroll scope (see `FocusedStack`), so a finger that lands mid-motion cancels the motion and carries on from where it
 * had got to.
 */
@Stable
internal class StreamStackState {
    /** Which card is in front, as a float: 0 is the first card, 1.5 is half way from the second to the third. */
    var position by mutableFloatStateOf(0f)
        private set

    /** The stack's opacity: 1, except for the short fade after a jump under reduced motion. */
    var fade by mutableFloatStateOf(1f)
        private set

    /** The card the stack last came to rest on. */
    var settled by mutableIntStateOf(0)
        private set

    /** The card the stack is moving to, so Next and Previous step on from where a running move is heading. */
    var heading: Int = 0
        private set

    /** A motion (a drag, a coast, a glide) is running or was cut short by a finger: the drag has no slop to give. */
    var inMotion: Boolean = false
        private set

    /** Holds the stack to [count] cards after the content changed. */
    fun clampTo(count: Int) {
        val last = (count - 1).coerceAtLeast(0)
        if (position > last) position = last.toFloat()
        if (settled > last) settled = last
        if (heading > last) heading = last
    }

    /** The finger moved by [deltaDp] (positive: down); [slopDp] is credited once at the start of a drag. */
    fun dragBy(
        deltaDp: Float,
        slopDp: Float,
        count: Int,
    ) {
        val credit = if (!inMotion && deltaDp != 0f) slopDp * (if (deltaDp > 0f) 1f else -1f) else 0f
        inMotion = true
        position = StackDrag.advance(position, StackDrag.cardsFor(deltaDp + credit), count)
    }

    /**
     * The finger lifted at [velocityCards] (cards per second, positive: toward older cards). The stack coasts to the
     * card it would reach and a magnetic spring settles it on that whole card; under reduced motion it jumps to the
     * nearest card and fades. Cancelled by a finger landing, which leaves it where it is.
     */
    suspend fun release(
        velocityCards: Float,
        count: Int,
        tuning: DockMotionTuning,
        reduced: Boolean,
        onTick: () -> Unit,
    ) {
        inMotion = true
        if (reduced) {
            jump(StackSettle.reducedTarget(position, count), onTick)
            return
        }
        val velocityCards = if (velocityCards.isFinite()) velocityCards else 0f
        val target = StackFling.target(position, velocityCards, count, tuning.stackInertia)
        heading = target
        spring(StackSpring.release(position, velocityCards, target, tuning), target, tuning, onTick)
    }

    /** Moves to card [target] without a finger (a tap on a peeking card, Next and Previous, the keyboard). */
    suspend fun glideTo(
        target: Int,
        tuning: DockMotionTuning,
        reduced: Boolean,
        onTick: () -> Unit,
    ) {
        inMotion = true
        heading = target
        if (reduced) {
            jump(target, onTick)
        } else {
            spring(StackSpringState(position), target, tuning, onTick)
        }
    }

    /** Steps [heading] one card, returning the card to go to, or null at an end. */
    fun stepFromHeading(
        older: Boolean,
        count: Int,
    ): Int? = StackSettle.step(heading, count, older)

    private suspend fun spring(
        from: StackSpringState,
        target: Int,
        tuning: DockMotionTuning,
        onTick: () -> Unit,
    ) {
        var state = from
        var last = withFrameNanos { it }
        while (!StackSpring.isSettled(state, target)) {
            val now = withFrameNanos { it }
            state = StackSpring.advance(state, target, (now - last) / NANOS_PER_SECOND, tuning)
            last = now
            position = state.position
        }
        rest(target, onTick)
    }

    /** Brings card [target] to the front at once (a tap on a strip behind an open card opens that card in place). */
    fun snapTo(target: Int) {
        position = StackSettle.restPosition(target)
        heading = target
    }

    /** Reduced motion: no coast, spring or magnet; the nearest card at once and a short fade. */
    private suspend fun jump(
        target: Int,
        onTick: () -> Unit,
    ) {
        position = StackSettle.restPosition(target)
        heading = target
        try {
            fade = StackSettle.reducedFadeAlpha(0f)
            val start = withFrameNanos { it }
            while (fade < 1f) {
                val now = withFrameNanos { it }
                fade = StackSettle.reducedFadeAlpha((now - start) / NANOS_PER_SECOND)
            }
        } finally {
            fade = 1f
        }
        rest(target, onTick)
    }

    private fun rest(
        target: Int,
        onTick: () -> Unit,
    ) {
        position = StackSettle.restPosition(target)
        val tick = StackSettle.shouldTick(startedOn = settled, settledOn = target)
        settled = target
        heading = target
        inMotion = false
        if (tick) onTick()
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}

/** The finger's velocity in px per second (positive: down) as cards per second. */
internal fun cardsPerSecond(
    pxPerSecond: Float,
    density: Float,
): Float = StackFling.velocityCards(pxPerSecond / density)

/** The Dock motion tuning the stack runs with where nothing provides the settings' (tests, previews): the defaults. */
internal val StackDefaultTuning = DockMotionTuning(inertia = DockMotionConstants.DEFAULT_STACK_INERTIA)
