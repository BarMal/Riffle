package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockDropToPairTest {
    // Icons of 48 px in slots of 58 px (10 px gap), five items.
    private fun hover(
        origin: Int,
        dragged: Float,
    ) = dockHover(origin, dragged, slotPx = 58f, iconPx = 48f, itemCount = 5)

    @Test
    fun theMiddleHalfOfATileIsOnItAndTheRestOfItAndTheGapsReorder() {
        assertEquals(DockHover(1, true), hover(origin = 0, dragged = 58f))
        assertEquals(DockHover(1, true), hover(origin = 0, dragged = 58f + 11f))
        assertEquals(DockHover(1, true), hover(origin = 0, dragged = 58f - 11f))
        assertEquals(DockHover(1, false), hover(origin = 0, dragged = 58f + 13f))
        assertEquals(DockHover(1, false), hover(origin = 0, dragged = 58f - 13f))
        // Between two icons, over the gap: nearer slot 1 than 2, not over its middle.
        assertEquals(DockHover(2, false), hover(origin = 0, dragged = 58f * 1.5f + 1f))
    }

    @Test
    fun theHoverStaysOnTheRunAndIsNullWithNoSlots() {
        assertEquals(DockHover(0, false), hover(origin = 0, dragged = -500f))
        assertEquals(DockHover(4, false), hover(origin = 0, dragged = 5_000f))
        assertNull(dockHover(0, 10f, 58f, 48f, itemCount = 0))
    }

    @Test
    fun thereIsACandidateOnlyOverTheMiddleOfAnotherPairableItem() {
        val pairable = listOf("a", "b", null, "d", null)

        assertEquals("b", dockPairCandidate(DockHover(1, true), originIndex = 0, pairable = pairable))
        assertNull(dockPairCandidate(DockHover(1, false), 0, pairable))
        // Nothing is dropped onto a pair, a folder or a widget (null), nor onto the dragged icon itself.
        assertNull(dockPairCandidate(DockHover(2, true), 0, pairable))
        assertNull(dockPairCandidate(DockHover(0, true), 0, pairable))
        // A pair, a folder or a widget is not dragged onto anything either.
        assertNull(dockPairCandidate(DockHover(1, true), originIndex = 2, pairable = pairable))
        assertNull(dockPairCandidate(null, 0, pairable))
    }
}
