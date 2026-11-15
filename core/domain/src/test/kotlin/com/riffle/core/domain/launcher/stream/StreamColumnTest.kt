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

    // The unfolded column spans the Dock (batch 7) -----------------------------------------------------------------

    private fun unfolded(
        edge: DockPosition,
        width: Float = 1200f,
        height: Float = 900f,
        dock: StreamColumnRect? = null,
        length: Float? = null,
    ) = StreamColumnWindow(
        widthDp = width,
        heightDp = height,
        dockEdge = edge,
        dockExtentDp = 72f,
        safeTopDp = 24f,
        safeBottomDp = 16f,
        dockBounds = dock,
        unfolded = true,
        dockBodyLengthDp = length,
    )

    /** A right Dock's body, [length] long and centred on the middle of a 900 dp tall window. */
    private fun rightBody(length: Float) =
        StreamColumnRect(left = 1128f, top = 450f - length / 2f, right = 1200f, bottom = 450f + length / 2f)

    @Test
    fun theUnfoldedColumnIsWiderAndTheFoldedOnesWidthIsUntouched() {
        assertEquals(0.38f * 1200f, StreamColumn.restWidthDp(1200f, unfolded = true), 0.01f)
        assertEquals(360f, StreamColumn.restWidthDp(840f, unfolded = true)) // 0.38 of 840 is 319: the floor holds
        assertEquals(320f, StreamColumn.restWidthDp(840f)) // folded and the earlier column: as before
        assertEquals(400f, StreamColumn.restWidthDp(1200f))
        assertTrue(StreamColumn.openWidthDp(840f, unfolded = true) >= StreamColumn.restWidthDp(840f, unfolded = true))
        // The grid stays usable: at 1200 dp it keeps more than half of the width past the Dock.
        assertTrue(1200f - 72f - StreamColumn.restWidthDp(1200f, unfolded = true) > 600f)
        assertTrue(StreamColumn.openWidthDp(300f, unfolded = true) <= 300f)
    }

    @Test
    fun aTallDockGivesAColumnWhoseTopAndBottomAreTheDocksBody() {
        val body = rightBody(700f)
        val rect = StreamColumn.rect(unfolded(DockPosition.RIGHT, dock = body, length = 700f))
        assertEquals(body.top, rect.top, 0.01f)
        assertEquals(body.bottom, rect.bottom, 0.01f)
        assertEquals(700f, rect.height, 0.01f)
        // The edge against the Dock is as before.
        assertEquals(1200f - 72f + overlap, rect.right)
        assertEquals(StreamColumn.restWidthDp(1200f, unfolded = true), rect.width, 0.01f)
    }

    @Test
    fun theColumnFollowsTheContextualLengthAboutTheMiddleOfTheMeasuredBody() {
        // The Dock is still its short Home-mode self when it is measured; the contextual length is what is spanned.
        val home = rightBody(300f)
        val rect = StreamColumn.rect(unfolded(DockPosition.RIGHT, dock = home, length = 700f))
        assertEquals(450f - 350f, rect.top, 0.01f)
        assertEquals(450f + 350f, rect.bottom, 0.01f)
    }

    @Test
    fun aShortDockIsCentredOnAndTheColumnIsNeverShorterThanItsRestHeight() {
        val body = rightBody(300f)
        val rect = StreamColumn.rect(unfolded(DockPosition.RIGHT, dock = body, length = 300f))
        assertEquals(StreamColumn.restHeightDp(unfolded = true), rect.height, 0.01f)
        assertTrue(rect.height >= StreamColumn.REST_HEIGHT_DP, "never shorter than today's resting height")
        assertEquals(450f, (rect.top + rect.bottom) / 2f, 0.01f, "centred on the Dock")
    }

    @Test
    fun aDockLongerThanTheSafeAreaIsHeldInsideIt() {
        val body = rightBody(990f) // 450 +- 495: past both ends of the window
        val rect = StreamColumn.rect(unfolded(DockPosition.RIGHT, dock = body, length = 990f))
        assertEquals(24f + StreamColumn.TOP_MARGIN_DP, rect.top, 0.01f)
        assertEquals(900f - 16f, rect.bottom, 0.01f)
    }

    @Test
    fun aLeftDockMirrorsTheRightOneUnfolded() {
        val body = StreamColumnRect(left = 0f, top = 150f, right = 72f, bottom = 750f)
        val rect = StreamColumn.rect(unfolded(DockPosition.LEFT, dock = body, length = 600f))
        assertEquals(150f, rect.top, 0.01f)
        assertEquals(750f, rect.bottom, 0.01f)
        assertEquals(72f - overlap, rect.left)
    }

    @Test
    fun anOpenCardMayUseTheDocksHeightAndNoMore() {
        val tall = unfolded(DockPosition.RIGHT, dock = rightBody(700f), length = 700f)
        val rest = StreamColumn.rect(tall)
        val open = StreamColumn.rect(tall, open = 1f)
        assertEquals(rest.bottom, open.bottom, 0.01f, "the bottom stays")
        assertEquals(rest.top, open.top, 0.01f, "a tall Dock already has the room: the top stays")
        assertTrue(open.width > rest.width)
        // A short Dock still grows up to the open height.
        val short = unfolded(DockPosition.RIGHT, dock = rightBody(300f), length = 300f)
        assertTrue(StreamColumn.rect(short, open = 1f).height >= StreamColumn.OPEN_HEIGHT_DP)
        assertTrue(StreamColumn.rect(short, open = 1f).top >= 24f)
    }

    @Test
    fun aMeasuredDockWithNoLengthYetStillGivesAColumnAtItsLowerEnd() {
        val rect = StreamColumn.rect(unfolded(DockPosition.RIGHT, dock = rightBody(300f)))
        assertEquals(StreamColumn.restHeightDp(unfolded = true), rect.height, 0.01f)
        val none = StreamColumn.rect(unfolded(DockPosition.RIGHT))
        assertEquals(900f - 16f, none.bottom)
        assertEquals(StreamColumn.restHeightDp(unfolded = true), none.height, 0.01f)
    }

    @Test
    fun aNarrowWindowKeepsTheUnfoldedColumnOnScreen() {
        val narrow =
            StreamColumnWindow(
                widthDp = 340f,
                heightDp = 640f,
                dockEdge = DockPosition.RIGHT,
                dockExtentDp = 72f,
                unfolded = true,
                dockBodyLengthDp = 400f,
                dockBounds = StreamColumnRect(268f, 120f, 340f, 520f),
            )
        listOf(0f, 1f).forEach { open ->
            val rect = StreamColumn.rect(narrow, open)
            assertTrue(rect.left >= 0f && rect.right <= 340f - 72f + StreamColumn.PAGE_INSET_DP, "at $open: $rect")
            assertTrue(rect.top >= 0f && rect.bottom <= 640f, "at $open: $rect")
        }
    }

    @Test
    fun aHingeStillCutsTheUnfoldedColumnBack() {
        val hinge = StreamColumnRect(left = 700f, top = 0f, right = 708f, bottom = 900f)
        val window =
            unfolded(DockPosition.RIGHT, dock = rightBody(700f), length = 700f).copy(verticalHingesDp = listOf(hinge))
        val open = StreamColumn.rect(window, open = 1f)
        assertTrue(open.left >= hinge.right, "cut back to the hinge's far side: ${open.left}")
        assertEquals(
            StreamColumn.rect(unfolded(DockPosition.RIGHT, dock = rightBody(700f), length = 700f)).top,
            open.top,
        )
    }

    @Test
    fun aBottomDockGetsTheHeightBetweenTheSafeTopAndTheDockUpToFiveHundredSixty() {
        val dock = StreamColumnRect(left = 450f, top = 826f, right = 750f, bottom = 890f)
        val tall = unfolded(DockPosition.BOTTOM, height = 1400f, dock = dock)
        assertEquals(StreamColumn.BOTTOM_DOCK_MAX_HEIGHT_DP, StreamColumn.rect(tall).height, 0.01f)
        val room = unfolded(DockPosition.BOTTOM, height = 900f, dock = dock)
        val rect = StreamColumn.rect(room)
        // 900 dp window: the room between the safe top and the Dock is less than 560 dp but more than the least.
        assertTrue(rect.height <= StreamColumn.BOTTOM_DOCK_MAX_HEIGHT_DP)
        assertTrue(rect.height >= StreamColumn.restHeightDp(unfolded = true))
        assertTrue(rect.top >= 24f)
        assertEquals(900f - 16f - 72f + overlap, rect.bottom, 0.01f, "stands on the Dock")
        assertEquals(dock.centreX, rect.centreX, 0.01f, "centred on the Dock")
    }

    @Test
    fun theFoldedGeometryIsExactlyWhatItWas() {
        val folded = window(DockPosition.RIGHT, dock = StreamColumnRect(768f, 300f, 840f, 640f))
        val rect = StreamColumn.rect(folded)
        assertEquals(StreamColumn.REST_HEIGHT_DP, rect.height)
        assertEquals(640f, rect.bottom)
        assertEquals(StreamColumn.restWidthDp(840f), rect.width)
        assertEquals(StreamColumn.OPEN_HEIGHT_DP, StreamColumn.rect(folded, open = 1f).height)
    }
}
