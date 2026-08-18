package com.riffle.core.domain.launcher.cards

/**
 * How long a card stack's scroll position is left standing where its own fling stopped, and how
 * firmly it is then pulled onto the nearest card.
 *
 * This is the tunable half of the "magnetize" phase described in `CardStackScroll` -- the step that
 * runs *after* a fling's decay has already come to rest, never a hand-off from one motion to
 * another. The reference "Calm" launcher's card stack has the same two-part behavior and exposes it
 * as a single `magnetStrength` knob: its `CardStackTuning.magnetDelayMillis` debounces the snap
 * behind the last scroll callback, and `magnetSnapThreshold` decides how far from a card the
 * position may rest and still be pulled in at all. Riffle had neither -- the position was pulled
 * onto the nearest card the instant the decay stopped, with one fixed spring, so the whole "the
 * stack sits where you left it for a beat, then eases home" character of Calm's motion was missing.
 *
 * [strengthPercent] drives both outputs from one control, exactly as Calm's own knob does:
 *  - [settleDelayMillis] is Calm's `magnetDelayMillis` verbatim (130ms at the weakest, 40ms at the
 *    strongest). A weak magnet visibly leaves the stack parked where the fling ran out before
 *    tidying up; a strong one takes it home almost immediately.
 *  - [stiffnessScale] multiplies the caller's own base magnetize spring stiffness. This is
 *    deliberately *not* a port of Calm's `magnetSnapThreshold`: there, a rest position further than
 *    the threshold from any card is left un-snapped entirely, which works because Calm's active
 *    index is derived by truncating the live scroll position, so a stack parked between two cards
 *    is still a coherent state. Riffle commits the magnetized position through
 *    [CardStackController.settle] -- durable focus, keyboard focus and accessibility all name the
 *    card the scroll came to rest on -- so declining to snap would strand that focus between two
 *    cards. Scaling how *lazily* the pull happens is the nearest equivalent that keeps the commit
 *    coherent: at the weakest setting the position drifts home slowly enough to read as "it stayed
 *    where I left it", which is what Calm's threshold buys in practice, without inventing a
 *    resting state nothing else in the stack knows how to interpret.
 */
data class CardStackMagnet(
    val strengthPercent: Int = DEFAULT_CARD_STACK_MAGNET_STRENGTH_PERCENT,
) {
    init {
        require(strengthPercent in MIN_CARD_STACK_MAGNET_STRENGTH_PERCENT..MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT) {
            "Magnet strength must be between $MIN_CARD_STACK_MAGNET_STRENGTH_PERCENT and " +
                "$MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT."
        }
    }

    private val strengthFraction: Float
        get() = strengthPercent.toFloat() / MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT

    /** How long the position rests where the decay stopped before the pull home starts. */
    val settleDelayMillis: Long
        get() = lerp(WEAKEST_SETTLE_DELAY_MILLIS, STRONGEST_SETTLE_DELAY_MILLIS, strengthFraction).toLong()

    /** Multiplies the renderer's own base magnetize spring stiffness -- see this class's own doc. */
    val stiffnessScale: Float
        get() = lerp(WEAKEST_STIFFNESS_SCALE, STRONGEST_STIFFNESS_SCALE, strengthFraction)

    private fun lerp(
        from: Float,
        to: Float,
        fraction: Float,
    ): Float = from + (to - from) * fraction.coerceIn(0f, 1f)

    private companion object {
        /** Calm's own `magnetDelayMillis` range, kept verbatim so the debounce feels the same. */
        const val WEAKEST_SETTLE_DELAY_MILLIS = 130f
        const val STRONGEST_SETTLE_DELAY_MILLIS = 40f

        /**
         * Chosen so the *default* strength lands within a few percent of 1.0 -- the renderer's own
         * unscaled base spring, which is the single fixed stiffness this replaced -- so an install
         * that never touches the slider keeps close to the motion it already had, with real range
         * either side of it. The weak end is the further of the two from that default on purpose:
         * an unhurried drift home is a much more visible change in character than an equally
         * proportioned increase in briskness would be, since spring stiffness reads roughly
         * logarithmically.
         */
        const val WEAKEST_STIFFNESS_SCALE = 0.45f
        const val STRONGEST_STIFFNESS_SCALE = 1.25f
    }
}

const val MIN_CARD_STACK_MAGNET_STRENGTH_PERCENT = 0
const val MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT = 100

/** Matches the reference "Calm" launcher's own `CardStackTuning.magnetStrength` default. */
const val DEFAULT_CARD_STACK_MAGNET_STRENGTH_PERCENT = 70
