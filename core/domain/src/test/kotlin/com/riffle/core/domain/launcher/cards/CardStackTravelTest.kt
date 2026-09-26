package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [CardStackTravel] and [cardStackProjectedSettleIndex] decide how many cards a gesture moves. The
 * contract under test: the same *physical* gesture (in dp) moves the same number of cards at every
 * density, a slow swipe moves one card, and a fast flick moves at most a few.
 */
class CardStackTravelTest {
    private val densities = listOf(2.0f, 2.6f, 3.5f)

    @Test
    fun travelPerCardNeverDropsBelowTheMinimum() {
        listOf(0f, 36f, -8f).forEach { pitch ->
            assertEquals(MIN_CARD_STACK_TRAVEL_PER_CARD_DP, CardStackTravel.resolve(pitch).distancePerCardDp)
        }
    }

    @Test
    fun aWidelySpacedStackMovesOneCardPerRenderedPitch() {
        assertEquals(120f, CardStackTravel.resolve(renderedCardPitchDp = 120f).distancePerCardDp)
    }

    @Test
    fun pixelTravelScalesWithDensity() {
        val travel = CardStackTravel.resolve(renderedCardPitchDp = 0f)

        assertEquals(144f, travel.distancePerCardPx(2.0f), 0.001f)
        assertEquals(187.2f, travel.distancePerCardPx(2.6f), 0.001f)
        assertEquals(252f, travel.distancePerCardPx(3.5f), 0.001f)
    }

    @Test
    fun theFlingThresholdIsAMultipleOfThePlatformMinimumWithAFloor() {
        assertEquals(400f, CardStackTravel.resolve(0f, 50f).flingVelocityThresholdDpPerSecond)
        assertEquals(
            MIN_CARD_STACK_FLING_VELOCITY_THRESHOLD_DP_PER_SECOND,
            CardStackTravel.resolve(0f, 1f).flingVelocityThresholdDpPerSecond,
        )
        assertEquals(1_040f, CardStackTravel.resolve(0f).flingVelocityThresholdPxPerSecond(2.6f), 0.01f)
    }

    @Test
    fun aSlowSwipeMovesExactlyOneCardAtEveryDensity() {
        densities.forEach { density ->
            // 30dp of finger travel released slowly: under half a card, but drag and speed together
            // are deliberate enough to commit one card.
            assertEquals(4, project(density, dragDp = -30f, velocityDpPerSecond = -150f), "density $density")
            assertEquals(2, project(density, dragDp = 30f, velocityDpPerSecond = 150f), "density $density")
        }
    }

    @Test
    fun aSmallNudgeSnapsBackAtEveryDensity() {
        densities.forEach { density ->
            assertEquals(3, project(density, dragDp = -12f, velocityDpPerSecond = -20f), "density $density")
        }
    }

    @Test
    fun aSlowDragAcrossSeveralCardsLandsOnTheNearestOneItPreviewed() {
        densities.forEach { density ->
            // 2.4 cards' worth of dragging, released without momentum.
            assertEquals(5, project(density, dragDp = -72f * 2.4f, velocityDpPerSecond = 0f), "density $density")
            assertEquals(1, project(density, dragDp = 72f * 2.4f, velocityDpPerSecond = 0f), "density $density")
        }
    }

    @Test
    fun aModerateFlickMovesOneCardAtEveryDensity() {
        densities.forEach { density ->
            assertEquals(4, project(density, dragDp = -20f, velocityDpPerSecond = -600f), "density $density")
            assertEquals(2, project(density, dragDp = 20f, velocityDpPerSecond = 600f), "density $density")
        }
    }

    @Test
    fun aFastFlickIsCappedAtAFewCardsAtEveryDensity() {
        densities.forEach { density ->
            assertEquals(3 + MAX_FLING_STEP_COUNT, project(density, dragDp = -20f, velocityDpPerSecond = -20_000f))
            assertEquals(3 + 2, project(density, dragDp = -20f, velocityDpPerSecond = -2_000f))
        }
    }

    @Test
    fun theFlingCardCountRisesWithSpeedAndIsCapped() {
        val counts = (0..40).map { step -> cardStackFlingCardCount(step * 250f, flingVelocityThreshold = 400f) }

        assertEquals(counts.sorted(), counts)
        assertEquals(0, counts.first())
        assertEquals(MAX_FLING_STEP_COUNT, counts.last())
        assertTrue(counts.all { count -> count <= MAX_FLING_STEP_COUNT })
    }

    @Test
    fun aFlingCountsFromTheFirstBoundaryAheadOfTheReleasePosition() {
        // Released 0.9 of a card forward and flung forward once: the next boundary is card 4.
        assertEquals(
            4,
            cardStackProjectedSettleIndex(
                anchorIndex = 3,
                cardCount = 10,
                scrollOffset = -0.9f * 100f,
                velocity = -500f,
                distancePerCard = 100f,
                flingVelocityThreshold = 400f,
            ),
        )
        // Released exactly on card 3 and flung backward once: card 2.
        assertEquals(
            2,
            cardStackProjectedSettleIndex(
                anchorIndex = 3,
                cardCount = 10,
                scrollOffset = 0f,
                velocity = 500f,
                distancePerCard = 100f,
                flingVelocityThreshold = 400f,
            ),
        )
    }

    @Test
    fun projectionIsClampedToTheStack() {
        assertEquals(2, cardStackProjectedSettleIndex(1, 3, -50f, -99_000f, 100f, 400f))
        assertEquals(0, cardStackProjectedSettleIndex(1, 3, 50f, 99_000f, 100f, 400f))
        assertEquals(0, cardStackProjectedSettleIndex(0, 0, 0f, -99_000f, 100f, 400f))
    }

    @Test
    fun aMagnetizedProjectionCommitsTheSameCardThroughTheController() {
        // The renderer settles on the projected card and hands the controller that exact multiple of
        // a non-integer px travel; the controller must land on the same card.
        densities.forEach { density ->
            val travel = CardStackTravel.resolve(renderedCardPitchDp = 0f)
            val perCardPx = travel.distancePerCardPx(density)
            val cardIds = List(12) { index -> LauncherCardId("card-$index") }
            (1..MAX_FLING_STEP_COUNT).forEach { steps ->
                val state = CardStackFocusState(CardStackKey("travel-test"), cardIds[2])
                val result =
                    CardStackController().settle(
                        state,
                        cardIds,
                        CardStackSettleRequest(
                            focusedCardId = cardIds[2],
                            verticalDragPx = -steps.toFloat() * perCardPx,
                            verticalVelocityPxPerSecond = 0f,
                            distanceThresholdPx = perCardPx,
                            flingVelocityThresholdPxPerSecond = travel.flingVelocityThresholdPxPerSecond(density),
                        ),
                    )
                assertEquals(cardIds[2 + steps], (result as CardStackFocusResult.Applied).state.focusedCardId)
            }
        }
    }

    @Test
    fun invalidInputsAreRejected() {
        assertFailsWith<IllegalArgumentException> { CardStackTravel(0f, 1f) }
        assertFailsWith<IllegalArgumentException> { CardStackTravel(1f, 0f) }
        assertFailsWith<IllegalArgumentException> { CardStackTravel.resolve(0f).distancePerCardPx(0f) }
    }

    private fun project(
        density: Float,
        dragDp: Float,
        velocityDpPerSecond: Float,
        anchorIndex: Int = 3,
        cardCount: Int = 10,
    ): Int {
        val travel = CardStackTravel.resolve(renderedCardPitchDp = 0f)
        return cardStackProjectedSettleIndex(
            anchorIndex = anchorIndex,
            cardCount = cardCount,
            scrollOffset = dragDp * density,
            velocity = velocityDpPerSecond * density,
            distancePerCard = travel.distancePerCardPx(density),
            flingVelocityThreshold = travel.flingVelocityThresholdPxPerSecond(density),
            maxFlingCards = travel.maxFlingCards,
        )
    }
}
