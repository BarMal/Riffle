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
}
