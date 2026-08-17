package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a stack's focused card sits along its own settle axis -- the alignment half of the
 * reference "Calm" launcher's `stackPeakPosition`.
 */
class CardStackPeakAlignmentTest {
    @Test
    fun theCenteredFractionLeavesBothAxesUnbiased() {
        // This surface's prior fixed behavior was a plain Alignment.Center, so the default has to
        // reproduce it exactly rather than merely land near it.
        val alignment =
            cardStackPeakAlignment(CENTERED_CARD_STACK_PEAK_FRACTION, CardStackOrientation.VERTICAL)

        assertEquals(0f, alignment.horizontalBias, 0.0001f)
        assertEquals(0f, alignment.verticalBias, 0.0001f)
    }

    @Test
    fun aPeakAboveCenterBiasesTheSettleAxisTowardTheStart() {
        // Calm's own default: the focused card sits a fifth of the way down, so the room below it
        // belongs to the cards still to come. A BiasAlignment bias runs -1..1 across the axis and
        // places the child's centre, so a fifth of the way down is -0.6.
        val alignment = cardStackPeakAlignment(0.2f, CardStackOrientation.VERTICAL)

        assertEquals(-0.6f, alignment.verticalBias, 0.0001f)
        assertEquals(0f, alignment.horizontalBias, 0.0001f)
    }

    @Test
    fun aHorizontalStackTakesThePeakOnItsOwnAxisInstead() {
        // HORIZONTAL rotates the whole stack 90 degrees, so the peak has to follow the settle axis
        // rather than staying stuck to the screen's vertical.
        val alignment = cardStackPeakAlignment(0.2f, CardStackOrientation.HORIZONTAL)

        assertEquals(-0.6f, alignment.horizontalBias, 0.0001f)
        assertEquals(0f, alignment.verticalBias, 0.0001f)
    }

    @Test
    fun aFractionOutsideTheStackIsHeldToItsEdges() {
        // Unlike Calm -- whose equivalent padding is clamped against the real card height after the
        // fact -- nothing downstream of this bias would rein in an out-of-range value, so it would
        // park the focused card partly off its own stack.
        assertEquals(
            1f,
            cardStackPeakAlignment(4f, CardStackOrientation.VERTICAL).verticalBias,
            0.0001f,
        )
        assertEquals(
            -1f,
            cardStackPeakAlignment(-4f, CardStackOrientation.VERTICAL).verticalBias,
            0.0001f,
        )
    }
}
