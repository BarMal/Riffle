package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePageSwipeMotionTest {
    private val motion = HomePageSwipeMotion()

    @Test
    fun offsetsTowardNextPageWhenLeftSwipeSelectsNextPage() {
        val offset =
            motion.pageDragOffset(
                horizontalDragPx = -96f,
                selectedPageIndex = 0,
                pageCount = 2,
                homeSwipeGestures = HomeSwipeGestureSettings(),
            )

        assertEquals(-96f, offset)
    }

    @Test
    fun offsetsTowardPreviousPageWhenRightSwipeSelectsPreviousPage() {
        val offset =
            motion.pageDragOffset(
                horizontalDragPx = 96f,
                selectedPageIndex = 1,
                pageCount = 2,
                homeSwipeGestures = HomeSwipeGestureSettings(),
            )

        assertEquals(96f, offset)
    }

    @Test
    fun doesNotOffsetWhenThereIsNoAdjacentPage() {
        assertEquals(
            0f,
            motion.pageDragOffset(
                horizontalDragPx = -96f,
                selectedPageIndex = 0,
                pageCount = 1,
                homeSwipeGestures = HomeSwipeGestureSettings(),
            ),
        )
        assertEquals(
            0f,
            motion.pageDragOffset(
                horizontalDragPx = 96f,
                selectedPageIndex = 0,
                pageCount = 2,
                homeSwipeGestures = HomeSwipeGestureSettings(),
            ),
        )
    }

    @Test
    fun doesNotOffsetWhenHorizontalGestureIsMappedToAnotherAction() {
        val settings =
            HomeSwipeGestureSettings(
                left = LauncherGestureAction.OPEN_SETTINGS,
                right = LauncherGestureAction.OPEN_SEARCH,
            )

        assertEquals(
            0f,
            motion.pageDragOffset(
                horizontalDragPx = -96f,
                selectedPageIndex = 0,
                pageCount = 2,
                homeSwipeGestures = settings,
            ),
        )
        assertEquals(
            0f,
            motion.pageDragOffset(
                horizontalDragPx = 96f,
                selectedPageIndex = 1,
                pageCount = 2,
                homeSwipeGestures = settings,
            ),
        )
    }
}
