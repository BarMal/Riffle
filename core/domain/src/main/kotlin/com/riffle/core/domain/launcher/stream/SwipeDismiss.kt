package com.riffle.core.domain.launcher.stream

import kotlin.math.abs
import kotlin.math.sign

/**
 * Swiping a notification's card sideways to dismiss it (ADR 0003, Amendment 1; device fixes, round 5, item 2), as pure
 * rules: where the gesture exists, when a release dismisses and how the card looks on the way.
 *
 * The rule that settles the conflict with swiping between the Stream's pages: a horizontal drag is the card's only
 * where it starts on the front card of a notification that [StreamActionPolicy] offers Dismiss for ([claimsDrag]);
 * anywhere else on the page (the strips, the empty room, a notification that cannot be dismissed, any other kind of
 * card) it is the pager's, so a page whose front card is dismissible is still left by swiping on its strips or its
 * room.
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

    /** What a release does. */
    enum class Outcome { DISMISS, SETTLE }

    /** Whether [content] can be dismissed by a swipe: only a notification that the policy offers Dismiss for. */
    fun offers(content: StreamContent): Boolean =
        content is NotificationContent && StreamActionPolicy.offers(content, StreamAction.Dismiss)

    /**
     * Whether a horizontal drag that starts on a card is that card's: only the front card of a dismissible
     * notification ([content] is the card's content, null for a card that is no content). Everything else pages.
     */
    fun claimsDrag(
        content: StreamContent?,
        isFront: Boolean,
    ): Boolean = isFront && content != null && offers(content)

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
