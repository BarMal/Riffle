package com.riffle.core.domain.launcher.stream

import kotlin.math.abs
import kotlin.math.sign

/**
 * Swiping a notification's card sideways to dismiss it (ADR 0003, Amendment 1; device fixes, round 5, item 2), as pure
 * rules: where the gesture exists, when a release dismisses and how the card looks on the way.
 *
 * The rule that settles who owns a horizontal drag, by where it starts ([ownerOf], batch 7): a drag that starts on a
 * card is always that card's and never the pager's. On the front card of a notification that [StreamActionPolicy]
 * offers Dismiss for it dismisses; on any other card (a strip, a notification that cannot be dismissed, any other kind
 * of card) the card resists, a short rubber band ([resist]) with no fade, and settles back. Only a drag that starts in
 * the page's empty room, outside every card, changes the stack (the pager's); the Dock is the primary route.
 */
object SwipeDismiss {
    /** A release past this share of the card's width dismisses, however slowly. */
    const val DISTANCE_FRACTION = 0.4f

    /** A fling at least this fast (dp per second) dismisses, once the card has left its place a little. */
    const val FLING_VELOCITY_DP_PER_SECOND = 700f

    /** The least the card has travelled (dp) for a fling to dismiss it: a flick that barely moved it is not. */
    const val MIN_FLING_TRAVEL_DP = 24f

    /** The card's opacity at a full width of travel: a slight fade, not a vanishing. */
    const val MIN_ALPHA = 0.5f

    /** How long the card that was sent off waits (ms) for its notification to go before it comes back (it failed). */
    const val RETURN_AFTER_MILLIS = 2_000L

    /** The farthest an off-screen card is taken, in widths: past the edge of the card, so none of it shows. */
    const val FLY_OFF_WIDTHS = 1.2f

    /** How long a card sent off takes to leave, at the default opening speed. */
    const val FLY_OFF_MILLIS = 180

    /** What a release does. */
    enum class Outcome { DISMISS, SETTLE }

    /** Whether [content] can be dismissed by a swipe: only a notification that the policy offers Dismiss for. */
    fun offers(content: StreamContent): Boolean =
        content is NotificationContent && StreamActionPolicy.offers(content, StreamAction.Dismiss)

    /** Who a horizontal drag belongs to. */
    enum class Owner {
        /** The card goes with the finger, fades, and is dismissed past the threshold. */
        DISMISS,

        /** The card gives a few dp, without fading, and settles back; the pager does not get the drag. */
        RESIST,

        /** The page's empty room: the pager changes the stack. */
        PAGER,
    }

    /**
     * Who owns a horizontal drag that starts where it does: [onCard] false is the empty room (the pager's); on a card,
     * the front card of a dismissible notification ([content] is the card's content, null for a card that is no
     * content) dismisses and every other card resists.
     */
    fun ownerOf(
        onCard: Boolean,
        content: StreamContent?,
        isFront: Boolean,
    ): Owner =
        when {
            !onCard -> Owner.PAGER
            isFront && content != null && offers(content) -> Owner.DISMISS
            else -> Owner.RESIST
        }

    /** How far a card that resists gives at most (dp): a short rubber band, a hint and not a movement. */
    const val RESIST_MAX_DP = 12f

    /** The card's offset (px) at a raw finger travel of [rawPx] when it resists: it follows, never past the max. */
    fun resist(
        rawPx: Float,
        density: Float,
    ): Float {
        val max = RESIST_MAX_DP * density
        val travel = abs(rawPx)
        return sign(rawPx) * max * travel / (travel + max)
    }

    /** The finger travel that gives [offsetPx] of [resist] (for a finger that takes the card again mid-settle). */
    fun unresist(
        offsetPx: Float,
        density: Float,
    ): Float {
        val max = RESIST_MAX_DP * density
        val shown = abs(offsetPx).coerceAtMost(max * 0.99f)
        return sign(offsetPx) * max * shown / (max - shown)
    }

    /** Whether a card taken [offsetPx] sideways is past the point where a release dismisses it. */
    fun isArmed(
        offsetPx: Float,
        widthPx: Float,
    ): Boolean = widthPx > 0f && abs(offsetPx) >= widthPx * DISTANCE_FRACTION

    /** The card's scale while it is taken: lifted by [LIFT_SCALE] within [LIFT_RAMP_DP] of travel, 1 at rest. */
    fun scale(
        offsetPx: Float,
        density: Float,
    ): Float = 1f + (LIFT_SCALE - 1f) * (abs(offsetPx) / (LIFT_RAMP_DP * density)).coerceIn(0f, 1f)

    /** The "Dismiss" cue's opacity: nothing before the threshold, full [CUE_RAMP] of the width past it. */
    fun cueAlpha(
        offsetPx: Float,
        widthPx: Float,
    ): Float {
        if (!isArmed(offsetPx, widthPx)) return 0f
        return ((abs(offsetPx) / widthPx - DISTANCE_FRACTION) / CUE_RAMP).coerceIn(0f, 1f)
    }

    /**
     * The opacity of the cue a card that resists draws in the room it uncovers ("Ongoing", "Can't dismiss"): it comes in
     * with the rubber band's own travel, nothing at rest and full once the card has given [RESIST_MAX_DP] (it never
     * gives more, so the cue is the same for every kind of card that resists).
     */
    fun resistCueAlpha(
        offsetPx: Float,
        density: Float,
    ): Float = if (density <= 0f) 0f else (abs(offsetPx) / (RESIST_MAX_DP * density)).coerceIn(0f, 1f)

    /** A dismissing card lifts to this scale. */
    const val LIFT_SCALE = 1.02f

    /** The travel (dp) over which the lift comes in. */
    const val LIFT_RAMP_DP = 24f

    /** The share of the width, past the threshold, over which the cue comes to full opacity. */
    const val CUE_RAMP = 0.06f

    /**
     * What releasing at [offsetPx] (positive: right) with the finger moving at [velocityPxPerSecond] does to a card
     * [widthPx] wide, on a screen of [density] px per dp: far enough, or fast enough the way it was taken, dismisses;
     * anything else, including a flick back toward where it started, settles back.
     */
    fun decide(
        offsetPx: Float,
        widthPx: Float,
        velocityPxPerSecond: Float,
        density: Float,
    ): Outcome {
        val travel = abs(offsetPx)
        val far = widthPx > 0f && travel >= widthPx * DISTANCE_FRACTION
        val sameWay = offsetPx == 0f || velocityPxPerSecond == 0f || sign(offsetPx) == sign(velocityPxPerSecond)
        val fast =
            abs(velocityPxPerSecond) >= FLING_VELOCITY_DP_PER_SECOND * density &&
                travel >= MIN_FLING_TRAVEL_DP * density && sameWay
        return if ((far && sameWay) || fast) Outcome.DISMISS else Outcome.SETTLE
    }

    /** The card's opacity at [offsetPx] of travel: 1 at rest, [MIN_ALPHA] a full width away, no less. */
    fun alpha(
        offsetPx: Float,
        widthPx: Float,
    ): Float {
        if (widthPx <= 0f) return 1f
        val travel = (abs(offsetPx) / widthPx).coerceIn(0f, 1f)
        return 1f - (1f - MIN_ALPHA) * travel
    }

    /** Where a card sent off ends, off the edge it was taken toward (the side of its offset, else of its velocity). */
    fun flyOffTarget(
        offsetPx: Float,
        velocityPxPerSecond: Float,
        widthPx: Float,
    ): Float {
        val direction =
            if (offsetPx != 0f) {
                sign(offsetPx)
            } else if (velocityPxPerSecond != 0f) {
                sign(velocityPxPerSecond)
            } else {
                1f
            }
        return direction * widthPx * FLY_OFF_WIDTHS
    }
}
