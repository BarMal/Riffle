package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Device fixes, round 7, item 1: the stack fits the column. Numbers are the owner's unfolded window: a Dock body of
 * 475 dp, cards at the larger sizes (a media card 303 dp, a text card up to 227 dp) and a strip pitch of 64 dp.
 */
class StackFitTest {
    private fun fit(
        heights: List<Float>,
        floors: List<Float> = heights,
        wanted: Int = 6,
        area: Float = 475f,
    ) = StackFit.fit(heights, floors, pitchDp = 64f, wantedPeeks = wanted, areaDp = area)

    /** The tallest the stack stands as the front card in turn: its height and its strips. */
    private fun tallest(
        result: StackFit.Result,
        count: Int,
    ) = result.heightsDp.indices.maxOf { result.heightsDp[it] + minOf(count - 1 - it, result.peeks) * 64f }

    @Test
    fun aStackThatFitsIsAsItWas() {
        val result = fit(listOf(303f, 160f, 160f), wanted = 2)

        assertEquals(2, result.peeks)
        assertEquals(listOf(303f, 160f, 160f), result.heightsDp)
    }

    @Test
    fun aFrontCardThatCanGiveHeightGivesItBeforeAStripGoes() {
        // A media card of 303 dp and three strips of 64 dp need 495 dp: the card gives 20 dp to its floor of 176.
        val result = fit(listOf(303f, 160f, 160f, 160f), floors = listOf(176f, 160f, 160f, 160f), wanted = 3)

        assertEquals(3, result.peeks)
        assertEquals(283f, result.heightsDp[0], 0.01f)
        assertTrue(tallest(result, 4) <= 475f)
    }

    @Test
    fun aTallStackTouchesTheTopOfTheRoomWhenTheFrontCardWasCapped() {
        val result = fit(listOf(303f, 160f, 160f, 160f), floors = listOf(176f, 160f, 160f, 160f), wanted = 3)

        assertEquals(475f, tallest(result, 4), 0.01f)
    }

    @Test
    fun aStripGoesWhenNoCardCanGiveMore() {
        // Cards with no height to give: three strips need 495 dp, so one strip goes.
        val result = fit(listOf(303f, 303f, 303f, 303f), wanted = 3)

        assertEquals(2, result.peeks)
        assertTrue(tallest(result, 4) <= 475f)
    }

    @Test
    fun aRoomTooShortForAnyStripLeavesTheFrontCardAlone() {
        val result = fit(listOf(303f, 303f), area = 320f, wanted = 3)

        assertEquals(0, result.peeks)
        assertEquals(listOf(303f, 303f), result.heightsDp)
    }

    @Test
    fun aRoomShorterThanACardHoldsTheCardToTheRoom() {
        val result = fit(listOf(303f), area = 250f, wanted = 3)

        assertEquals(0, result.peeks)
        assertEquals(250f, result.heightsDp.single(), 0.01f)
    }

    @Test
    fun theOldestCardHasNoStripsBehindItSoItMayBeTheTallest() {
        // As the front card the oldest has nothing behind it; the newest has three strips.
        val result = fit(listOf(160f, 160f, 160f, 400f), wanted = 3, area = 475f)

        assertEquals(3, result.peeks)
        assertEquals(400f, result.heightsDp[3], 0.01f)
        assertEquals(160f, result.heightsDp[0], 0.01f)
    }

    @Test
    fun noCardsAndNoRoomAreLeftAlone() {
        assertEquals(StackFit.Result(3, emptyList()), fit(emptyList(), wanted = 3))
        assertEquals(StackFit.Result(3, listOf(100f)), fit(listOf(100f), wanted = 3, area = 0f))
    }
}
