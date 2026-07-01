package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction

class HomePageSwipeMotion {
    fun pageDragOffset(
        horizontalDragPx: Float,
        selectedPageIndex: Int,
        pageCount: Int,
        homeSwipeGestures: HomeSwipeGestureSettings,
    ): Float =
        when {
            horizontalDragPx < 0f &&
                selectedPageIndex < pageCount - 1 &&
                homeSwipeGestures.left == LauncherGestureAction.SELECT_NEXT_HOME_PAGE ->
                horizontalDragPx

            horizontalDragPx > 0f &&
                selectedPageIndex > 0 &&
                homeSwipeGestures.right == LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE ->
                horizontalDragPx

            else -> 0f
        }

    fun pagePositionDuringDrag(
        dragStartPageIndex: Int,
        pageDragOffsetPx: Float,
        pageWidthPx: Float,
    ): Float =
        when {
            pageWidthPx <= 0f -> dragStartPageIndex.toFloat()
            else -> dragStartPageIndex - (pageDragOffsetPx.coerceIn(-pageWidthPx, pageWidthPx) / pageWidthPx)
        }

    fun pageSettleInitialVelocity(
        horizontalVelocityPxPerSecond: Float,
        pageWidthPx: Float,
    ): Float =
        when {
            pageWidthPx <= 0f -> 0f
            else ->
                (-horizontalVelocityPxPerSecond / pageWidthPx)
                    .coerceIn(-MAX_PAGE_SETTLE_VELOCITY_PAGES_PER_SECOND, MAX_PAGE_SETTLE_VELOCITY_PAGES_PER_SECOND)
        }

    fun pageFlingAction(
        horizontalVelocityPxPerSecond: Float,
        selectedPageIndex: Int,
        pageCount: Int,
        homeSwipeGestures: HomeSwipeGestureSettings,
    ): LauncherShellAction? =
        when {
            horizontalVelocityPxPerSecond <= -PAGE_FLING_VELOCITY_THRESHOLD_PX_PER_SECOND &&
                selectedPageIndex < pageCount - 1 &&
                homeSwipeGestures.left == LauncherGestureAction.SELECT_NEXT_HOME_PAGE ->
                LauncherShellAction.SelectNextHomePage

            horizontalVelocityPxPerSecond >= PAGE_FLING_VELOCITY_THRESHOLD_PX_PER_SECOND &&
                selectedPageIndex > 0 &&
                homeSwipeGestures.right == LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE ->
                LauncherShellAction.SelectPreviousHomePage

            else -> null
        }

    fun pageSettleTargetIndex(
        action: LauncherShellAction?,
        selectedPageIndex: Int,
        pageCount: Int,
    ): Int? =
        when (action) {
            LauncherShellAction.SelectNextHomePage ->
                (selectedPageIndex + 1).takeIf { targetIndex -> targetIndex < pageCount }

            LauncherShellAction.SelectPreviousHomePage ->
                (selectedPageIndex - 1).takeIf { targetIndex -> targetIndex >= 0 }

            else -> null
        }

    private companion object {
        const val PAGE_FLING_VELOCITY_THRESHOLD_PX_PER_SECOND = 900f
        const val MAX_PAGE_SETTLE_VELOCITY_PAGES_PER_SECOND = 5f
    }
}
