package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveStageLiveActiveCardIndexTest {
    @Test
    fun withNoLiveDragReturnsTheCommittedIndexUnchanged() {
        assertEquals(2f, adaptiveStageLiveActiveCardIndex(activeCardIndex = 2, cardCount = 5, liveDragPx = null))
    }

    @Test
    fun tracksTheDragProportionallyUpToOneCardAway() {
        val halfway =
            adaptiveStageLiveActiveCardIndex(
                activeCardIndex = 2,
                cardCount = 5,
                liveDragPx = -ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX / 2f,
            )

        assertEquals(2.5f, halfway)
    }

    @Test
    fun previewsMultipleCardsAwayForADragSeveralThresholdsLong() {
        // CardStackController.settle now skips as many cards as the *release* motion -- fling
        // velocity or plain drag distance -- reaches a further multiple of its own threshold (see
        // its own doc), so this live preview tracks a long drag by that same multiple instead of
        // capping at the immediate neighbor -- otherwise release would visibly jump further than
        // the drag had shown.
        val farDrag =
            adaptiveStageLiveActiveCardIndex(
                activeCardIndex = 5,
                cardCount = 11,
                liveDragPx = -ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX * 4f,
            )

        assertEquals(9f, farDrag)
    }

    @Test
    fun stillClampsToTheStackBoundaryForADragThatWouldOverflowIt() {
        val atTheLastCard =
            adaptiveStageLiveActiveCardIndex(
                activeCardIndex = 4,
                cardCount = 5,
                liveDragPx = -ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX * 2f,
            )

        assertEquals(4f, atTheLastCard)
    }

    @Test
    fun dragInTheOppositeDirectionMovesTowardThePreviousCards() {
        val towardPrevious =
            adaptiveStageLiveActiveCardIndex(
                activeCardIndex = 5,
                cardCount = 11,
                liveDragPx = ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX * 3f,
            )

        assertEquals(2f, towardPrevious)
    }
}
