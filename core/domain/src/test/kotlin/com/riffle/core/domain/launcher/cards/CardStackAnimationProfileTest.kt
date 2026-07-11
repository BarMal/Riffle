package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CardStackAnimationProfileTest {
    @Test
    fun exposesAllRequestedAnimationProfiles() {
        assertEquals(
            setOf(
                CardStackAnimationProfile.FADE,
                CardStackAnimationProfile.SLIDE,
                CardStackAnimationProfile.CARD_FLIGHT,
                CardStackAnimationProfile.SLIDE_AND_FADE,
                CardStackAnimationProfile.STACK_REFLOW,
            ),
            CardStackAnimationProfile.entries.toSet(),
        )
    }

    @Test
    fun profilesHaveDeterministicTransitionSpecs() {
        assertEquals(
            CardStackAnimationSpec(enteringAlpha = 0f, exitingAlpha = 0f),
            CardStackAnimationProfile.FADE.spec,
        )
        assertEquals(1f, CardStackAnimationProfile.SLIDE.spec.horizontalTravelFraction)
        assertEquals(CARD_FLIGHT_ALPHA, CardStackAnimationProfile.CARD_FLIGHT.spec.enteringAlpha)
        assertEquals(
            CARD_FLIGHT_VERTICAL_TRAVEL_FRACTION,
            CardStackAnimationProfile.CARD_FLIGHT.spec.verticalTravelFraction,
        )
        assertEquals(1f, CardStackAnimationProfile.SLIDE_AND_FADE.spec.horizontalTravelFraction)
        assertEquals(true, CardStackAnimationProfile.STACK_REFLOW.spec.reflowsStack)
    }

    @Test
    fun rejectsInvalidAnimationSpecs() {
        assertFailsWith<IllegalArgumentException> { CardStackAnimationSpec(enteringAlpha = -0.1f) }
        assertFailsWith<IllegalArgumentException> { CardStackAnimationSpec(exitingAlpha = 1.1f) }
        assertFailsWith<IllegalArgumentException> { CardStackAnimationSpec(horizontalTravelFraction = -1f) }
        assertFailsWith<IllegalArgumentException> { CardStackAnimationSpec(verticalTravelFraction = -1f) }
    }
}
