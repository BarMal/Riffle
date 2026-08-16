package com.riffle.app.launcher

import com.riffle.core.domain.launcher.cards.CardStackController
import com.riffle.core.domain.launcher.cards.CardStackFocusResult
import com.riffle.core.domain.launcher.cards.CardStackFocusState
import com.riffle.core.domain.launcher.cards.CardStackKey
import com.riffle.core.domain.launcher.cards.CardStackSettleRequest
import com.riffle.core.domain.launcher.cards.LauncherCardId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure half of [CardStackScroll] -- where a continuously-scrolling position is allowed to
 * reach, and which card it commits to once the fling that drove it has come to rest.
 */
class CardStackScrollTest {
    @Test
    fun scrollRangeReachesTheFirstAndLastCardFromWhereverFocusSits() {
        val scroll = CardStackScroll(cardCount = 5, activeCardIndex = 2, distancePerCardPx = 64f)

        val range = cardStackScrollPxRange(scroll)

        // Negative scroll moves forward, so the last card sits at the low end of the range.
        assertEquals(-128f, range.start)
        assertEquals(128f, range.endInclusive)
    }

    @Test
    fun aSingleCardStackHasNowhereToScrollTo() {
        val range = cardStackScrollPxRange(CardStackScroll(cardCount = 1, activeCardIndex = 0))

        assertEquals(0f, range.start)
        assertEquals(0f, range.endInclusive)
    }

    @Test
    fun aStaleFocusedIndexStillYieldsAReachableRange() {
        // Content reconciliation can shrink the stack under a focused index that has not caught up
        // yet; the scroll must not offer positions outside the cards that actually exist. Index 9
        // of a three-card stack anchors to the last card, which leaves nowhere further forward to
        // go -- the range reaches back to the first card and no further on.
        val range = cardStackScrollPxRange(CardStackScroll(cardCount = 3, activeCardIndex = 9))

        assertEquals(0f, range.start)
        assertEquals(128f, range.endInclusive)
    }

    @Test
    fun anEmptyStackHasNowhereForAScrollToRender() {
        // Surfaces guard against empty content before composing a stack, but the scroll conversion
        // must not produce a negative index if one ever slips through mid-reconciliation.
        assertEquals(
            0f,
            cardStackLiveActiveCardIndex(activeCardIndex = 0, cardCount = 0, liveDragPx = -500f),
        )
    }

    @Test
    fun magnetizingCommitsToTheNearestCardRatherThanTheOneMostRecentlyPassed() {
        val scroll = CardStackScroll(cardCount = 6, activeCardIndex = 1, distancePerCardPx = 64f)

        // A shade past two cards' worth of forward travel from card 1, so the position sits at
        // 3.19 -- nearest is card 3, not the card the scroll most recently swept past.
        assertEquals(3, cardStackSettledCardIndex(scrollPx = -140f, scroll = scroll))
        // Not quite halfway into the next card -- the scroll falls back to where it started.
        assertEquals(1, cardStackSettledCardIndex(scrollPx = -20f, scroll = scroll))
        // Backwards, past halfway into the previous card.
        assertEquals(0, cardStackSettledCardIndex(scrollPx = 40f, scroll = scroll))
    }

    @Test
    fun magnetizingClampsToTheStackBoundary() {
        val scroll = CardStackScroll(cardCount = 4, activeCardIndex = 1, distancePerCardPx = 64f)

        assertEquals(3, cardStackSettledCardIndex(scrollPx = -1_000f, scroll = scroll))
        assertEquals(0, cardStackSettledCardIndex(scrollPx = 1_000f, scroll = scroll))
    }

    @Test
    fun theMagnetizedPositionRendersExactlyTheCardItCommittedTo() {
        val scroll = CardStackScroll(cardCount = 8, activeCardIndex = 3, distancePerCardPx = 64f)

        listOf(-260f, -140f, -20f, 0f, 33f, 150f, 5_000f).forEach { scrollPx ->
            val magnetizedPx = cardStackMagnetizedScrollPx(scrollPx, scroll)
            val settledIndex = cardStackSettledCardIndex(scrollPx, scroll)

            assertEquals(
                "magnetized $scrollPx should render card $settledIndex",
                settledIndex.toFloat(),
                cardStackLiveActiveCardIndex(
                    activeCardIndex = scroll.activeCardIndex,
                    cardCount = scroll.cardCount,
                    liveDragPx = magnetizedPx,
                    distancePerCardPx = scroll.distancePerCardPx,
                ),
            )
        }
    }

    @Test
    fun aMagnetizedPositionCommitsThatSameCardThroughTheRealController() {
        // The seam the whole continuous-scroll model rests on: CardStack reports the *magnetized*
        // distance with zero velocity, and CardStackController.settle -- untouched by any of this,
        // and still measuring against the same per-card distance -- has to resolve it to precisely
        // the card the scroll stopped on. If these two ever disagreed, a fling would visibly land
        // on one card and commit a different one.
        val scroll = CardStackScroll(cardCount = 9, activeCardIndex = 4, distancePerCardPx = 64f)

        listOf(-400f, -190f, -140f, -33f, 0f, 20f, 96f, 260f, 9_000f).forEach { scrollPx ->
            assertEquals(
                "scroll position $scrollPx",
                cardStackSettledCardIndex(scrollPx, scroll),
                committedCardIndex(scrollPx, scroll),
            )
        }
    }

    @Test
    fun aMagnetizedPositionCommitsThatSameCardAtTheOtherTunedPerCardDistance() {
        // The generated-notifications stack uses a shorter per-card distance than the notification
        // stacks do; the magnetize/commit agreement above must not be an artifact of 64f dividing
        // evenly into anything in particular.
        val scroll = CardStackScroll(cardCount = 7, activeCardIndex = 2, distancePerCardPx = 48f)

        listOf(-200f, -100f, -25f, 0f, 30f, 110f).forEach { scrollPx ->
            assertEquals(
                "scroll position $scrollPx",
                cardStackSettledCardIndex(scrollPx, scroll),
                committedCardIndex(scrollPx, scroll),
            )
        }
    }

    @Test
    fun comingToRestOnTheStartingCardCommitsNothing() {
        val scroll = CardStackScroll(cardCount = 5, activeCardIndex = 2, distancePerCardPx = 64f)

        assertEquals(0f, cardStackMagnetizedScrollPx(scrollPx = -12f, scroll = scroll))
        assertEquals(2, committedCardIndex(scrollPx = -12f, scroll = scroll))
    }

    @Test
    fun aLongFlingIsNotCappedToAFewCardsTheWayAVelocityFlingWas() {
        // Under the old model a fling's reach was capped (MAX_FLING_STEP_COUNT) because raw release
        // velocity had no on-screen preview to anchor it -- an uncapped velocity-to-step ratio read
        // as a jump cut. A continuous scroll has no such problem: every card it crosses is rendered
        // on the way past, and the distance is the fling's own decay rather than a multiplier. The
        // committed distance is therefore reported as travel, which settle does not cap.
        val scroll = CardStackScroll(cardCount = 20, activeCardIndex = 0, distancePerCardPx = 64f)

        assertEquals(7, committedCardIndex(scrollPx = -64f * 7f, scroll = scroll))
    }

    private fun committedCardIndex(
        scrollPx: Float,
        scroll: CardStackScroll,
    ): Int {
        val cardIds = List(scroll.cardCount) { index -> LauncherCardId("card-$index") }
        val state = CardStackFocusState(CardStackKey("card-stack-scroll-test"), cardIds[scroll.anchorIndex])
        val result =
            CardStackController().settle(
                state,
                cardIds,
                CardStackSettleRequest(
                    focusedCardId = state.focusedCardId,
                    verticalDragPx = cardStackMagnetizedScrollPx(scrollPx, scroll),
                    verticalVelocityPxPerSecond = 0f,
                    distanceThresholdPx = scroll.distancePerCardPx,
                    flingVelocityThresholdPxPerSecond = 500f,
                ),
            )
        return cardIds.indexOf((result as CardStackFocusResult.Applied).state.focusedCardId)
    }
}
