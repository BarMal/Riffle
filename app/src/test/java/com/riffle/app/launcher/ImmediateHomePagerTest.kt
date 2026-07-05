package com.riffle.app.launcher

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
}
