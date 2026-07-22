package com.riffle.core.domain.launcher.cards

enum class CardStackAnimationProfile(
    val spec: CardStackAnimationSpec,
) {
    FADE(
        CardStackAnimationSpec(
            enteringAlpha = 0f,
            animatesAlpha = true,
        ),
    ),
    SLIDE(
        CardStackAnimationSpec(
            horizontalTravelFraction = 1f,
            animatesHorizontalTranslation = true,
        ),
    ),
    CARD_FLIGHT(
        CardStackAnimationSpec(
            enteringAlpha = CARD_FLIGHT_ALPHA,
            horizontalTravelFraction = 1f,
            verticalTravelFraction = CARD_FLIGHT_VERTICAL_TRAVEL_FRACTION,
            animatesAlpha = true,
            animatesHorizontalTranslation = true,
            animatesVerticalTranslation = true,
            animatesScale = true,
        ),
    ),
    SLIDE_AND_FADE(
        CardStackAnimationSpec(
            enteringAlpha = 0f,
            horizontalTravelFraction = 1f,
            animatesAlpha = true,
            animatesHorizontalTranslation = true,
        ),
    ),
    STACK_REFLOW(
        CardStackAnimationSpec(
            reflowsStack = true,
            animatesAlpha = true,
            animatesHorizontalTranslation = true,
            animatesVerticalTranslation = true,
            animatesScale = true,
            animatesRotation = true,
        ),
    ),
}

data class CardStackAnimationSpec(
    val enteringAlpha: Float = 1f,
    val horizontalTravelFraction: Float = 0f,
    val verticalTravelFraction: Float = 0f,
    val reflowsStack: Boolean = false,
    val animatesAlpha: Boolean = false,
    val animatesHorizontalTranslation: Boolean = false,
    val animatesVerticalTranslation: Boolean = false,
    val animatesScale: Boolean = false,
    val animatesRotation: Boolean = false,
    val durationMillis: Int = DEFAULT_CARD_STACK_ANIMATION_DURATION_MILLIS,
    val enterDurationMillis: Int = durationMillis,
    val settleDurationMillis: Int = durationMillis,
    val easing: CardStackAnimationEasing = CardStackAnimationEasing.STANDARD,
    val springBouncinessPercent: Int = 0,
) {
    init {
        require(enteringAlpha in 0f..1f) { "Entering alpha must be between zero and one." }
        require(horizontalTravelFraction >= 0f) { "Horizontal travel must not be negative." }
        require(verticalTravelFraction >= 0f) { "Vertical travel must not be negative." }
        require(durationMillis > 0) { "Animation duration must be positive." }
        require(enterDurationMillis > 0) { "Enter animation duration must be positive." }
        require(settleDurationMillis > 0) { "Settle animation duration must be positive." }
        require(springBouncinessPercent in 0..100) { "Spring bounciness must be a percentage." }
    }
}

/** Renderer-neutral interpolation intent used by card stack implementations. */
enum class CardStackAnimationEasing { STANDARD, EMPHASIZED, GENTLE_SPRING }

const val DEFAULT_CARD_STACK_ANIMATION_DURATION_MILLIS = 220
const val CARD_FLIGHT_ALPHA = 0.65f
const val CARD_FLIGHT_VERTICAL_TRAVEL_FRACTION = 0.15f
