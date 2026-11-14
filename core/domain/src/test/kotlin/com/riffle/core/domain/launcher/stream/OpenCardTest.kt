package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenCardTest {
    private fun reduce(
        state: OpenCardState,
        vararg events: OpenCardEvent,
    ) = events.fold(state) { acc, event -> OpenCardReducer.reduce(acc, event) }

    private val open = OpenCardState("page-a", "card-1")

    @Test
    fun nothingIsOpenToStartWith() {
        assertFalse(OpenCardState().isOpen)
        assertFalse(OpenCardReducer.takesPressOrBack(OpenCardState()))
    }

    @Test
    fun aTapOnTheFrontCardOpensIt() {
        assertEquals(open, reduce(OpenCardState(), OpenCardEvent.Open("page-a", "card-1")))
        assertTrue(OpenCardReducer.takesPressOrBack(open))
    }

    @Test
    fun aTapOnItAgainAPressOfTheDockAndBackEachCloseIt() {
        assertEquals(OpenCardState(), reduce(open, OpenCardEvent.CloseByTap))
        assertEquals(OpenCardState(), reduce(open, OpenCardEvent.CloseByPress))
        assertEquals(OpenCardState(), reduce(open, OpenCardEvent.CloseByBack))
    }

    @Test
    fun closingWhenNothingIsOpenChangesNothing() {
        assertEquals(OpenCardState(), reduce(OpenCardState(), OpenCardEvent.CloseByPress, OpenCardEvent.CloseByBack))
    }

    @Test
    fun switchingPageClosesTheCardButStayingOnItsPageDoesNot() {
        assertEquals(OpenCardState(), reduce(open, OpenCardEvent.PageChanged("page-b")))
        assertEquals(OpenCardState(), reduce(open, OpenCardEvent.PageChanged(null)))
        assertEquals(open, reduce(open, OpenCardEvent.PageChanged("page-a")))
    }

    @Test
    fun aCardThatGoesAwayClosesAndOneThatStaysDoesNot() {
        assertEquals(open, reduce(open, OpenCardEvent.CardsChanged("page-a", setOf("card-1", "card-2"))))
        assertEquals(OpenCardState(), reduce(open, OpenCardEvent.CardsChanged("page-a", setOf("card-2"))))
        // Another page's cards changing is none of its business.
        assertEquals(open, reduce(open, OpenCardEvent.CardsChanged("page-b", emptySet())))
    }

    @Test
    fun theStreamLeavingClosesIt() {
        assertEquals(OpenCardState(), reduce(open, OpenCardEvent.StreamLeft))
    }

    @Test
    fun openingAnotherCardReplacesTheFirst() {
        assertEquals(
            OpenCardState("page-b", "card-9"),
            reduce(open, OpenCardEvent.Open("page-b", "card-9")),
        )
    }

    @Test
    fun aPressOrBackIsTakenByAnOpenCardFirstThenTheStreamClosesOnTheNextOne() {
        // First press: the card closes and the Stream stays. Second: nothing is open, so the Stream is the press's.
        val afterFirst = reduce(open, OpenCardEvent.CloseByPress)
        assertTrue(OpenCardReducer.takesPressOrBack(open))
        assertFalse(OpenCardReducer.takesPressOrBack(afterFirst))
    }
}
