package com.riffle.core.domain.launcher.split

import com.riffle.core.domain.launcher.cards.AdaptiveStageHingeBounds
import com.riffle.core.domain.launcher.home.DockPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitGeometryTest {
    private val unfolded = SplitWindow(widthDp = 840f, heightDp = 900f, dockExtentDp = 96f)
    private val verticalHinge = AdaptiveStageHingeBounds(leftDp = 416, topDp = 0, rightDp = 424, bottomDp = 900)
    private val horizontalHinge = AdaptiveStageHingeBounds(leftDp = 0, topDp = 446, rightDp = 900, bottomDp = 454)

    @Test
    fun orientationFollowsTheHingeOnly() {
        assertEquals(SplitOrientation.SIDE_BY_SIDE, SplitGeometry.orientationFor(emptyList()))
        assertEquals(SplitOrientation.SIDE_BY_SIDE, SplitGeometry.orientationFor(listOf(verticalHinge)))
        assertEquals(SplitOrientation.STACKED, SplitGeometry.orientationFor(listOf(horizontalHinge)))
    }

    @Test
    fun theStreamTakesTheSideNearerTheDynamicSideOfABottomOrTopDock() {
        assertEquals(StreamPaneSide.END, SplitGeometry.streamSideFor(unfolded.copy(dynamicSideAtRight = true)))
        assertEquals(StreamPaneSide.START, SplitGeometry.streamSideFor(unfolded.copy(dynamicSideAtRight = false)))
        assertEquals(
            StreamPaneSide.START,
            SplitGeometry.streamSideFor(unfolded.copy(dockEdge = DockPosition.TOP, dynamicSideAtRight = false)),
        )
    }

    @Test
    fun aSideDockKeepsHomeAgainstItsEdge() {
        assertEquals(StreamPaneSide.END, SplitGeometry.streamSideFor(unfolded.copy(dockEdge = DockPosition.LEFT)))
        assertEquals(StreamPaneSide.START, SplitGeometry.streamSideFor(unfolded.copy(dockEdge = DockPosition.RIGHT)))
    }

    @Test
    fun aStackedSplitPutsTheStreamNearTheDock() {
        val tabletop = unfolded.copy(hinges = listOf(horizontalHinge))
        assertEquals(StreamPaneSide.END, SplitGeometry.streamSideFor(tabletop))
        assertEquals(StreamPaneSide.START, SplitGeometry.streamSideFor(tabletop.copy(dockEdge = DockPosition.TOP)))
        assertEquals(StreamPaneSide.END, SplitGeometry.streamSideFor(tabletop.copy(dockEdge = DockPosition.LEFT)))
    }

    @Test
    fun limitsTakeOffInsetsAndTheDocksRoomOnTheAxis() {
        val side = SplitGeometry.limits(unfolded.copy(safeLeftDp = 10f, safeRightDp = 20f))
        assertEquals(10f, side.regionStartDp)
        assertEquals(820f, side.regionEndDp)
        val left = SplitGeometry.limits(unfolded.copy(dockEdge = DockPosition.LEFT))
        assertEquals(96f, left.regionStartDp)
        val right = SplitGeometry.limits(unfolded.copy(dockEdge = DockPosition.RIGHT))
        assertEquals(744f, right.regionEndDp)
        val stacked = SplitGeometry.limits(unfolded.copy(hinges = listOf(horizontalHinge)))
        assertEquals(804f, stacked.regionEndDp)
    }

    @Test
    fun aNarrowWindowOffersNoSplit() {
        assertFalse(SplitGeometry.limits(unfolded.copy(widthDp = 560f)).splitAvailable)
        assertTrue(SplitGeometry.limits(unfolded).splitAvailable)
    }

    @Test
    fun limitsKeepEveryPaneUsable() {
        val limits = SplitGeometry.limits(unfolded.copy(widthDp = 700f))
        assertTrue(limits.minShare >= 280f / 700f - 0.001f)
        assertTrue(limits.maxShare <= 1f - 280f / 700f + 0.001f)
    }

    @Test
    fun aSplitPutsHomeAndTheStreamSideBySideWithAGapBetween() {
        val layout = SplitGeometry.layout(unfolded, SplitMode.SPLIT, 0.4f)
        assertEquals(SplitMode.SPLIT, layout.mode)
        // The Stream owns the right 40% of the 840 dp, less half the gap; Home the rest.
        assertEquals(840f - 0.4f * 840f + 8f, layout.stream.left, 0.01f)
        assertEquals(840f, layout.stream.right)
        assertEquals(0f, layout.home.left)
        assertEquals(840f - 0.4f * 840f - 8f, layout.home.right, 0.01f)
        assertEquals(SplitGeometry.DIVIDER_GAP_DP, layout.stream.left - layout.home.right, 0.01f)
        assertEquals(0f, layout.home.top)
        assertEquals(900f, layout.home.bottom)
    }

    @Test
    fun aBottomDockPadsBothPanesAndSideDividerLeavesItsRoomAlone() {
        val layout = SplitGeometry.layout(unfolded, SplitMode.SPLIT, 0.4f)
        // The Stream stops at the Dock's room; Home pads for it itself.
        assertEquals(900f - 96f, layout.stream.bottom)
        assertEquals(900f, layout.home.bottom)
        val divider = assertNotNull(layout.divider)
        assertEquals(900f - 96f, divider.bottom)
        assertEquals(SplitGeometry.DIVIDER_TARGET_DP, divider.width, 0.01f)
    }

    @Test
    fun aSideDockLeavesTheDockSideToHome() {
        val window = unfolded.copy(dockEdge = DockPosition.LEFT)
        val layout = SplitGeometry.layout(window, SplitMode.SPLIT, 0.4f)
        assertEquals(0f, layout.home.left)
        assertEquals(StreamPaneSide.END, layout.streamSide)
        // The Stream is away from the Dock, so it is not cut back; Home is against it and pads itself.
        assertEquals(840f, layout.stream.right)
        // The share is of the room beside the Dock: 744 dp.
        assertEquals(840f - 0.4f * 744f + 8f, layout.stream.left, 0.01f)
    }

    @Test
    fun theStreamCanTakeTheStartSide() {
        val layout = SplitGeometry.layout(unfolded.copy(dynamicSideAtRight = false), SplitMode.SPLIT, 0.4f)
        assertEquals(0f, layout.stream.left)
        assertEquals(0.4f * 840f - 8f, layout.stream.right, 0.01f)
        assertEquals(840f, layout.home.right)
    }

    @Test
    fun homeOnlyAndStreamOnlyFillTheWindow() {
        val home = SplitGeometry.layout(unfolded, SplitMode.HOME_ONLY, 0.4f)
        assertEquals(PaneRect(0f, 0f, 840f, 900f), home.home)
        assertTrue(home.stream.isEmpty)
        assertNull(home.divider)
        val stream = SplitGeometry.layout(unfolded, SplitMode.STREAM_ONLY, 0.4f)
        assertEquals(PaneRect(0f, 0f, 840f, 804f), stream.stream)
        assertTrue(stream.home.isEmpty)
    }

    @Test
    fun theShareIsClampedToTheLimits() {
        val layout = SplitGeometry.layout(unfolded, SplitMode.SPLIT, 0.05f)
        val limits = SplitGeometry.limits(unfolded)
        assertEquals(limits.minShare, layout.share, 0.001f)
        assertTrue(layout.stream.width >= SplitGeometry.MIN_PANE_DP - 8f)
    }

    @Test
    fun aSplitWithNoRoomFallsBackToAFullPane() {
        val narrow = unfolded.copy(widthDp = 500f)
        assertEquals(SplitMode.HOME_ONLY, SplitGeometry.layout(narrow, SplitMode.SPLIT, 0.3f).mode)
        assertEquals(SplitMode.STREAM_ONLY, SplitGeometry.layout(narrow, SplitMode.SPLIT, 0.7f).mode)
    }

    @Test
    fun theDividerSnapsToTheHingeWhenCloseAndNeverCrossesIt() {
        val window = unfolded.copy(hinges = listOf(verticalHinge))
        // A divider centred 10 dp left of the hinge's centre (420) snaps onto it.
        val closeShare = (840f - 410f) / 840f
        val snapped = SplitGeometry.layout(window, SplitMode.SPLIT, closeShare)
        assertTrue(snapped.snappedToHinge)
        // Centred on the hinge (420), the divider's gap (16 dp) is wider than the hinge (8 dp), so both panes clear it.
        assertEquals(412f, snapped.home.right, 0.01f)
        assertEquals(428f, snapped.stream.left, 0.01f)
        // A divider far from the hinge stays where it is, and the pane over the hinge is cut back to one side of it.
        val far = SplitGeometry.layout(window, SplitMode.SPLIT, 0.3f)
        assertFalse(far.snappedToHinge)
        val hingeRange = 416f..424f
        listOf(far.home, far.stream).forEach { pane ->
            assertFalse(
                pane.left < 424f && pane.right > 416f && pane.left < 416f && pane.right > 424f,
                "$pane crosses the hinge",
            )
            assertTrue(pane.isEmpty || pane.right <= hingeRange.start || pane.left >= hingeRange.endInclusive)
        }
    }

    @Test
    fun resolveShareMatchesWhatTheLayoutDraws() {
        val window = unfolded.copy(hinges = listOf(verticalHinge))
        val resolved = SplitGeometry.resolveShare(window, (840f - 410f) / 840f)
        assertEquals(SplitGeometry.layout(window, SplitMode.SPLIT, (840f - 410f) / 840f).share, resolved, 0.0001f)
        assertEquals(0.5f, resolved, 0.01f)
    }

    @Test
    fun aHingeOutsideTheSplitRangeDoesNotSnap() {
        val edgeHinge = AdaptiveStageHingeBounds(leftDp = 90, topDp = 0, rightDp = 98, bottomDp = 900)
        val window = unfolded.copy(hinges = listOf(edgeHinge))
        val layout = SplitGeometry.layout(window, SplitMode.SPLIT, 0.9f)
        assertFalse(layout.snappedToHinge)
    }

    @Test
    fun tabletopStacksThePanesAboveAndBelowTheHinge() {
        val window =
            unfolded.copy(
                widthDp = 900f,
                heightDp = 840f,
                hinges = listOf(horizontalHinge.copy(topDp = 416, bottomDp = 424)),
            )
        // The room above the Dock is 744 dp; a share of 0.45 puts the divider within the hinge magnet's reach.
        val layout = SplitGeometry.layout(window, SplitMode.SPLIT, 0.45f)
        assertEquals(SplitOrientation.STACKED, layout.orientation)
        assertTrue(layout.snappedToHinge)
        assertEquals(428f, layout.stream.top, 0.01f)
        assertEquals(840f - 96f, layout.stream.bottom)
        // Home is not against the bottom Dock edge, so its surface reaches over the Dock's room, which it pads itself.
        assertEquals(412f + 96f, layout.home.bottom, 0.01f)
        val divider = assertNotNull(layout.divider)
        assertEquals(900f, divider.right)
    }

    @Test
    fun transientFramesGrowAPaneFromNothingWithoutClamping() {
        val tiny = SplitGeometry.layout(unfolded, SplitMode.SPLIT, 0.05f, transient = true)
        assertEquals(0.05f, tiny.share, 0.0001f)
        assertEquals(SplitMode.HOME_ONLY, SplitGeometry.layout(unfolded, SplitMode.SPLIT, 0f, transient = true).mode)
        assertEquals(SplitMode.STREAM_ONLY, SplitGeometry.layout(unfolded, SplitMode.SPLIT, 1f, transient = true).mode)
    }

    @Test
    fun dragDeltasMoveTheShareTowardTheStreamsSide() {
        // Dragging the divider left (negative) grows a Stream at the right.
        assertEquals(0.1f, SplitGeometry.shareDeltaFor(unfolded, -84f), 0.0001f)
        assertEquals(-0.1f, SplitGeometry.shareDeltaFor(unfolded.copy(dynamicSideAtRight = false), -84f), 0.0001f)
    }

    @Test
    fun positionAndShareAreInverses() {
        val share = 0.37f
        val position = 840f - share * 840f
        assertEquals(share, SplitGeometry.shareAtPosition(unfolded, position), 0.0001f)
    }
}
