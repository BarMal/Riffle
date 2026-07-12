package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CardExpansionStateTest {
    private val cardId = LauncherCardId("card")

    @Test
    fun expandsAndCollapsesTheSameCard() {
        val expanding = CardExpansionState().expand(cardId)
        val expanded = expanding.complete()
        val collapsing = expanded.collapse()

        assertEquals(CardExpansionState(CardExpansionPhase.EXPANDING, cardId), expanding)
        assertEquals(CardExpansionState(CardExpansionPhase.EXPANDED, cardId), expanded)
        assertEquals(CardExpansionState(CardExpansionPhase.COLLAPSING, cardId), collapsing)
        assertEquals(CardExpansionState(), collapsing.complete())
    }

    @Test
    fun reducedMotionSkipsIntermediatePhases() {
        val expanded = CardExpansionState().expand(cardId, reducedMotion = true)

        assertEquals(CardExpansionState(CardExpansionPhase.EXPANDED, cardId), expanded)
        assertEquals(CardExpansionState(), expanded.collapse(reducedMotion = true))
    }

    @Test
    fun rejectsCardlessNonCollapsedStates() {
        assertFailsWith<IllegalArgumentException> {
            CardExpansionState(phase = CardExpansionPhase.EXPANDED)
        }
    }
}
