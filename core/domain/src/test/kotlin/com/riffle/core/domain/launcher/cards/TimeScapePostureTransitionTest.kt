package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeScapePostureTransitionTest {
    @Test
    fun newPostureReplacesInterruptedTransition() {
        val state =
            TimeScapePostureTransitionState(TimeScapePosture.COMPACT)
                .transitionTo(TimeScapePosture.UNFOLDED)
                .transitionTo(TimeScapePosture.TABLETOP)

        assertEquals(TimeScapePosture.TABLETOP, state.effectivePosture)
        assertEquals(TimeScapePosture.COMPACT, state.settledPosture)
    }

    @Test
    fun settlingCommitsOnlyTheLatestPosture() {
        val state =
            TimeScapePostureTransitionState(TimeScapePosture.COMPACT)
                .transitionTo(TimeScapePosture.UNFOLDED)
                .transitionTo(TimeScapePosture.COMPACT)
                .settle()

        assertEquals(TimeScapePosture.COMPACT, state.settledPosture)
        assertEquals(null, state.pendingPosture)
    }

    @Test
    fun contextReconciliationClearsRemovedCardButRetainsAvailableStage() {
        val context =
            TimeScapeInteractionContext(
                selectedStageKey = "personal:mail",
                focusedCardKey = "removed",
                detailCardKey = "removed",
                templateId = "shared",
                scrollOffsetPx = 42,
            )

        assertEquals(
            TimeScapeInteractionContext(
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
}
