package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeHandleTest {
    // The quick-drag: a position along the dots is a page

    @Test
    fun theTrackEndsAreTheFirstAndLastPage() {
        assertEquals(0, HomeHandle.pageAt(0f, 100f, 5))
        assertEquals(2, HomeHandle.pageAt(50f, 100f, 5))
        assertEquals(4, HomeHandle.pageAt(100f, 100f, 5))
    }

    @Test
    fun pagesChangeAtTheMidpointsBetweenDots() {
        // Five pages: the dots are at 0, 25, 50, 75 and 100; the page flips halfway between two.
        assertEquals(0, HomeHandle.pageAt(12f, 100f, 5))
        assertEquals(1, HomeHandle.pageAt(13f, 100f, 5))
        assertEquals(1, HomeHandle.pageAt(37f, 100f, 5))
        assertEquals(2, HomeHandle.pageAt(38f, 100f, 5))
    }

    @Test
    fun aFingerPastEitherEndStaysOnTheLastOrFirstPage() {
        assertEquals(0, HomeHandle.pageAt(-40f, 100f, 3))
        assertEquals(2, HomeHandle.pageAt(240f, 100f, 3))
    }

    @Test
    fun aDegenerateTrackIsPageZero() {
        assertEquals(0, HomeHandle.pageAt(50f, 0f, 3))
        assertEquals(0, HomeHandle.pageAt(50f, 100f, 1))
        assertEquals(0, HomeHandle.pageAt(50f, 100f, 0))
    }

    @Test
    fun rightToLeftReadsThePagesFromTheRight() {
        assertEquals(2, HomeHandle.pageAt(0f, 100f, 3, rtl = true))
        assertEquals(1, HomeHandle.pageAt(50f, 100f, 3, rtl = true))
        assertEquals(0, HomeHandle.pageAt(100f, 100f, 3, rtl = true))
    }

    @Test
    fun aDotsOffsetIsTheExactInverseOfThePageUnderIt() {
        for (rtl in listOf(false, true)) {
            for (count in 2..9) {
                for (page in 0 until count) {
                    val offset = HomeHandle.offsetOf(page, 180f, count, rtl)
                    assertEquals(page, HomeHandle.pageAt(offset, 180f, count, rtl), "page $page of $count rtl=$rtl")
                }
            }
        }
    }

    @Test
    fun aSinglePageSitsAtTheStartOfItsTrack() {
        assertEquals(0f, HomeHandle.offsetOf(0, 100f, 1))
    }

    // The step an accessibility action takes

    @Test
    fun stepsMoveOnePageAndStopAtTheEnds() {
        assertEquals(2, HomeHandle.step(1, 4, 1))
        assertEquals(0, HomeHandle.step(1, 4, -1))
        assertNull(HomeHandle.step(0, 4, -1))
        assertNull(HomeHandle.step(3, 4, 1))
    }

    @Test
    fun aSinglePageHasNothingToStepTo() {
        assertNull(HomeHandle.step(0, 1, 1))
        assertNull(HomeHandle.step(0, 1, -1))
        assertNull(HomeHandle.step(0, 0, 1))
    }

    // Its size

    @Test
    fun theTouchTargetIsNeverUnder48AndTheDrawnPillIsShorterThanIt() {
        assertEquals(48, HomeHandle.TOUCH_TARGET_DP)
        assertTrue(HomeHandle.PILL_HEIGHT_DP < HomeHandle.TOUCH_TARGET_DP)
        for (count in 0..12) assertTrue(HomeHandle.touchWidthDp(count) >= 48, "width at $count pages")
    }

    @Test
    fun theDotsComeWithASecondPage() {
        assertFalse(HomeHandle.showsDots(1))
        assertTrue(HomeHandle.showsDots(2))
        assertEquals(48, HomeHandle.pillWidthDp(1))
        // 48 for the glyph, two 6 dp dots and the 6 dp between them, and 14 after.
        assertEquals(48 + 18 + 14, HomeHandle.pillWidthDp(2))
    }

    @Test
    fun theTrackIsTheDotsAndTheGapsBetweenThem() {
        assertEquals(0, HomeHandle.trackWidthDp(0))
        assertEquals(6, HomeHandle.trackWidthDp(1))
        assertEquals(6 * 5 + 6 * 4, HomeHandle.trackWidthDp(5))
    }
}
