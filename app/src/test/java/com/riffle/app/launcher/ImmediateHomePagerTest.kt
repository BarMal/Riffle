package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmediateHomePagerTest {
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
    fun selectsImmediateExternalPageSelectionSettlePolicyWhenReducedMotionIsOn() {
        assertEquals(
            HomePageExternalSelectionSettlePolicy.ImmediateSnap,
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
