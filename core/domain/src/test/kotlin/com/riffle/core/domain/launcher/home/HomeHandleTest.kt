package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.settings.PageScrollDirection
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

/** The handle's placement and size for each Dock edge and way of scrolling (ADR 0003, Amendment 2). */
class HomeHandlePlacementTest {
    @Test
    fun theHandleRunsTheWayThePagesScroll() {
        assertEquals(HandleAxis.HORIZONTAL, PageScrollDirection.HORIZONTAL.handleAxis)
        assertEquals(HandleAxis.VERTICAL, PageScrollDirection.VERTICAL.handleAxis)
    }

    @Test
    fun besideASideDockTheHandleIsAlwaysAColumnInFrontOfItsMiddleOnHomesSide() {
        assertEquals(HandleStrip.LEFT_COLUMN, HomeHandleLayout.stripFor(DockPosition.LEFT))
        assertEquals(HandleStrip.RIGHT_COLUMN, HomeHandleLayout.stripFor(DockPosition.RIGHT))
        // Whichever way the pages scroll, it runs down the column, along the Dock.
        PageScrollDirection.entries.forEach { scroll ->
            assertEquals(HandleAxis.VERTICAL, HomeHandleLayout.axisFor(DockPosition.LEFT, scroll))
            assertEquals(HandleAxis.VERTICAL, HomeHandleLayout.axisFor(DockPosition.RIGHT, scroll))
        }
    }

    @Test
    fun aBottomDockKeepsTheRowAboveItAndTheHandleFollowsThePagesThere() {
        assertEquals(HandleStrip.BOTTOM_ROW, HomeHandleLayout.stripFor(DockPosition.BOTTOM))
        // A top Dock is not offered; a layout that still holds it keeps the handle where it always was.
        assertEquals(HandleStrip.BOTTOM_ROW, HomeHandleLayout.stripFor(DockPosition.TOP))
        assertEquals(
            HandleAxis.HORIZONTAL,
            HomeHandleLayout.axisFor(DockPosition.BOTTOM, PageScrollDirection.HORIZONTAL),
        )
        assertEquals(HandleAxis.VERTICAL, HomeHandleLayout.axisFor(DockPosition.BOTTOM, PageScrollDirection.VERTICAL))
    }

    @Test
    fun theHandleTakesItsRoomFromTheMarginOnTheDocksSideFirst() {
        // A 16 dp margin holds 16 dp of the 48 dp column: the grid gives up the other 32, never the whole 48.
        assertEquals(0, HomeHandleLayout.remainingMarginDp(marginDp = 16, reservedDp = 48))
        assertEquals(0, HomeHandleLayout.remainingMarginDp(marginDp = 48, reservedDp = 48))
        assertEquals(12, HomeHandleLayout.remainingMarginDp(marginDp = 60, reservedDp = 48))

        // The room the grid loses in all is the reservation not covered by the margin.
        // (The column is 48 dp wide in the layout, the margin shrinks to what is left of it.)
        fun lostDp(margin: Int) = 48 + HomeHandleLayout.remainingMarginDp(margin, 48) - margin
        assertEquals(32, lostDp(16))
        assertEquals(0, lostDp(60))
    }

    @Test
    fun aRowIsAsTallAsATouchTargetAndAColumnAsWide() {
        val row = HomeHandleLayout.sizeDp(HandleAxis.HORIZONTAL, pageCount = 4)
        val column = HomeHandleLayout.sizeDp(HandleAxis.VERTICAL, pageCount = 4)
        // Glyph square, four dots (6 dp with 6 dp between) and the room after them.
        assertEquals(HandleSize(widthDp = 48 + 42 + 14, heightDp = 48), row)
        assertEquals(HandleSize(widthDp = 48, heightDp = 48 + 42 + 14), column)
    }

    @Test
    fun aSinglePageOrNoDotsIsJustTheGlyphsSquare() {
        assertEquals(HandleSize(48, 48), HomeHandleLayout.sizeDp(HandleAxis.HORIZONTAL, pageCount = 1))
        assertEquals(HandleSize(48, 48), HomeHandleLayout.sizeDp(HandleAxis.VERTICAL, pageCount = 1))
        assertEquals(HandleSize(48, 48), HomeHandleLayout.sizeDp(HandleAxis.VERTICAL, pageCount = 6, showsDots = false))
    }

    @Test
    fun everyPageCountKeepsTheGlyphAndTheDotsTheSameRoomAroundThem() {
        (2..8).forEach { pages ->
            // The length is exactly the glyph's square, the dots and the fixed room after them: nothing depends on
            // how many pages there are but the dots themselves.
            assertEquals(
                48 + HomeHandle.trackWidthDp(pages) + HomeHandle.TRAILING_PADDING_DP,
                HomeHandleLayout.lengthDp(pages),
            )
        }
    }

    @Test
    fun theRoomTheHandleTakesIsItsHeightInARowAndItsWidthInAColumnBesideASideDock() {
        assertEquals(48, HomeHandleLayout.reservedDp(DockPosition.BOTTOM, HandleAxis.HORIZONTAL, pageCount = 5))
        assertEquals(48, HomeHandleLayout.reservedDp(DockPosition.LEFT, HandleAxis.VERTICAL, pageCount = 5))
        assertEquals(48, HomeHandleLayout.reservedDp(DockPosition.RIGHT, HandleAxis.VERTICAL, pageCount = 5))
        // Beside a side Dock the handle is always the column, whatever the pages do: a column's width, 48 dp.
        assertEquals(48, HomeHandleLayout.reservedDp(DockPosition.RIGHT, HandleAxis.VERTICAL, pageCount = 9))
        // A column above a bottom Dock stands in the row above it, as tall as the column is long.
        assertEquals(
            HomeHandleLayout.lengthDp(5),
            HomeHandleLayout.reservedDp(DockPosition.BOTTOM, HandleAxis.VERTICAL, pageCount = 5),
        )
    }

    // The quick-drag on a column

    @Test
    fun aColumnReadsItsFingerAlongTheY() {
        assertEquals(40f, HomeHandleLayout.alongAxis(HandleAxis.VERTICAL, x = 3f, y = 40f))
        assertEquals(3f, HomeHandleLayout.alongAxis(HandleAxis.HORIZONTAL, x = 3f, y = 40f))
    }

    private fun down(
        y: Float,
        pages: Int,
        x: Float = 0f,
        trackPx: Float = 100f,
    ): Int = HomeHandleLayout.pageAtPoint(HandleAxis.VERTICAL, x = x, y = y, trackLengthPx = trackPx, pageCount = pages)

    @Test
    fun theTopOfAColumnIsTheFirstPageAndTheBottomTheLast() {
        // Five pages down a 100 px track: the dots are at 0, 25, 50, 75 and 100 from the top.
        assertEquals(0, down(y = 0f, pages = 5, x = 70f))
        assertEquals(2, down(y = 50f, pages = 5))
        assertEquals(4, down(y = 100f, pages = 5))
    }

    @Test
    fun aColumnChangesPageAtTheMidpointsAndStaysOnTheEndsPastThem() {
        assertEquals(0, down(y = 12f, pages = 5))
        assertEquals(1, down(y = 13f, pages = 5))
        assertEquals(0, down(y = -50f, pages = 3))
        assertEquals(2, down(y = 400f, pages = 3))
    }

    @Test
    fun theHorizontalXDoesNotMoveAColumnsDrag() {
        // A finger drifting sideways while it drags down the dots changes nothing.
        assertEquals(1, down(y = 50f, pages = 3, x = 0f))
        assertEquals(1, down(y = 50f, pages = 3, x = 999f))
    }

    private fun rtlPage(axis: HandleAxis): Int =
        HomeHandleLayout.pageAtPoint(axis, x = 0f, y = 0f, trackLengthPx = 100f, pageCount = 5, rtl = true)

    @Test
    fun onlyARowMirrorsInRightToLeft() {
        val row = rtlPage(HandleAxis.HORIZONTAL)
        assertEquals(4, row)
        // A column reads from the top in every language.
        assertEquals(0, rtlPage(HandleAxis.VERTICAL))
    }
}
