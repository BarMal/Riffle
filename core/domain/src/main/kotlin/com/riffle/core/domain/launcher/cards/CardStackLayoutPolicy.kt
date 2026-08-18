package com.riffle.core.domain.launcher.cards

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

data class CardStackLayoutPolicy(
    val maxVisibleDepth: Int = DEFAULT_CARD_STACK_MAX_VISIBLE_DEPTH,
    val scaleStep: Float = DEFAULT_CARD_STACK_SCALE_STEP,
    val offsetStep: Float = DEFAULT_CARD_STACK_OFFSET_STEP,
    val focusedGap: Float = 0f,
    val offsetDirection: Float = 1f,
    val alphaStep: Float = DEFAULT_CARD_STACK_ALPHA_STEP,
    val verticalOffsetStep: Float = DEFAULT_CARD_STACK_VERTICAL_OFFSET_STEP,
    /**
     * The total curve displacement reached at [maxVisibleDepth] itself, not a per-depth
     * coefficient -- every nearer depth's own share of that peak eases toward it via
     * [curveProgress] (a smootherstep ramp), so the curve stays visible through the near-to-mid
     * depths instead of being concentrated almost entirely in the last card or two the way
     * multiplying by squared distance did.
     */
    val curveStep: Float = DEFAULT_CARD_STACK_CURVE_STEP,
    val rotationStep: Float = DEFAULT_CARD_STACK_ROTATION_STEP,
    val reducedMotionScaleStep: Float = DEFAULT_CARD_STACK_REDUCED_MOTION_SCALE_STEP,
    val reducedMotionOffsetStep: Float = DEFAULT_CARD_STACK_REDUCED_MOTION_OFFSET_STEP,
    /**
     * Mirrors [offsetDirection] for the vertical axis: -1/1 flips which side (earlier vs. later
     * cards) fans up vs. down, 0 disables vertical fan/curve entirely. Defaults to 1 (today's
     * only prior behavior -- earlier cards fan up, later cards fan down, unconditionally) so every
     * existing caller that doesn't know about this field is unaffected.
     */
    val verticalOffsetDirection: Float = 1f,
    /**
     * How many cards *above* focus -- earlier ones the stack has already scrolled past -- stay
     * visible, independently of [maxVisibleDepth]'s reach in the other direction. `null` (the
     * default) keeps the geometry strictly symmetric about focus, which is this policy's only
     * prior behavior, so every existing caller is unaffected.
     *
     * The reference "Calm" launcher's stack is asymmetric here by design: its `aboveFocusCards`
     * feeds a separate `outgoingVisibleRange`, and every one of its style functions branches on the
     * sign of `visualDepth` to measure an already-passed card against that shorter range instead of
     * against `visibleCards`. The effect is that outgoing cards shrink and fade away over a much
     * tighter span than incoming ones fan in over -- they read as leaving, rather than as the
     * mirror image of the cards still to come.
     *
     * Only *style* is measured against this range -- scale, alpha, rotation and the horizontal fan.
     * The vertical step that positions a card is deliberately left alone, matching Calm, whose
     * outgoing cards also stay at their full layout spacing while their style compresses; pulling
     * them physically closer together would read as the stack bunching up rather than as cards
     * departing.
     */
    val aboveFocusDepth: Int? = null,
) {
    init {
        require(maxVisibleDepth >= 0) { "Maximum visible depth must not be negative." }
        require(aboveFocusDepth == null || aboveFocusDepth >= 0) {
            "Above-focus depth must not be negative."
        }
        require(scaleStep >= 0f) { "Scale step must not be negative." }
        require(offsetStep >= 0f) { "Offset step must not be negative." }
        require(focusedGap >= 0f) { "Focused gap must not be negative." }
        require(offsetDirection in -1f..1f) { "Offset direction must be between -1 and 1." }
        require(alphaStep >= 0f) { "Alpha step must not be negative." }
        require(verticalOffsetStep >= 0f) { "Vertical offset step must not be negative." }
        require(curveStep >= 0f) { "Curve step must not be negative." }
        require(reducedMotionScaleStep >= 0f) { "Reduced-motion scale step must not be negative." }
        require(reducedMotionOffsetStep >= 0f) { "Reduced-motion offset step must not be negative." }
        require(verticalOffsetDirection in -1f..1f) { "Vertical offset direction must be between -1 and 1." }
    }

    fun entries(
        cardCount: Int,
        activeIndex: Int,
        reducedMotion: Boolean = false,
    ): List<CardStackLayoutEntry> = entries(cardCount, activeIndex.toFloat(), reducedMotion)

    /**
     * Same geometry as the [Int] overload, but [activeIndex] can sit *between* two cards -- the
     * fractional part continuously interpolates every entry's scale/offset/verticalOffset/
     * rotation/alpha toward its neighbor's pose, using exactly the same formulas the settled
     * (integer-[activeIndex]) case already uses. This is what lets a live, in-progress drag drive
     * this same computation frame by frame (each card's own depth-relative pose smoothly
     * reflowing as the drag progresses) instead of only updating once a drag settles on a new
     * integer index -- the reference "Calm" launcher's own card stack works the same way: its
     * `style()` recomputes every visible card's scale/translation/alpha from a continuous
     * `visualDepth` derived straight from live scroll position, not a discrete per-card index.
     * The [Int] overload above is exact for an integer input (no interpolation to do), so every
     * existing caller and test is unaffected.
     */
    fun entries(
        cardCount: Int,
        activeIndex: Float,
        reducedMotion: Boolean = false,
    ): List<CardStackLayoutEntry> {
        require(cardCount >= 0) { "Card count must not be negative." }
        if (cardCount == 0) {
            return emptyList()
        }

        val focusedIndex = activeIndex.coerceIn(0f, (cardCount - 1).toFloat())
        val visibleIndexes =
            (0 until cardCount).filter { cardIndex ->
                // How far the stack reaches depends on which side of focus this card falls.
                val reach =
                    if (cardIndex < focusedIndex) effectiveAboveFocusDepth else maxVisibleDepth
                cardIndex.depthFrom(focusedIndex) <= reach
            }
        val orderedIndexes =
            visibleIndexes.sortedWith(
                compareByDescending<Int> { cardIndex -> cardIndex.depthFrom(focusedIndex) }
                    .thenBy { cardIndex -> cardIndex },
            )

        return orderedIndexes.mapIndexed { order, cardIndex ->
            val depth = cardIndex.depthFrom(focusedIndex)
            val signedDistance = cardIndex - focusedIndex
            // Style is measured against whichever range this card's own side of focus uses, by
            // rescaling its depth into the far side's units -- so every step below (which is
            // calibrated against maxVisibleDepth) reaches exactly the same fraction of its total
            // travel at this card's own range as it would have at maxVisibleDepth. A symmetric
            // policy compresses by 1, leaving all of this arithmetically identical to before.
            val styleCompression = styleCompressionFor(signedDistance)
            val styleDepth = depth * styleCompression
            val styleSignedDistance = signedDistance * styleCompression
            val activeScaleStep =
                when {
                    reducedMotion -> reducedMotionScaleStep
                    else -> scaleStep
                }
            val activeOffsetStep =
                when {
                    reducedMotion -> reducedMotionOffsetStep
                    else -> offsetStep
                }

            CardStackLayoutEntry(
                cardIndex = cardIndex,
                order = order,
                depth = depth.roundToInt(),
                scale = (1f - activeScaleStep * styleDepth).coerceAtLeast(0f),
                offset =
                    if (signedDistance == 0f) {
                        0f
                    } else {
                        offsetDirection *
                            (activeOffsetStep * abs(styleSignedDistance) + if (reducedMotion) 0f else focusedGap) *
                            signedDistance.sign
                    },
                verticalOffset =
                    verticalOffsetDirection *
                        (
                            verticalOffsetStep * signedDistance +
                                curveStep * curveProgress(styleDepth) * signedDistance.sign
                        ),
                rotationDegrees = if (reducedMotion) 0f else rotationStep * styleSignedDistance,
                alpha = ((1f - alphaStep * styleDepth) * edgeFadeMultiplier(styleDepth)).coerceIn(0f, 1f),
            )
        }
    }

    fun entries(
        cards: List<LauncherCard>,
        activeCardId: LauncherCardId? = cards.firstOrNull()?.id,
        reducedMotion: Boolean = false,
    ): List<LauncherCardStackLayoutEntry> {
        if (cards.isEmpty()) return emptyList()
        require(cards.map { it.id }.distinct().size == cards.size) { "Card ids must be unique." }
        val focusedIndex = cards.indexOfFirst { it.id == activeCardId }
        require(focusedIndex >= 0) { "Active card must be in the stack." }

        return entries(cards.size, focusedIndex, reducedMotion).map { layout ->
            LauncherCardStackLayoutEntry(card = cards[layout.cardIndex], layout = layout)
        }
    }

    private fun Int.depthFrom(activeIndex: Float): Float = abs(this - activeIndex)

    /**
     * [aboveFocusDepth] resolved against the symmetric default -- how far this policy reaches on
     * the already-passed side of focus.
     */
    private val effectiveAboveFocusDepth: Int
        get() = aboveFocusDepth ?: maxVisibleDepth

    /**
     * What to multiply a card's depth by so that a step calibrated against [maxVisibleDepth]
     * completes over this card's own side's range instead. Always 1 on the incoming side, and 1
     * everywhere when [aboveFocusDepth] is unset -- so a symmetric policy is untouched. Above
     * focus with a shorter range it exceeds 1, which is what makes an outgoing card reach its
     * fully-shrunk, fully-faded pose in fewer cards than an incoming one takes to arrive.
     *
     * A zero above-focus range needs no compression value: every card above focus sits at a depth
     * greater than zero, so the visibility filter has already dropped all of them. A zero
     * [maxVisibleDepth] is degenerate in the other direction -- every step it calibrates is already
     * flat (see [curveProgress] and [edgeFadeMultiplier], which both bail on it) -- and dividing
     * into it would hand outgoing cards a compression of zero, rendering them at the focused card's
     * own full-size, fully-opaque pose.
     */
    private fun styleCompressionFor(signedDistance: Float): Float {
        val aboveRange = effectiveAboveFocusDepth
        val compresses =
            signedDistance < 0f &&
                maxVisibleDepth > 0 &&
                aboveRange > 0 &&
                aboveRange != maxVisibleDepth
        return if (compresses) maxVisibleDepth.toFloat() / aboveRange else 1f
    }

    /**
     * Eased 0..1 progress of [depth] toward [maxVisibleDepth], reaching exactly 1 there -- used to
     * distribute [curveStep]'s configured peak across every nearer depth. A smootherstep ramp
     * (Ken Perlin's improved ease, `6t^5 - 15t^4 + 10t^3`) front-loads less of the curve into the
     * first couple of depths than a plain squared-distance ramp does, so the cascade stays visibly
     * curved through the whole stack instead of concentrating almost all of it into the last card
     * or two. Mirrors the reference "Calm" launcher's own `CardStackTuning.smootherCurve`, which
     * uses the identical polynomial for its own horizontal-path/rotation easing.
     */
    private fun curveProgress(depth: Float): Float {
        if (maxVisibleDepth <= 0) return 0f
        val t = (depth / maxVisibleDepth).coerceIn(0f, 1f)
        return t * t * t * (t * (6f * t - 15f) + 10f)
    }

    /**
     * Extra falloff for the last visible depths, layered on top of [alphaStep]'s own gentle
     * per-depth dim. [alphaStep] alone is tuned to keep the stack readable at a glance, not to
     * reach near-transparent -- a card sitting at [maxVisibleDepth] can still be most of the way
     * opaque, so whatever clips it at the caller's own viewport edge (fanned/rotated entries can
     * translate past their own footprint) reads as an abrupt cut instead of a fade. Ramps from 1
     * (no extra dim) at [maxVisibleDepth] * [EDGE_FADE_START_FRACTION] down to 0 (fully
     * transparent) at [maxVisibleDepth] itself, leaving cards closer to focus untouched.
     */
    private fun edgeFadeMultiplier(depth: Float): Float {
        if (maxVisibleDepth <= 0) return 1f
        // fadeRange is always maxVisibleDepth * (1 - EDGE_FADE_START_FRACTION), positive whenever
        // maxVisibleDepth is, so no separate zero-range guard is needed here.
        val fadeStart = maxVisibleDepth * EDGE_FADE_START_FRACTION
        val fadeRange = maxVisibleDepth - fadeStart
        val depthPastFadeStart = (depth - fadeStart).coerceAtLeast(0f)
        return (1f - depthPastFadeStart / fadeRange).coerceIn(0f, 1f)
    }

    companion object {
        fun forProfile(profile: CardStackLayoutProfile): CardStackLayoutPolicy = profile.policy
    }
}

data class CardStackLayoutEntry(
    val cardIndex: Int,
    val order: Int,
    val depth: Int,
    val scale: Float,
    val offset: Float,
    val verticalOffset: Float = 0f,
    val rotationDegrees: Float = 0f,
    val alpha: Float,
)

data class LauncherCardStackLayoutEntry(
    val card: LauncherCard,
    val layout: CardStackLayoutEntry,
)

const val DEFAULT_CARD_STACK_MAX_VISIBLE_DEPTH = 3
const val DEFAULT_CARD_STACK_SCALE_STEP = 0.06f
const val DEFAULT_CARD_STACK_OFFSET_STEP = 24f
const val DEFAULT_CARD_STACK_ALPHA_STEP = 0.16f
const val DEFAULT_CARD_STACK_VERTICAL_OFFSET_STEP = 0f
const val DEFAULT_CARD_STACK_CURVE_STEP = 0f
const val DEFAULT_CARD_STACK_ROTATION_STEP = 0f
const val DEFAULT_CARD_STACK_REDUCED_MOTION_SCALE_STEP = 0.01f
const val DEFAULT_CARD_STACK_REDUCED_MOTION_OFFSET_STEP = 2f

/** See [CardStackLayoutPolicy.edgeFadeMultiplier]. */
private const val EDGE_FADE_START_FRACTION = 0.5f
