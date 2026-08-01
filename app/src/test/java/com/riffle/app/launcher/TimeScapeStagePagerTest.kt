package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TimeScapeStagePagerState] settles via the same generic threshold/fling/external-selection
 * helpers [ImmediateHomePagerState] uses (see [ImmediateHomePagerTest]), reused as-is rather than
 * duplicated. These tests exercise that shared logic against TimeScape-shaped stage counts and
 * confirm the pager's own constants.
 */
class TimeScapeStagePagerTest {
    @Test
    fun settlesToNextStageOncePastDistanceThresholdDraggingLeft() {
        assertEquals(
            1,
            pageSettleTargetIndex(
                startPagePosition = 0f,
                releasedPagePosition = 0.3f,
                horizontalDragPx = -300f,
                pageWidthPx = 1000f,
                horizontalVelocityPxPerSecond = 0f,
                pageCount = 3,
            ),
        )
    }

    @Test
    fun settlesBackToStartWhenDragIsBelowBothThresholds() {
        assertEquals(
            1,
            pageSettleTargetIndex(
                startPagePosition = 1f,
                releasedPagePosition = 1.05f,
                horizontalDragPx = -50f,
                pageWidthPx = 1000f,
                horizontalVelocityPxPerSecond = 0f,
                pageCount = 3,
            ),
        )
    }

    @Test
    fun doesNotWrapPastTheLastStageOnAFastLeftFling() {
        assertEquals(
            2,
            pageSettleTargetIndex(
                startPagePosition = 2f,
                releasedPagePosition = 2f,
                horizontalDragPx = -20f,
                pageWidthPx = 1000f,
                horizontalVelocityPxPerSecond = -2000f,
                pageCount = 3,
            ),
        )
    }

    @Test
    fun doesNotWrapBeforeTheFirstStageOnAFastRightFling() {
        assertEquals(
            0,
            pageSettleTargetIndex(
                startPagePosition = 0f,
                releasedPagePosition = 0f,
                horizontalDragPx = 20f,
                pageWidthPx = 1000f,
                horizontalVelocityPxPerSecond = 2000f,
                pageCount = 3,
            ),
        )
    }

    @Test
    fun appliesExternalStageSelectionWhenPagerIsIdleAndPositionIsStale() {
        assertTrue(
            shouldApplyExternalHomePageSelection(
                isDragging = false,
                isSettling = false,
                hasPendingGestureTarget = false,
                pageCount = 2,
                currentPagePosition = 0f,
                selectedPageIndex = 1,
            ),
        )
    }

    @Test
    fun doesNotApplyExternalStageSelectionWhileDraggingBetweenStages() {
        assertFalse(
            shouldApplyExternalHomePageSelection(
                isDragging = true,
                isSettling = false,
                hasPendingGestureTarget = false,
                pageCount = 2,
                currentPagePosition = 0f,
                selectedPageIndex = 1,
            ),
        )
    }

    @Test
    fun horizontalDragIntentThresholdMatchesStandardHomesPager() {
        assertEquals(18f, STAGE_HORIZONTAL_DRAG_INTENT_PX)
    }
}
