package com.riffle.core.domain.launcher.cards

import kotlin.math.abs

data class CardStackLayoutPolicy(
    val maxVisibleDepth: Int = DEFAULT_CARD_STACK_MAX_VISIBLE_DEPTH,
    val scaleStep: Float = DEFAULT_CARD_STACK_SCALE_STEP,
    val offsetStep: Float = DEFAULT_CARD_STACK_OFFSET_STEP,
    val alphaStep: Float = DEFAULT_CARD_STACK_ALPHA_STEP,
    val reducedMotionScaleStep: Float = DEFAULT_CARD_STACK_REDUCED_MOTION_SCALE_STEP,
    val reducedMotionOffsetStep: Float = DEFAULT_CARD_STACK_REDUCED_MOTION_OFFSET_STEP,
) {
    init {
        require(maxVisibleDepth >= 0) { "Maximum visible depth must not be negative." }
        require(scaleStep >= 0f) { "Scale step must not be negative." }
        require(offsetStep >= 0f) { "Offset step must not be negative." }
        require(alphaStep >= 0f) { "Alpha step must not be negative." }
        require(reducedMotionScaleStep >= 0f) { "Reduced-motion scale step must not be negative." }
        require(reducedMotionOffsetStep >= 0f) { "Reduced-motion offset step must not be negative." }
    }

    fun entries(
        cardCount: Int,
        activeIndex: Int,
        reducedMotion: Boolean = false,
    ): List<CardStackLayoutEntry> {
        require(cardCount >= 0) { "Card count must not be negative." }
        if (cardCount == 0) {
            return emptyList()
        }

        val focusedIndex = activeIndex.coerceIn(0, cardCount - 1)
        val visibleIndexes =
            (0 until cardCount).filter { cardIndex ->
                cardIndex.depthFrom(focusedIndex) <= maxVisibleDepth
            }
        val orderedIndexes =
            visibleIndexes.sortedWith(
                compareByDescending<Int> { cardIndex -> cardIndex.depthFrom(focusedIndex) }
                    .thenBy { cardIndex -> cardIndex },
            )

        return orderedIndexes.mapIndexed { order, cardIndex ->
            val depth = cardIndex.depthFrom(focusedIndex)
            val signedDistance = cardIndex - focusedIndex
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
                depth = depth,
                scale = (1f - activeScaleStep * depth).coerceAtLeast(0f),
                offset = activeOffsetStep * signedDistance,
                alpha = (1f - alphaStep * depth).coerceIn(0f, 1f),
            )
        }
    }

    private fun Int.depthFrom(activeIndex: Int): Int = abs(this - activeIndex)
}

data class CardStackLayoutEntry(
    val cardIndex: Int,
    val order: Int,
    val depth: Int,
    val scale: Float,
    val offset: Float,
    val alpha: Float,
)

const val DEFAULT_CARD_STACK_MAX_VISIBLE_DEPTH = 3
const val DEFAULT_CARD_STACK_SCALE_STEP = 0.06f
const val DEFAULT_CARD_STACK_OFFSET_STEP = 24f
const val DEFAULT_CARD_STACK_ALPHA_STEP = 0.16f
const val DEFAULT_CARD_STACK_REDUCED_MOTION_SCALE_STEP = 0.01f
const val DEFAULT_CARD_STACK_REDUCED_MOTION_OFFSET_STEP = 2f
