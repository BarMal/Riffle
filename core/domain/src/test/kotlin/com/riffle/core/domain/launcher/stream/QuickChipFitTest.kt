package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.dock.DockEdgeFade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickChipFitTest {
    @Test
    fun chipsThatFitKeepTheirNaturalWidths() {
        val natural = listOf(100, 120, 90)
        assertEquals(natural, QuickChipFit.fitted(natural, gapPx = 8, availablePx = 340, minChipPx = 60))
    }

    @Test
    fun chipsThatDoNotFitGiveUpTheEndsOfTheWidestLabelsAndAllFit() {
        val natural = listOf(200, 120, 90)
        val fitted = checkNotNull(QuickChipFit.fitted(natural, gapPx = 8, availablePx = 300, minChipPx = 60))
        assertTrue(fitted.sum() + 16 <= 300, "$fitted")
        // The widest gave way first; the short ones kept their width.
        assertEquals(90, fitted[2])
        assertTrue(fitted[0] <= 200 && fitted[0] >= fitted[1])
        // And the room is used: no chip is narrower than it need be.
        assertTrue(fitted.sum() + 16 >= 300 - fitted.size, "$fitted")
    }

    @Test
    fun whenEvenTheLeastWidthsDoNotFitTheRowKeepsNaturalWidthsAndScrolls() {
        assertNull(QuickChipFit.fitted(List(6) { 120 }, gapPx = 8, availablePx = 300, minChipPx = 60))
    }

    @Test
    fun noChipsFitTrivially() {
        assertEquals(emptyList(), QuickChipFit.fitted(emptyList(), 8, 300, 60))
    }

    @Test
    fun aChipLessThanHalfInViewIsNotDrawnAndOneMostlyInViewIsQuiet() {
        // A 120 wide chip with 40 in view: a third, gone. With 90 in view: three quarters, drawn at a quarter strength.
        assertEquals(0f, DockEdgeFade.alpha(260f, 380f, 0f, 300f, vanishBelow = 0.5f))
        assertEquals(0.25f, DockEdgeFade.alpha(210f, 330f, 0f, 300f, vanishBelow = 0.5f), 1e-4f)
        assertEquals(1f, DockEdgeFade.alpha(100f, 220f, 0f, 300f, vanishBelow = 0.5f))
        // The icons' own fade is unchanged.
        assertEquals(0.25f, DockEdgeFade.alpha(280f, 330f, 0f, 305f), 0.2f)
    }
}
