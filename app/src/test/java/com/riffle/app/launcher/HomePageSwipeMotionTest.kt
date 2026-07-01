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
    fun dragPositionStaysAnchoredToPageWhereDragStarted() {
        assertEquals(
            0.25f,
            motion.pagePositionDuringDrag(
                dragStartPageIndex = 0,
                pageDragOffsetPx = -100f,
                pageWidthPx = 400f,
            ),
        )
        assertEquals(
            0.75f,
            motion.pagePositionDuringDrag(
                dragStartPageIndex = 1,
                pageDragOffsetPx = 100f,
                pageWidthPx = 400f,
            ),
        )
    }

    @Test
    fun dragPositionClampsToAdjacentPage() {
        assertEquals(
            1f,
            motion.pagePositionDuringDrag(
                dragStartPageIndex = 0,
                pageDragOffsetPx = -800f,
                pageWidthPx = 400f,
            ),
        )
        assertEquals(
            0f,
            motion.pagePositionDuringDrag(
                dragStartPageIndex = 1,
                pageDragOffsetPx = 800f,
                pageWidthPx = 400f,
            ),
        )
    }

    @Test
    fun settleTargetUsesPageActionTarget() {
        assertEquals(
            1,
            motion.pageSettleTargetIndex(
                action = LauncherShellAction.SelectNextHomePage,
                selectedPageIndex = 0,
                pageCount = 3,
            ),
        )
        assertEquals(
            1,
            motion.pageSettleTargetIndex(
                action = LauncherShellAction.SelectPreviousHomePage,
                selectedPageIndex = 2,
                pageCount = 3,
            ),
        )
    }

    @Test
    fun settleInitialVelocityContinuesDragDirectionInPageUnits() {
        assertEquals(
            2f,
            motion.pageSettleInitialVelocity(
                horizontalVelocityPxPerSecond = -800f,
                pageWidthPx = 400f,
            ),
        )
        assertEquals(
            -2f,
            motion.pageSettleInitialVelocity(
                horizontalVelocityPxPerSecond = 800f,
                pageWidthPx = 400f,
            ),
        )
    }

    @Test
    fun settleInitialVelocityIgnoresMissingPageWidth() {
        assertEquals(
            0f,
            motion.pageSettleInitialVelocity(
                horizontalVelocityPxPerSecond = -800f,
                pageWidthPx = 0f,
            ),
        )
    }

    @Test
    fun settleTargetIgnoresUnavailableOrNonPageActions() {
        assertEquals(
            null,
            motion.pageSettleTargetIndex(
                action = LauncherShellAction.SelectNextHomePage,
                selectedPageIndex = 0,
                pageCount = 1,
            ),
        )
        assertEquals(
            null,
            motion.pageSettleTargetIndex(
                action = LauncherShellAction.OpenSettings,
                selectedPageIndex = 0,
                pageCount = 3,
            ),
        )
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
