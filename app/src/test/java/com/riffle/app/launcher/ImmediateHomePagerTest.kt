package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmediateHomePagerTest {
    @Test
    fun mapsWidgetPickerDropPositionToBoundedGridCell() {
        assertEquals(
            GridCell(column = 3, row = 4),
            widgetPickerDropCell(
                position = Offset(500f, 900f),
                rootSize = IntSize(width = 400, height = 800),
                grid = GridDimensions(columns = 4, rows = 5),
            ),
        )
    }

    @Test
    fun appliesExternalHomePageSelectionWhenPagerIsIdleAndPositionIsStale() {
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
    fun doesNotApplyExternalHomePageSelectionWhileGestureOwnsTarget() {
        assertFalse(
            shouldApplyExternalHomePageSelection(
                isDragging = false,
                isSettling = false,
                hasPendingGestureTarget = true,
                pageCount = 2,
                currentPagePosition = 0f,
                selectedPageIndex = 1,
            ),
        )
    }

    @Test
    fun doesNotApplyExternalHomePageSelectionWhenPositionAlreadyMatches() {
        assertFalse(
            shouldApplyExternalHomePageSelection(
                isDragging = false,
                isSettling = false,
                hasPendingGestureTarget = false,
                pageCount = 2,
                currentPagePosition = 1f,
                selectedPageIndex = 1,
            ),
        )
    }

    @Test
    fun appliesExternalHomePageSelectionWhenFractionalPositionRoundsToSelectedPage() {
        assertTrue(
            shouldApplyExternalHomePageSelection(
                isDragging = false,
                isSettling = false,
                hasPendingGestureTarget = false,
                pageCount = 2,
                currentPagePosition = 0.6f,
                selectedPageIndex = 1,
            ),
        )
    }

    @Test
    fun doesNotApplyExternalHomePageSelectionWhenThereAreNoPages() {
        assertFalse(
            shouldApplyExternalHomePageSelection(
                isDragging = false,
                isSettling = false,
                hasPendingGestureTarget = false,
                pageCount = 0,
                currentPagePosition = 0f,
                selectedPageIndex = 1,
            ),
        )
    }

    @Test
    fun doesNotApplyExternalHomePageSelectionWhileDragging() {
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
    fun doesNotApplyExternalHomePageSelectionWhileSettling() {
        assertFalse(
            shouldApplyExternalHomePageSelection(
                isDragging = false,
                isSettling = true,
                hasPendingGestureTarget = false,
                pageCount = 2,
                currentPagePosition = 0f,
                selectedPageIndex = 1,
            ),
        )
    }

    @Test
    fun selectsAnimatedExternalPageSelectionSettlePolicyWhenReducedMotionIsOff() {
        assertEquals(
            HomePageExternalSelectionSettlePolicy.AnimatedSettle,
            homePageExternalSelectionSettlePolicy(reducedMotion = false),
        )
    }

    @Test
    fun selectsAnimatedExternalPageSelectionSettlePolicyWhenReducedMotionIsOn() {
        assertEquals(
            HomePageExternalSelectionSettlePolicy.AnimatedSettle,
            homePageExternalSelectionSettlePolicy(reducedMotion = true),
        )
    }

    @Test
    fun selectsStandardSpringPageSettlePolicyWhenReducedMotionIsOff() {
        assertEquals(
            HomePageSettleMotionPolicy.StandardSpring,
            homePageSettleMotionPolicy(reducedMotion = false),
        )
    }

    @Test
    fun selectsShortTweenPageSettlePolicyWhenReducedMotionIsOn() {
        assertEquals(
            HomePageSettleMotionPolicy.ReducedShortTween,
            homePageSettleMotionPolicy(reducedMotion = true),
        )
        assertEquals(80, REDUCED_MOTION_PAGE_SETTLE_DURATION_MILLIS)
    }
}
