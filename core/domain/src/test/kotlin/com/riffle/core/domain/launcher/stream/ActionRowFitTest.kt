package com.riffle.core.domain.launcher.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRowFitTest {
    @Test
    fun theTransportStaysCentredOnlyWhenTheWiderSideHasRoomOnBothSides() {
        // 160 centre, 90 start, 48 end: the wider side (90 + 8) is needed on both sides of the centre.
        assertTrue(ActionRowFit.centredFits(90, 160, 48, availablePx = 360, gapPx = 8))
        assertFalse(ActionRowFit.centredFits(90, 160, 48, availablePx = 355, gapPx = 8))
    }

    @Test
    fun nothingAtTheEndsOnlyNeedsTheGroupToFit() {
        assertTrue(ActionRowFit.centredFits(0, 160, 0, availablePx = 160, gapPx = 8))
        assertFalse(ActionRowFit.centredFits(0, 160, 0, availablePx = 159, gapPx = 8))
    }

    @Test
    fun aSingleSecondaryActionStillReservesItsSpaceOnTheOtherSide() {
        // Only a start action: the centre is centred, so the empty end gives the same room.
        assertFalse(ActionRowFit.centredFits(120, 160, 0, availablePx = 340, gapPx = 8))
        assertTrue(ActionRowFit.centredFits(120, 160, 0, availablePx = 416, gapPx = 8))
    }

    @Test
    fun spreadsEvenlyWithEqualSpaceAtBothEndsAndBetween() {
        assertEquals(listOf(40, 120), ActionRowFit.evenlySpread(listOf(40, 60), availablePx = 220))
        assertEquals(listOf(50), ActionRowFit.evenlySpread(listOf(100), availablePx = 200))
    }

    @Test
    fun aRowThatDoesNotFitIsPackedFromTheStartNeverOffTheEdge() {
        assertEquals(listOf(0, 80), ActionRowFit.evenlySpread(listOf(80, 80), availablePx = 100))
        assertEquals(emptyList<Int>(), ActionRowFit.evenlySpread(emptyList(), availablePx = 100))
    }
}
