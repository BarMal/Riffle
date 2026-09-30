package com.riffle.app.launcher.expressions

import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardStackFocusStateTest {
    @Test
    fun moveToChangesFocusAndCountsTheSkippedCards() {
        val focus = CardStackFocusState()

        assertTrue(focus.moveTo(target = 3, count = 5))

        assertEquals(3, focus.index)
        assertEquals(3, focus.settleStepCount)
        assertEquals(1, focus.settleTransitionId)
    }

    @Test
    fun moveToTheSameCardDoesNotAdvanceTheTransition() {
        val focus = CardStackFocusState(initialIndex = 2)

        assertFalse(focus.moveTo(target = 2, count = 5))

        assertEquals(0, focus.settleTransitionId)
    }

    @Test
    fun moveToClampsIntoTheStack() {
        val focus = CardStackFocusState()

        focus.moveTo(target = 99, count = 4)

        assertEquals(3, focus.index)
    }

    @Test
    fun stepStopsAtBothEnds() {
        val focus = CardStackFocusState()

        assertFalse(focus.step(CardStackNavigationDirection.PREVIOUS, count = 3))
        assertTrue(focus.step(CardStackNavigationDirection.NEXT, count = 3))
        assertTrue(focus.step(CardStackNavigationDirection.NEXT, count = 3))
        assertFalse(focus.step(CardStackNavigationDirection.NEXT, count = 3))
        assertEquals(2, focus.index)
    }

    @Test
    fun aShrunkStackReclampsAStaleIndex() {
        val focus = CardStackFocusState(initialIndex = 4)

        assertEquals(1, focus.clamped(count = 2))
        assertEquals(0, focus.clamped(count = 0))
    }
}
