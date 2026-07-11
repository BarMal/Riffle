package com.riffle.core.domain.launcher.cards

enum class CardStackAnimationProfile(
    val spec: CardStackAnimationSpec,
) {
    FADE(CardStackAnimationSpec(enteringAlpha = 0f, exitingAlpha = 0f)),
    SLIDE(CardStackAnimationSpec(horizontalTravelFraction = 1f)),
    CARD_FLIGHT(
        CardStackAnimationSpec(
            enteringAlpha = CARD_FLIGHT_ALPHA,
            exitingAlpha = CARD_FLIGHT_ALPHA,
            horizontalTravelFraction = 1f,
            verticalTravelFraction = CARD_FLIGHT_VERTICAL_TRAVEL_FRACTION,
        ),
    ),
    SLIDE_AND_FADE(
        CardStackAnimationSpec(
            enteringAlpha = 0f,
            exitingAlpha = 0f,
            horizontalTravelFraction = 1f,
        ),
    ),
    STACK_REFLOW(CardStackAnimationSpec(reflowsStack = true)),
}

data class CardStackAnimationSpec(
    val enteringAlpha: Float = 1f,
    val exitingAlpha: Float = 1f,
    val horizontalTravelFraction: Float = 0f,
    val verticalTravelFraction: Float = 0f,
    val reflowsStack: Boolean = false,
) {
    init {
        require(enteringAlpha in 0f..1f) { "Entering alpha must be between zero and one." }
        require(exitingAlpha in 0f..1f) { "Exiting alpha must be between zero and one." }
        require(horizontalTravelFraction >= 0f) { "Horizontal travel must not be negative." }
        require(verticalTravelFraction >= 0f) { "Vertical travel must not be negative." }
    }
}

const val CARD_FLIGHT_ALPHA = 0.65f
const val CARD_FLIGHT_VERTICAL_TRAVEL_FRACTION = 0.15f
