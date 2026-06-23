package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings

data class HomeSwipeNavigationState(
    val enabled: Boolean,
    val thresholdPx: Float,
    val homeSwipeGestures: HomeSwipeGestureSettings,
    val selectedPageIndex: Int,
    val pageCount: Int,
    val pageSwipeMotion: HomePageSwipeMotion = HomePageSwipeMotion(),
) {
    fun pageDragOffset(horizontalDragPx: Float): Float =
        pageSwipeMotion.pageDragOffset(
            horizontalDragPx = horizontalDragPx,
            selectedPageIndex = selectedPageIndex,
            pageCount = pageCount,
            homeSwipeGestures = homeSwipeGestures,
        )
}
