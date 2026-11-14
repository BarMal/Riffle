package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.DockPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamColumnTest {
    /** How far past the Dock's body the column's edge sits: the page padding less the sliver left between them. */
    private val overlap = StreamColumn.PAGE_INSET_DP - StreamColumn.ATTACH_GAP_DP

    private fun window(
        edge: DockPosition,
        width: Float = 840f,
        height: Float = 930f,
        dock: StreamColumnRect? = null,
    ) = StreamColumnWindow(
        widthDp = width,
        heightDp = height,
        dockEdge = edge,
        dockExtentDp = 72f,
        safeTopDp = 24f,
        safeBottomDp = 16f,
        dockBounds = dock,
    )

    @Test
    fun theColumnsPageInsetIsTheStreamPagesOwnPadding() {
        assertEquals(16f, StreamColumn.PAGE_INSET_DP)
    }

    @Test
    fun theColumnIsAboutAThirdAtRestAndUpToHalfWhileACardIsOpen() {
        assertEquals(320f, StreamColumn.restWidthDp(840f)) // a third is 280: the floor holds the cards readable
        assertEquals(400f, StreamColumn.restWidthDp(1200f))
        assertEquals(600f, StreamColumn.openWidthDp(1200f))
        assertEquals(440f, StreamColumn.openWidthDp(840f))
        assertTrue(StreamColumn.openWidthDp(300f) <= 300f)
        assertTrue(StreamColumn.openWidthDp(900f) >= StreamColumn.restWidthDp(900f))
    }

    @Test
    fun besideARightDockTheColumnTouchesTheDocksInnerEdge() {
        val rect = StreamColumn.rect(window(DockPosition.RIGHT))
        assertEquals(840f - 72f + overlap, rect.right)
        assertEquals(StreamColumn.restWidthDp(840f), rect.width)
        // Stands at the bottom of the safe area when the Dock's body is not measured yet.
        assertEquals(930f - 16f, rect.bottom)
        assertEquals(StreamColumn.REST_HEIGHT_DP, rect.height)
    }

    @Test
    fun aMeasuredDockMovesTheColumnsBottomToItsLowerEndAndTheEdgeItTouchesToItsRestBody() {
        val live =
            StreamColumnRect(
                left = 733f,
                top = 300f,
                right = 823f,
                bottom = 640f,
            ) // dipped 15 dp toward the edge
        val rest = StreamColumnRect(left = 748f, top = 300f, right = 828f, bottom = 640f)
        val rect = StreamColumn.rect(window(DockPosition.RIGHT).copy(dockBounds = live, dockRestBounds = rest))
        assertEquals(748f + overlap, rect.right) // the rest body's edge: the dip does not move it
        assertEquals(640f, rect.bottom)
        val still = StreamColumn.rect(window(DockPosition.RIGHT).copy(dockBounds = rest, dockRestBounds = rest))
        assertEquals(still.right, rect.right)
    }

    @Test
    fun aLeftDockMirrorsTheRightOne() {
        val left = StreamColumn.rect(window(DockPosition.LEFT))
        val right = StreamColumn.rect(window(DockPosition.RIGHT))
        assertEquals(72f - overlap, left.left)
        assertEquals(right.width, left.width)
        assertEquals(right.top, left.top)
        assertEquals(right.bottom, left.bottom)
    }

    @Test
    fun aBottomDockCarriesAColumnCentredOnItAndStandingOnIt() {
        val rect = StreamColumn.rect(window(DockPosition.BOTTOM))
        assertEquals(840f / 2f, rect.centreX)
        assertEquals(930f - 16f - 72f + overlap, rect.bottom)
        assertEquals(StreamColumn.restWidthDp(840f), rect.width)
        val off = StreamColumn.rect(window(DockPosition.BOTTOM, dock = StreamColumnRect(400f, 850f, 700f, 914f)))
        assertEquals(550f, off.centreX)
        assertEquals(930f - 16f - 72f + overlap, off.bottom)
    }

    @Test
    fun aBottomColumnIsHeldInsideTheScreen() {
        val dock = StreamColumnRect(0f, 850f, 100f, 914f)
        val rect = StreamColumn.rect(window(DockPosition.BOTTOM, dock = dock))
        assertTrue(rect.left >= 0f)
    }

    @Test
    fun openingACardGrowsTheColumnTowardTheCentreWithTheDockSideAndTheBottomStayingPut() {
        val rest = StreamColumn.rect(window(DockPosition.RIGHT))
        val open = StreamColumn.rect(window(DockPosition.RIGHT), open = 1f)
        assertEquals(rest.right, open.right)
        assertEquals(rest.bottom, open.bottom)
        assertTrue(open.left < rest.left)
        assertTrue(open.top < rest.top)
        assertEquals(StreamColumn.openWidthDp(840f), open.width)
        assertEquals(open.bottom - StreamColumn.OPEN_HEIGHT_DP, open.top)
        val half = StreamColumn.rect(window(DockPosition.RIGHT), open = 0.5f)
        assertTrue(half.width > rest.width && half.width < open.width)
    }

    @Test
    fun anOpenColumnIsNeverTallerThanTheRoomUnderTheTopMargin() {
        val rect = StreamColumn.rect(window(DockPosition.RIGHT, height = 500f), open = 1f)
        assertEquals(24f + StreamColumn.TOP_MARGIN_DP, rect.top)
        assertTrue(rect.height <= StreamColumn.OPEN_HEIGHT_DP)
    }

    @Test
    fun aLeftColumnGrowsToTheRight() {
        val rest = StreamColumn.rect(window(DockPosition.LEFT))
        val open = StreamColumn.rect(window(DockPosition.LEFT), open = 1f)
        assertEquals(rest.left, open.left)
        assertTrue(open.right > rest.right)
    }

    @Test
    fun aShortWindowStillRestsInsideTheSafeArea() {
        val rect = StreamColumn.rect(window(DockPosition.RIGHT, height = 400f))
        assertTrue(rect.top >= 24f)
        assertTrue(rect.bottom <= 400f - 16f)
        assertTrue(rect.height > 0f)
    }

    @Test
    fun aColumnIsNeverDrawnAcrossAVerticalHinge() {
        // A fold in the middle of the 840 dp screen: a resting column clear of it is untouched, an open one is cut
        // back.
        val hinge = StreamColumnRect(left = 416f, top = 0f, right = 424f, bottom = 900f)
        val with = window(DockPosition.RIGHT).copy(verticalHingesDp = listOf(hinge))
        val rest = StreamColumn.rect(with)
        assertEquals(StreamColumn.rect(window(DockPosition.RIGHT)), rest)
        val open = StreamColumn.rect(with, open = 1f)
        assertTrue(open.left >= hinge.right, "cut back to the hinge's far side: ${open.left}")
        val left = StreamColumn.rect(window(DockPosition.LEFT).copy(verticalHingesDp = listOf(hinge)), open = 1f)
        assertTrue(left.right <= hinge.left, "and a left column stops short of it: ${left.right}")
    }

    @Test
    fun onAWindowNarrowerThanTheColumnsFloorTheColumnStaysOnScreenBesideARightDock() {
        val narrow =
            StreamColumnWindow(widthDp = 320f, heightDp = 640f, dockEdge = DockPosition.RIGHT, dockExtentDp = 72f)

        listOf(0f, 1f).forEach { open ->
            val rect = StreamColumn.rect(narrow, open)
            assertTrue(rect.left >= 0f, "left of the screen at open=$open: $rect")
            assertTrue(rect.right <= 320f - 72f + StreamColumn.PAGE_INSET_DP, "past the Dock at open=$open: $rect")
        }
    }

    @Test
    fun onAWindowNarrowerThanTheColumnsFloorTheColumnStaysOnScreenBesideALeftDock() {
        val narrow =
            StreamColumnWindow(widthDp = 320f, heightDp = 640f, dockEdge = DockPosition.LEFT, dockExtentDp = 72f)

        listOf(0f, 1f).forEach { open ->
            val rect = StreamColumn.rect(narrow, open)
            assertTrue(rect.right <= 320f, "right of the screen at open=$open: $rect")
            assertTrue(rect.left >= 72f - StreamColumn.PAGE_INSET_DP, "before the Dock at open=$open: $rect")
        }
    }
}
