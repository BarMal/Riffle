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
