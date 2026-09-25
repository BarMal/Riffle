package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardStackScrollStepTest {
    private val range = -128f..128f

    @Test
    fun aStepInsideTheRangeConsumesTheWholeDelta() {
        val step = cardStackScrollStep(position = 0f, delta = -20f, range = range)

        assertEquals(-20f, step.position)
        assertEquals(-20f, step.consumedDelta)
        assertFalse(step.pinned)
    }

    @Test
    fun aStepPastTheLastCardLeavesTheRemainderUnconsumed() {
        val step = cardStackScrollStep(position = -120f, delta = -20f, range = range)

        assertEquals(-128f, step.position)
        assertEquals(-8f, step.consumedDelta)
        assertTrue(step.pinned)
    }

    @Test
    fun aStepAgainstAnEndAlreadyReachedConsumesNothing() {
        val step = cardStackScrollStep(position = 128f, delta = 15f, range = range)

        assertEquals(128f, step.position)
        assertEquals(0f, step.consumedDelta)
        assertTrue(step.pinned)
    }

    @Test
    fun aSingleCardStackConsumesNothing() {
        val step = cardStackScrollStep(position = 0f, delta = -30f, range = 0f..0f, slopCredit = -21f)

        assertEquals(0f, step.position)
        assertEquals(0f, step.consumedDelta)
        assertTrue(step.pinned)
    }

    @Test
    fun theSlopCreditMovesThePositionButIsNeverReportedAsConsumed() {
        val step = cardStackScrollStep(position = 0f, delta = -5f, range = range, slopCredit = -21f)

        assertEquals(-26f, step.position)
        assertEquals(-5f, step.consumedDelta)
    }

    @Test
    fun theSlopCreditAloneCanPinWithoutTheDeltaBeingConsumedTwice() {
        val step = cardStackScrollStep(position = 120f, delta = 5f, range = range, slopCredit = 21f)

        assertEquals(128f, step.position)
        assertEquals(0f, step.consumedDelta)
    }

    @Test
    fun anUnboundedStackConsumesEverything() {
        val step = cardStackScrollStep(position = 500f, delta = 40f, range = null)

        assertEquals(540f, step.position)
        assertEquals(40f, step.consumedDelta)
        assertFalse(step.pinned)
    }

    @Test
    fun onlyAStackWithSomewhereToGoOwnsTheDrag() {
        assertFalse(cardStackOwnsDrag(0))
        assertFalse(cardStackOwnsDrag(1))
        assertTrue(cardStackOwnsDrag(2))
    }
}
