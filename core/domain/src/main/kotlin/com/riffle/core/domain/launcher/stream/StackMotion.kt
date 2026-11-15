package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * The card stack's physics as pure code (ADR 0003, Amendment 1 "Browse, then open" and Amendment 3 "Motion"): the
 * stack has one continuous position, a float card index. Every card is drawn from `r = index - position`: r = 0 is the
 * front card, r = -1 a card sunk into the Dock and faded, r = 1..3 the cards peeking above, r >= 4 hidden. A drag moves
 * the position, a release picks a card to settle on and a spring with a magnet carries it there. The numbers are the
 * working prototype's (UX canvas, board M3, `MotionStack`) and live in [DockMotionConstants] and [StreamCardStackSpec];
 * the settings slice exposes the inertia and the magnet through [DockMotionTuning].
 */

/**
 * How one card is drawn at an instant. Distances are dp measured up from the bottom of the stack area (just above the
 * Dock); the card is drawn as a whole card scaled by [scale] from the centre of its top edge, with only the part
 * between [visibleBottomDp] and [topDp] shown: the real card, clipped, never a separate tab.
 */
data class StackCardFrame(
    /** Opacity, 0 to 1. */
    val alpha: Float,
    /** Size, 1 for the front card, a little less for each card behind it and for the one sinking. */
    val scale: Float,
    /** Where the card's top edge is drawn. */
    val topDp: Float,
    /** The lowest edge of what is shown: the top of the card in front (plus the gap), or the card's own bottom. */
    val visibleBottomDp: Float,
) {
    /** How tall the shown part is, as drawn. */
    val visibleHeightDp: Float get() = (topDp - visibleBottomDp).coerceAtLeast(0f)

    /** How tall the shown part is in the card's own, unscaled coordinates: the height to clip the real card to. */
    val clipHeightDp: Float get() = if (scale > 0f) visibleHeightDp / scale else 0f

    val isVisible: Boolean get() = alpha > 0f && visibleHeightDp > 0f
}

/**
 * The stack's layout as a function of position. A card is as tall as its content needs ([heightsDp], one per card;
 * without them every card is [cardHeightDp] tall). The stack is anchored at its bottom: the front card stands on the
 * bottom of the area and its top is its own height up, so a short front card leaves the strips of the cards behind it
 * lower; every strip is a [pitchDp] above the one in front of it, so the strips line up whatever their cards' heights.
 * Between two cards the front card's top moves from the one's height to the other's ([frontHeightAt]).
 *
 * The rules, which the tests pin: a card never overlaps the one in front of it (the slot's lower edge is the top of the
 * card in front plus the gap, if there is one), so translucent fills never add up; the slot opens as a card moves
 * toward the front (from a strip to the whole card); and the front card sinks into the Dock, fading, while the next
 * one is revealed above it.
 */
class StackGeometry(
    val cardHeightDp: Float,
    val pitchDp: Float = StreamCardStackSpec.PITCH_DP,
    val gapDp: Float = StreamCardStackSpec.GAP_DP,
    private val heightsDp: List<Float> = emptyList(),
    /** How many cards peek above the front one (the folded phone's three; the unfolded column's as many as fit). */
    val visiblePeeks: Int = StreamCardStackSpec.VISIBLE_PEEKS,
    /** Each peeking card is this much narrower than the one in front of it. */
    private val scaleStep: Float = StreamCardStackSpec.SCALE_STEP,
) {
    /** A stack whose cards each have their own height; [cardHeightDp] is then the tallest. */
    constructor(
        heightsDp: List<Float>,
        pitchDp: Float = StreamCardStackSpec.PITCH_DP,
        visiblePeeks: Int = StreamCardStackSpec.VISIBLE_PEEKS,
        scaleStep: Float = StreamCardStackSpec.SCALE_STEP,
    ) : this(
        cardHeightDp = heightsDp.maxOrNull() ?: StreamCardStackSpec.CARD_HEIGHT_DP,
        pitchDp = pitchDp,
        heightsDp = heightsDp,
        visiblePeeks = visiblePeeks,
        scaleStep = scaleStep,
    )

    private val peeks = visiblePeeks.toFloat()

    /** How tall the card at [index] is; an index outside the stack is held to it. */
    fun heightOf(index: Int): Float =
        if (heightsDp.isEmpty()) cardHeightDp else heightsDp[index.coerceIn(0, heightsDp.size - 1)]

    /**
     * How high the front card's top stands at [position]: the front card's height at a whole position, and between
     * two cards the one's height moving to the other's, so the strips rise or fall smoothly as the stack moves.
     */
    fun frontHeightAt(position: Float): Float {
        if (heightsDp.isEmpty()) return cardHeightDp
        val held = position.coerceIn(0f, (heightsDp.size - 1).toFloat())
        val low = floor(held).toInt()
        val fraction = held - low
        return if (fraction <= 0f) heightOf(low) else heightOf(low) + (heightOf(low + 1) - heightOf(low)) * fraction
    }

    /**
     * The frame of the card at [index] when the stack is at [position]. [stripPitchDp] is the distance between the
     * strips now (the geometry's own [pitchDp] at rest; a card that is open eases it to the slim tabs' pitch).
     */
    fun frame(
        index: Int,
        position: Float,
        stripPitchDp: Float = pitchDp,
    ): StackCardFrame =
        frame(
            r = index - position,
            ownHeightDp = heightOf(index),
            frontHeightDp = frontHeightAt(position),
            inFrontHeightDp = heightOf(index - 1),
            stripPitchDp = stripPitchDp,
        )

    /** The frame of the card at [r] cards behind the front (negative: in front of it, sinking), all cards alike. */
    fun frame(r: Float): StackCardFrame = frame(r, cardHeightDp, cardHeightDp, cardHeightDp, pitchDp)

    private fun frame(
        r: Float,
        ownHeightDp: Float,
        frontHeightDp: Float,
        inFrontHeightDp: Float,
        stripPitchDp: Float,
    ): StackCardFrame =
        when {
            r <= -1f || r >= peeks + 1f ->
                HIDDEN.copy(topDp = topDp(r.coerceIn(-1f, peeks + 1f), ownHeightDp, frontHeightDp, stripPitchDp))
            r < 0f -> sinking(r, ownHeightDp)
            else -> behind(r, ownHeightDp, frontHeightDp, inFrontHeightDp, stripPitchDp)
        }

    /** The front card on its way into the Dock: the whole card, moving down, shrinking and fading. */
    private fun sinking(
        r: Float,
        ownHeightDp: Float,
    ): StackCardFrame {
        val u = r + 1f
        val top = ownHeightDp * u
        val scale = StreamCardStackSpec.SINK_SCALE + (1f - StreamCardStackSpec.SINK_SCALE) * u
        return StackCardFrame(alpha = u, scale = scale, topDp = top, visibleBottomDp = top - ownHeightDp * scale)
    }

    /**
     * The front card (r = 0) and the cards behind it. Below a card lies the card in front of it: nothing of its slot is
     * lower than that card's top, plus a gap that opens over the first card's travel (the front card itself has no card
     * in front and shows whole).
     */
    private fun behind(
        r: Float,
        ownHeightDp: Float,
        frontHeightDp: Float,
        inFrontHeightDp: Float,
        stripPitchDp: Float,
    ): StackCardFrame {
        val top = topDp(r, ownHeightDp, frontHeightDp, stripPitchDp)
        val scale = 1f - scaleStep * minOf(r, peeks)
        val alpha = if (r <= peeks) 1f else peeks + 1f - r
        val visibleBottom = topDp(r - 1f, inFrontHeightDp, frontHeightDp, stripPitchDp) + gapDp * minOf(r, 1f)
        return StackCardFrame(alpha = alpha, scale = scale, topDp = top, visibleBottomDp = minOf(visibleBottom, top))
    }

    /** The top edge of the card at [r] in a stack whose cards are all [cardHeightDp] tall. */
    fun topDp(r: Float): Float = topDp(r, cardHeightDp, cardHeightDp, pitchDp)

    /**
     * The top edge of a card [ownHeightDp] tall at [r]: the front card's top is the front height; each card behind is
     * a pitch higher; a card sinking into the Dock comes down from its own height.
     */
    private fun topDp(
        r: Float,
        ownHeightDp: Float,
        frontHeightDp: Float,
        stripPitchDp: Float,
    ): Float =
        when {
            r <= -1f -> 0f
            r < 0f -> ownHeightDp * (r + 1f)
            else -> frontHeightDp + stripPitchDp * r
        }

    /** The indices of the cards worth composing at [position]: those with something to show (`-1 < r < 4`). */
    fun composedRange(
        position: Float,
        count: Int,
    ): IntRange {
        if (count <= 0) return IntRange.EMPTY
        val first = floor(position - 1f).toInt() + 1
        val last = ceil(position + peeks + 1f).toInt() - 1
        return first.coerceAtLeast(0)..last.coerceAtMost(count - 1)
    }
}

private val HIDDEN = StackCardFrame(alpha = 0f, scale = 1f, topDp = 0f, visibleBottomDp = 0f)

/** The finger's part: how far a drag moves the stack and how it resists at either end. */
object StackDrag {
    /** How many cards a finger drag of [dragDp] (positive: down) moves the stack, before any resistance. */
    fun cardsFor(dragDp: Float): Float = dragDp / DockMotionConstants.STACK_DRAG_DP_PER_CARD

    /**
     * The position after the finger moves by [deltaCards] (positive: down, which brings older cards toward the front).
     * Inside the stack it follows one to one; past the first or last card it moves
     * [DockMotionConstants.STACK_RUBBER_BAND] of the finger's travel, both ways, so the finger can pull it out and let
     * it back along the same path. A move that crosses an end is split there. (Worked in the finger's own space, where
     * the band is stretched out to full travel.)
     */
    fun advance(
        position: Float,
        deltaCards: Float,
        count: Int,
    ): Float {
        val high = (count - 1).coerceAtLeast(0).toFloat()
        return fromFinger(toFinger(position, high) + deltaCards, high)
    }

    private fun toFinger(
        position: Float,
        high: Float,
    ): Float =
        when {
            position > high -> high + (position - high) / DockMotionConstants.STACK_RUBBER_BAND
            position < 0f -> position / DockMotionConstants.STACK_RUBBER_BAND
            else -> position
        }

    private fun fromFinger(
        finger: Float,
        high: Float,
    ): Float =
        when {
            finger > high -> high + (finger - high) * DockMotionConstants.STACK_RUBBER_BAND
            finger < 0f -> finger * DockMotionConstants.STACK_RUBBER_BAND
            else -> finger
        }

    /** The position held to the stack, where a release is measured from. */
    fun clamp(
        position: Float,
        count: Int,
    ): Float = position.coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat())
}

/** Where a release goes: the card a fling would reach, and the rule that a settle lands on a whole one. */
object StackFling {
    /**
     * The card the stack settles on when released at [position] moving [velocityCardsPerSecond] (positive: toward older
     * cards): the card it would coast to, `position + velocity * 0.18 s * inertia`, rounded and held to the stack. At
     * rest (or with no inertia) it is the nearest card.
     */
    fun target(
        position: Float,
        velocityCardsPerSecond: Float,
        count: Int,
        inertia: Float = DockMotionConstants.DEFAULT_STACK_INERTIA,
    ): Int {
        if (count <= 0) return 0
        val projected =
            position +
                velocityCardsPerSecond * DockMotionConstants.STACK_FLING_SECONDS *
                inertia.coerceIn(0f, DockMotionConstants.MAX_STACK_INERTIA)
        return projected.roundToInt().coerceIn(0, count - 1)
    }

    /** The card in front at [position]: the nearest one. */
    fun nearest(
        position: Float,
        count: Int,
    ): Int = target(position, 0f, count)

    /** The velocity in cards per second of a drag moving [velocityDpPerSecond] (positive: down). */
    fun velocityCards(velocityDpPerSecond: Float): Float =
        velocityDpPerSecond / DockMotionConstants.STACK_DRAG_DP_PER_CARD
}

/** The stack's position and how fast it moves, in cards and cards per second. */
data class StackSpringState(
    val position: Float,
    val velocity: Float = 0f,
)

/**
 * The spring that carries a released stack onto its card (critically damped, so it never swings past), with the magnet:
 * within a quarter of a card of the card it settles on the pull strengthens, so it is drawn in and clicks home. Fixed
 * steps, so a trajectory is the same whatever the frame rate.
 */
object StackSpring {
    /**
     * The state a release starts from. A velocity toward [target] is held to what the spring can absorb without passing
     * it; one away from it (a push past either end, which has moved the stack at the rubber band's rate) is held to
     * what carries the stack only a little past the card before the spring turns it round, so a hard push at the end
     * never sends the stack out of sight.
     */
    fun release(
        position: Float,
        velocityCardsPerSecond: Float,
        target: Int,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): StackSpringState {
        val toward = target - position
        val limit =
            if (velocityCardsPerSecond * toward > 0f) {
                tuning.stackOmega * abs(toward)
            } else {
                tuning.stackOmega * DockMotionConstants.STACK_MAX_AWAY_CARDS
            }
        return StackSpringState(position, velocityCardsPerSecond.coerceIn(-limit, limit))
    }

    /** [state] advanced by [seconds] toward card [target]. */
    fun advance(
        state: StackSpringState,
        target: Int,
        seconds: Float,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): StackSpringState {
        var current = state
        var remaining = seconds.coerceIn(0f, DockMotionConstants.MAX_FRAME_SECONDS)
        while (remaining > 0f) {
            val step = minOf(remaining, DockMotionConstants.STEP_SECONDS)
            current = step(current, target, step, tuning)
            remaining -= step
        }
        return current
    }

    /** One fixed step of [seconds]. */
    fun step(
        state: StackSpringState,
        target: Int,
        seconds: Float,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): StackSpringState {
        val omega = tuning.stackOmega
        val pull = pullAt(abs(state.position - target), tuning)
        val velocity =
            state.velocity +
                (-omega * omega * pull * (state.position - target) - 2f * omega * sqrt(pull) * state.velocity) * seconds
        val position = state.position + velocity * seconds
        // Critically damped means it never crosses its rest; the integrator must not either.
        if ((state.position - target) * (position - target) < 0f) return StackSpringState(target.toFloat(), 0f)
        return StackSpringState(position, velocity)
    }

    /**
     * How many times stronger than its own the spring pulls at [distanceCards] from its card: 1 far away, more near.
     */
    fun pullAt(
        distanceCards: Float,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): Float {
        val near = maxOf(0f, 1f - distanceCards / DockMotionConstants.STACK_MAGNET_RANGE)
        return 1f + tuning.magnetGain * near
    }

    /** Whether the spring has come to rest on card [target]. */
    fun isSettled(
        state: StackSpringState,
        target: Int,
    ): Boolean =
        abs(state.position - target) < DockMotionConstants.STACK_SETTLED_POSITION &&
            abs(state.velocity) < DockMotionConstants.STACK_SETTLED_VELOCITY
}

/** What happens when a stack settles: the rest position, the haptic and the reduced-motion path. */
object StackSettle {
    /** A settle always ends on a whole card: the position is exactly its index. */
    fun restPosition(target: Int): Float = target.toFloat()

    /** The light tick of a settle: only when the stack lands on a different card than it started on. */
    fun shouldTick(
        startedOn: Int,
        settledOn: Int,
    ): Boolean = settledOn != startedOn

    /** Reduced motion: no coast, no spring, no magnet. A release goes to the card nearest the finger, at once. */
    fun reducedTarget(
        position: Float,
        count: Int,
    ): Int = StackFling.nearest(position, count)

    /** Reduced motion: the stack's opacity [elapsedSeconds] into the short fade that follows the jump. */
    fun reducedFadeAlpha(elapsedSeconds: Float): Float {
        val fraction = (elapsedSeconds / DockMotionConstants.REDUCED_FADE_SECONDS).coerceIn(0f, 1f)
        val from = DockMotionConstants.STACK_REDUCED_FADE_FROM
        return from + (1f - from) * fraction
    }

    /** One card [older] (toward the back, drawn above) or newer from [current], or null at either end. */
    fun step(
        current: Int,
        count: Int,
        older: Boolean,
    ): Int? = (current + if (older) 1 else -1).takeIf { it in 0 until count }
}
