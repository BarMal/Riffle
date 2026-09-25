package com.riffle.core.domain.launcher.cards

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * How far a finger has to travel along a card stack's settle axis to move one card, and how fast
 * a release has to be to count as a fling -- both in density-independent units, so the same
 * physical gesture moves the same number of cards on a 2.0x phone, a 2.6x phone and a 3.5x tablet.
 *
 * Riffle previously used a fixed 64 *pixels* per card and a 500 px/s fling threshold. On a 2.6x
 * phone that is under 25dp per card, so an ordinary flick skipped many cards; on a 3.5x screen it
 * was tighter still. Deriving both from dp (and the fling threshold from the platform's own
 * minimum fling velocity) is what makes "slow swipe = one card, fast flick = a few" hold across
 * devices.
 *
 * @param distancePerCardDp travel per card; never below [MIN_CARD_STACK_TRAVEL_PER_CARD_DP].
 * @param flingVelocityThresholdDpPerSecond release speed at or above which the gesture is a fling.
 * @param maxFlingCards the most cards one fling may project past the release position.
 */
data class CardStackTravel(
    val distancePerCardDp: Float,
    val flingVelocityThresholdDpPerSecond: Float,
    val maxFlingCards: Int = MAX_FLING_STEP_COUNT,
) {
    init {
        require(distancePerCardDp > 0f) { "Travel per card must be positive." }
        require(flingVelocityThresholdDpPerSecond > 0f) { "Fling velocity threshold must be positive." }
        require(maxFlingCards >= 1) { "A fling must be able to move at least one card." }
    }

    fun distancePerCardPx(density: Float): Float = distancePerCardDp * density.requirePositiveDensity()

    fun flingVelocityThresholdPxPerSecond(density: Float): Float =
        flingVelocityThresholdDpPerSecond * density.requirePositiveDensity()

    companion object {
        /**
         * Resolves the travel for a stack whose cards are rendered [renderedCardPitchDp] apart along
         * the settle axis. A tightly packed stack (a pitch of a few dp) still needs a deliberate
         * [MIN_CARD_STACK_TRAVEL_PER_CARD_DP] of finger travel per card; a widely spaced one moves
         * one card per pitch, so the card under the finger tracks it.
         *
         * [platformMinimumFlingVelocityDpPerSecond] is the platform's minimum fling velocity
         * (`ViewConfiguration.getScaledMinimumFlingVelocity()` divided by density). A card stack
         * wants a clearly intentional flick before it projects past the nearest card, so the
         * threshold is a multiple of that value, floored at
         * [MIN_CARD_STACK_FLING_VELOCITY_THRESHOLD_DP_PER_SECOND] for devices reporting unusually
         * low values.
         */
        fun resolve(
            renderedCardPitchDp: Float,
            platformMinimumFlingVelocityDpPerSecond: Float = DEFAULT_PLATFORM_MINIMUM_FLING_VELOCITY_DP_PER_SECOND,
            maxFlingCards: Int = MAX_FLING_STEP_COUNT,
        ): CardStackTravel =
            CardStackTravel(
                distancePerCardDp = abs(renderedCardPitchDp).coerceAtLeast(MIN_CARD_STACK_TRAVEL_PER_CARD_DP),
                flingVelocityThresholdDpPerSecond =
                    (abs(platformMinimumFlingVelocityDpPerSecond) * CARD_STACK_FLING_THRESHOLD_MULTIPLIER)
                        .coerceAtLeast(MIN_CARD_STACK_FLING_VELOCITY_THRESHOLD_DP_PER_SECOND),
                maxFlingCards = maxFlingCards,
            )
    }
}

/**
 * How many cards a release at [speed] (same unit as [flingVelocityThreshold]) projects past the
 * release position: zero below the threshold, then one extra card per
 * [CARD_STACK_FLING_STEP_VELOCITY_MULTIPLE] thresholds' worth of speed, capped at [maxFlingCards].
 */
fun cardStackFlingCardCount(
    speed: Float,
    flingVelocityThreshold: Float,
    maxFlingCards: Int = MAX_FLING_STEP_COUNT,
): Int {
    require(flingVelocityThreshold > 0f) { "Fling velocity threshold must be positive." }
    val ratio = abs(speed) / flingVelocityThreshold
    if (ratio < 1f) return 0
    return (1 + ((ratio - 1f) / CARD_STACK_FLING_STEP_VELOCITY_MULTIPLE).toInt()).coerceIn(1, maxFlingCards)
}

/**
 * The card a released stack magnetically settles onto. Units are unit-agnostic -- pass px with px
 * thresholds or dp with dp thresholds -- because only ratios are compared.
 *
 * Sign convention matches the renderer's scroll position: negative [scrollOffset] and negative
 * [velocity] move forward (towards higher card indexes).
 *
 *  - **Fling** (speed at or above [flingVelocityThreshold]): the first card boundary ahead of the
 *    release position in the fling's direction, plus up to `maxFlingCards - 1` more depending on
 *    speed -- so a flick never jumps further than a few cards however hard it is.
 *  - **Slow release**: the nearest card, except that a release still resting on its starting card
 *    commits one card when drag and release speed together are deliberate enough (the same
 *    combined-energy rule [CardStackController.settle] uses), so a short purposeful swipe is never
 *    snapped back.
 *
 * The result is always inside `0 until cardCount` (or 0 for an empty stack).
 */
@Suppress("LongParameterList")
fun cardStackProjectedSettleIndex(
    anchorIndex: Int,
    cardCount: Int,
    scrollOffset: Float,
    velocity: Float,
    distancePerCard: Float,
    flingVelocityThreshold: Float,
    maxFlingCards: Int = MAX_FLING_STEP_COUNT,
): Int {
    require(distancePerCard > 0f) { "Distance per card must be positive." }
    if (cardCount <= 0) return 0
    val lastIndex = cardCount - 1
    val anchor = anchorIndex.coerceIn(0, lastIndex)
    val releaseIndex = (anchor - scrollOffset / distancePerCard).coerceIn(0f, lastIndex.toFloat())
    val flingCards = cardStackFlingCardCount(velocity, flingVelocityThreshold, maxFlingCards)
    val target =
        when {
            flingCards > 0 && velocity < 0f ->
                floor(releaseIndex + CARD_STACK_INDEX_EPSILON).toInt() + flingCards
            flingCards > 0 -> ceil(releaseIndex - CARD_STACK_INDEX_EPSILON).toInt() - flingCards
            else -> slowSettleIndex(anchor, releaseIndex, velocity, flingVelocityThreshold)
        }
    return target.coerceIn(0, lastIndex)
}

private fun slowSettleIndex(
    anchor: Int,
    releaseIndex: Float,
    velocity: Float,
    flingVelocityThreshold: Float,
): Int {
    val nearest = releaseIndex.roundToInt()
    if (nearest != anchor) return nearest
    val travelledCards = releaseIndex - anchor
    val energy =
        abs(travelledCards) / CARD_STACK_SLOW_COMMIT_FRACTION + abs(velocity) / flingVelocityThreshold
    // Only commit in the direction the finger actually travelled, and only when it travelled at all.
    val direction =
        when {
            travelledCards > 0f -> 1
            travelledCards < 0f -> -1
            else -> 0
        }
    return if (energy >= 1f) anchor + direction else anchor
}

private fun Float.requirePositiveDensity(): Float {
    require(this > 0f) { "Density must be positive." }
    return this
}

/** Minimum finger travel per card; roughly a thumb's comfortable deliberate movement. */
const val MIN_CARD_STACK_TRAVEL_PER_CARD_DP = 72f

/** AOSP's default `ViewConfiguration` minimum fling velocity (50dp/s). */
const val DEFAULT_PLATFORM_MINIMUM_FLING_VELOCITY_DP_PER_SECOND = 50f

/** A card-stack fling must be this many times the platform's minimum fling velocity. */
const val CARD_STACK_FLING_THRESHOLD_MULTIPLIER = 8f

/** Floor for the fling threshold, whatever the platform reports. */
const val MIN_CARD_STACK_FLING_VELOCITY_THRESHOLD_DP_PER_SECOND = 300f

/** Each additional fling card needs this many further thresholds' worth of release speed. */
const val CARD_STACK_FLING_STEP_VELOCITY_MULTIPLE = 3f

/** Fraction of a card a slow drag must cover on its own to commit (before velocity helps). */
const val CARD_STACK_SLOW_COMMIT_FRACTION = 0.5f

/** Tolerance for float noise when a release sits exactly on a card boundary. */
private const val CARD_STACK_INDEX_EPSILON = 1e-3f
