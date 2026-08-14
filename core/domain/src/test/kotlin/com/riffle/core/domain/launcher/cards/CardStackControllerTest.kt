package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CardStackControllerTest {
    private val controller = CardStackController()
    private val overview = CardStackKey("overview")
    private val chapter = CardStackKey("chapter:mail")
    private val a = LauncherCardId("a")
    private val b = LauncherCardId("b")
    private val c = LauncherCardId("c")
    private val d = LauncherCardId("d")

    @Test
    fun initializationAndRestoreUseCardIdentityInsteadOfAListIndex() {
        val initialized = controller.initialize(overview, listOf(a, b, c)).applied()
        val selected = controller.jumpTo(initialized.state, listOf(a, b, c), b).applied()

        val restored = controller.restore(selected.state, listOf(c, b, a)).applied()

        assertEquals(b, restored.state.focusedCardId)
        assertEquals(overview, restored.state.stackKey)
    }

    @Test
    fun stacksRememberFocusIndependentlyThroughTheirKeys() {
        val overviewState = controller.initialize(overview, listOf(a, b)).applied().state
        val chapterState = controller.initialize(chapter, listOf(c, d)).applied().state

        val changedOverview =
            controller
                .navigate(overviewState, listOf(a, b), CardStackNavigationDirection.NEXT)
                .applied()

        assertEquals(b, changedOverview.state.focusedCardId)
        assertEquals(c, chapterState.focusedCardId)
    }

    @Test
    fun reconciliationPreservesFocusAcrossReorderAndChoosesNearestPriorSurvivorAfterRemoval() {
        val focused =
            controller
                .jumpTo(
                    controller.initialize(overview, listOf(a, b, c, d)).applied().state,
                    listOf(a, b, c, d),
                    c,
                ).applied()
                .state

        val reordered = controller.reconcile(focused, listOf(a, b, c, d), listOf(d, c, a, b)).applied()
        val removed = controller.reconcile(focused, listOf(a, b, c, d), listOf(a, b, d)).applied()

        assertEquals(c, reordered.state.focusedCardId)
        assertEquals(b, removed.state.focusedCardId)
    }

    @Test
    fun reconciliationHandlesEmptyContentAndPreservesASurvivingCard() {
        val focused =
            controller
                .jumpTo(
                    controller.initialize(overview, listOf(a, b, c)).applied().state,
                    listOf(a, b, c),
                    b,
                ).applied()
                .state

        val empty = controller.reconcile(focused, listOf(a, b, c), emptyList()).applied()
        val surviving = controller.reconcile(focused, listOf(a, b, c), listOf(LauncherCardId("new"), c)).applied()

        assertEquals(null, empty.state.focusedCardId)
        assertEquals(c, surviving.state.focusedCardId)
    }

    @Test
    fun reconciliationClearsFocusWhenNoPriorCardSurvives() {
        val firstReplacement = LauncherCardId("replacement-one")
        val secondReplacement = LauncherCardId("replacement-two")
        val focused =
            controller
                .jumpTo(
                    controller.initialize(overview, listOf(a, b, c)).applied().state,
                    listOf(a, b, c),
                    b,
                ).applied()
                .state

        val replaced =
            controller
                .reconcile(
                    state = focused,
                    previousCardIds = listOf(a, b, c),
                    cardIds = listOf(firstReplacement, secondReplacement),
                ).applied()
        val firstNext =
            controller
                .navigate(
                    state = replaced.state,
                    cardIds = listOf(firstReplacement, secondReplacement),
                    direction = CardStackNavigationDirection.NEXT,
                ).applied()

        assertEquals(null, replaced.state.focusedCardId)
        assertEquals(firstReplacement, firstNext.state.focusedCardId)
        assertEquals(false, firstNext.boundaryReached)
    }

    @Test
    fun navigationStopsAtBoundariesAndDoesNotCycle() {
        val initial = controller.initialize(overview, listOf(a, b)).applied().state

        val previous = controller.navigate(initial, listOf(a, b), CardStackNavigationDirection.PREVIOUS).applied()
        val next = controller.navigate(initial, listOf(a, b), CardStackNavigationDirection.NEXT).applied()
        val lastNext = controller.navigate(next.state, listOf(a, b), CardStackNavigationDirection.NEXT).applied()

        assertEquals(a, previous.state.focusedCardId)
        assertEquals(true, previous.boundaryReached)
        assertEquals(b, next.state.focusedCardId)
        assertEquals(true, lastNext.boundaryReached)
        assertEquals(b, lastNext.state.focusedCardId)
    }

    @Test
    fun settleCommitsOneDirectionalFocusChangeFromDragOrFling() {
        val initial = controller.initialize(overview, listOf(a, b, c)).applied().state

        val dragged =
            controller.settle(
                initial,
                listOf(a, b, c),
                CardStackSettleRequest(
                    focusedCardId = a,
                    verticalDragPx = -80f,
                    verticalVelocityPxPerSecond = 0f,
                    distanceThresholdPx = 48f,
                    flingVelocityThresholdPxPerSecond = 1_000f,
                ),
            ).applied()
        val flungBack =
            controller.settle(
                dragged.state,
                listOf(a, b, c),
                CardStackSettleRequest(
                    focusedCardId = b,
                    verticalDragPx = -4f,
                    verticalVelocityPxPerSecond = 1_200f,
                    distanceThresholdPx = 48f,
                    flingVelocityThresholdPxPerSecond = 1_000f,
                ),
            ).applied()

        assertEquals(b, dragged.state.focusedCardId)
        assertEquals(a, flungBack.state.focusedCardId)
    }

    @Test
    fun aHarderFlingSkipsMoreThanOneCard() {
        val initial = controller.initialize(overview, listOf(a, b, c, d)).applied().state

        val flungTwo =
            controller.settle(
                initial,
                listOf(a, b, c, d),
                CardStackSettleRequest(
                    focusedCardId = a,
                    verticalDragPx = -4f,
                    verticalVelocityPxPerSecond = -2_100f,
                    distanceThresholdPx = 48f,
                    flingVelocityThresholdPxPerSecond = 1_000f,
                ),
            ).applied()

        // 2_100f is just over twice the 1_000f fling threshold, so it skips two cards (a -> c),
        // not one (a -> b) the way a plain slow drag-release would.
        assertEquals(c, flungTwo.state.focusedCardId)
    }

    @Test
    fun aHardFlingIsCappedRatherThanEjectingToTheBoundaryFromMidStack() {
        // Without the cap, a fling's step count was velocity / flingThreshold with no upper
        // bound, so a modest flick against a low velocity threshold (e.g. 3000 px/s at 500 px/s
        // threshold = 6 steps) skipped enough cards to reach a boundary from most of a longer
        // stack -- reading as a jump-cut rather than a boosted swipe. With MAX_FLING_STEP_COUNT
        // in place, even an extreme fling advances at most that many cards, so mid-stack flings
        // land somewhere the user can still see the origin card from.
        val e = LauncherCardId("e")
        val f = LauncherCardId("f")
        val g = LauncherCardId("g")
        val cards = listOf(a, b, c, d, e, f, g)
        val initial = controller.initialize(overview, cards).applied().state

        val flungHard =
            controller.settle(
                initial,
                cards,
                CardStackSettleRequest(
                    focusedCardId = a,
                    verticalDragPx = -4f,
                    // 16x the fling threshold -- large enough that an uncapped ratio would skip
                    // clean past the last card in a 7-card stack.
                    verticalVelocityPxPerSecond = -8_000f,
                    distanceThresholdPx = 48f,
                    flingVelocityThresholdPxPerSecond = 500f,
                ),
            ).applied()

        // From the first card, MAX_FLING_STEP_COUNT (3) steps lands on the fourth card, not the
        // last card the way an uncapped 16-step skip would.
        assertEquals(cards[MAX_FLING_STEP_COUNT], flungHard.state.focusedCardId)
        assertEquals(false, flungHard.boundaryReached)
    }

    @Test
    fun aFlingThatWouldOvershootTheStackLandsOnTheLastReachableCardInstead() {
        val initial = controller.initialize(overview, listOf(a, b, c)).applied().state

        val flungPastTheEnd =
            controller.settle(
                initial,
                listOf(a, b, c),
                CardStackSettleRequest(
                    focusedCardId = a,
                    verticalDragPx = -4f,
                    verticalVelocityPxPerSecond = -5_000f,
                    distanceThresholdPx = 48f,
                    flingVelocityThresholdPxPerSecond = 1_000f,
                ),
            ).applied()

        // Even with the fling step count capped, a MAX_FLING_STEP_COUNT-card skip from a
        // 3-card stack's first card still overshoots the last -- clamped to the last card, not
        // a no-op or an out-of-bounds index, and boundaryReached is still true.
        assertEquals(c, flungPastTheEnd.state.focusedCardId)
        assertEquals(true, flungPastTheEnd.boundaryReached)
    }

    @Test
    fun aSlowDragJustPastTheThresholdStillOnlyMovesOneCard() {
        val initial = controller.initialize(overview, listOf(a, b, c, d)).applied().state

        val dragged =
            controller.settle(
                initial,
                listOf(a, b, c, d),
                CardStackSettleRequest(
                    focusedCardId = a,
                    verticalDragPx = -60f,
                    verticalVelocityPxPerSecond = 0f,
                    distanceThresholdPx = 48f,
                    flingVelocityThresholdPxPerSecond = 1_000f,
                ),
            ).applied()

        assertEquals(b, dragged.state.focusedCardId)
    }

    @Test
    fun aSlowDragSeveralThresholdsLongSkipsThatManyCards() {
        val initial = controller.initialize(overview, listOf(a, b, c, d)).applied().state

        // -400px is just over eight times the 48px distance threshold, so it reaches as far as an
        // 8-card skip would -- clamped to this 4-card stack's last card, exactly like a hard fling
        // overshooting the stack does (see the overshoot test above). A sustained drag distance,
        // not just a fling's release velocity, now drives multi-card skips -- matching the live
        // preview a caller renders while the finger is still down.
        val dragged =
            controller.settle(
                initial,
                listOf(a, b, c, d),
                CardStackSettleRequest(
                    focusedCardId = a,
                    verticalDragPx = -400f,
                    verticalVelocityPxPerSecond = 0f,
                    distanceThresholdPx = 48f,
                    flingVelocityThresholdPxPerSecond = 1_000f,
                ),
            ).applied()

        assertEquals(d, dragged.state.focusedCardId)
        assertEquals(true, dragged.boundaryReached)
    }

    @Test
    fun aQuickShortFlickCommitsFromCombinedDragAndVelocityEvenIfNeitherAloneReachesItsThreshold() {
        // Regression: the previous no-op check compared only the drag distance (or, for
        // qualifying flings, only the release velocity) against a single threshold, so a quick
        // short flick that fell short of both -- e.g. 40 px of travel with 300 px/s release
        // velocity against a 64 px / 500 px/s pair -- silently returned no-op. The finger had
        // visibly moved the stack partway toward the next card during the drag itself; the
        // release then snapped it back to the origin.
        val initial = controller.initialize(overview, listOf(a, b, c)).applied().state

        val quickShortFlick =
            controller.settle(
                initial,
                listOf(a, b, c),
                CardStackSettleRequest(
                    focusedCardId = a,
                    verticalDragPx = -40f,
                    verticalVelocityPxPerSecond = -300f,
                    distanceThresholdPx = 64f,
                    flingVelocityThresholdPxPerSecond = 500f,
                ),
            ).applied()

        // 40 / 64 + 300 / 500 = 0.625 + 0.6 = 1.225 -> commits one step forward.
        assertEquals(b, quickShortFlick.state.focusedCardId)
        assertEquals(true, quickShortFlick.focusChanged)
    }

    @Test
    fun cancelledOrShortDragIsANoOpAndBoundarySettleIsReported() {
        val initial = controller.initialize(overview, listOf(a, b)).applied().state

        val cancelled =
            controller.settle(
                initial,
                listOf(a, b),
                CardStackSettleRequest(a, -20f, 0f, 48f, 1_000f),
            ).applied()
        val boundary =
            controller.settle(
                initial,
                listOf(a, b),
                CardStackSettleRequest(a, 80f, 0f, 48f, 1_000f),
            ).applied()

        assertEquals(a, cancelled.state.focusedCardId)
        assertEquals(false, cancelled.focusChanged)
        assertEquals(true, boundary.boundaryReached)
    }

    @Test
    fun staleSettleCannotOverwriteFocusChangedDuringAGesture() {
        val initial = controller.initialize(overview, listOf(a, b, c)).applied().state
        val current = controller.jumpTo(initial, listOf(a, b, c), c).applied().state

        val stale =
            controller.settle(
                current,
                listOf(a, b, c),
                CardStackSettleRequest(a, -80f, 0f, 48f, 1_000f),
            )

        assertEquals(CardStackFocusResult.Rejected(CardStackFocusRejection.STALE_SETTLE), stale)
    }

    @Test
    fun duplicateIdsAndUnknownJumpAreRejectedWithoutThrowing() {
        val duplicate = controller.initialize(overview, listOf(a, a))
        val unknown =
            controller.jumpTo(
                controller.initialize(overview, listOf(a)).applied().state,
                listOf(a),
                b,
            )

        assertEquals(
            CardStackFocusResult.Rejected(CardStackFocusRejection.DUPLICATE_CARD_IDS),
            duplicate,
        )
        assertEquals(CardStackFocusResult.Rejected(CardStackFocusRejection.UNKNOWN_CARD), unknown)
    }

    private fun CardStackFocusResult.applied(): CardStackFocusResult.Applied {
        return assertIs<CardStackFocusResult.Applied>(this)
    }
}
