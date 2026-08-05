package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveStagePostureTransitionTest {
    @Test
    fun newPostureReplacesInterruptedTransition() {
        val state =
            AdaptiveStagePostureTransitionState(AdaptiveStagePosture.COMPACT)
                .transitionTo(AdaptiveStagePosture.UNFOLDED)
                .transitionTo(AdaptiveStagePosture.TABLETOP)

        assertEquals(AdaptiveStagePosture.TABLETOP, state.effectivePosture)
        assertEquals(AdaptiveStagePosture.COMPACT, state.settledPosture)
    }

    @Test
    fun settlingCommitsOnlyTheLatestPosture() {
        val state =
            AdaptiveStagePostureTransitionState(AdaptiveStagePosture.COMPACT)
                .transitionTo(AdaptiveStagePosture.UNFOLDED)
                .transitionTo(AdaptiveStagePosture.COMPACT)
                .settle()

        assertEquals(AdaptiveStagePosture.COMPACT, state.settledPosture)
        assertEquals(null, state.pendingPosture)
    }

    @Test
    fun contextReconciliationClearsRemovedCardButRetainsAvailableStage() {
        val context =
            AdaptiveStageInteractionContext(
                selectedStageKey = "personal:mail",
                focusedCardKey = "removed",
                detailCardKey = "removed",
                templateId = "shared",
                scrollOffsetPx = 42,
            )

        assertEquals(
            AdaptiveStageInteractionContext(
                selectedStageKey = "personal:mail",
                templateId = "shared",
                scrollOffsetPx = 42,
            ),
            context.reconcile(
                availableStageKeys = setOf("personal:mail"),
                availableCardKeys = setOf("available"),
            ),
        )
    }

    @Test
    fun contextReconciliationPreservesScrollOffsetForRendererRestoration() {
        val context = AdaptiveStageInteractionContext(scrollOffsetPx = 128)

        assertEquals(
            128,
            context
                .reconcile(availableStageKeys = emptySet(), availableCardKeys = emptySet())
                .scrollOffsetPx,
        )
    }
}
