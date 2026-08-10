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
    fun doesNotPreviewPastTheImmediateNeighborEvenForAMuchLongerDrag() {
        // A non-fling release only ever lands on the immediate neighbor (or not at all) --
        // CardStackController.settle skips more than one card solely on a genuine fling, decided
        // by velocity at release. A drag several multiples of the threshold long must not preview
        // further than that one-card reach, or release visibly springs most of the way back to
        // where it started once the (unchanged) committed index reasserts itself.
        val farDrag =
            adaptiveStageLiveActiveCardIndex(
                activeCardIndex = 2,
                cardCount = 5,
                liveDragPx = -ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX * 4f,
            )

        assertEquals(3f, farDrag)
    }

    @Test
    fun stillClampsToTheStackBoundaryWhenTheNeighborCapWouldOverflowIt() {
        val atTheLastCard =
            adaptiveStageLiveActiveCardIndex(
                activeCardIndex = 4,
                cardCount = 5,
                liveDragPx = -ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX * 2f,
            )

        assertEquals(4f, atTheLastCard)
    }

    @Test
    fun dragInTheOppositeDirectionMovesTowardThePreviousCard() {
        val towardPrevious =
            adaptiveStageLiveActiveCardIndex(
                activeCardIndex = 2,
                cardCount = 5,
                liveDragPx = ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX * 3f,
            )

        assertEquals(1f, towardPrevious)
    }
}
