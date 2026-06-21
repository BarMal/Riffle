package com.riffle.app.launcher

enum class HomeSwipeGesture {
    UP,
    DOWN,
}

class HomeSwipeGestureInterpreter(
    private val thresholdPx: Float,
) {
    fun gestureFor(verticalDragPx: Float): HomeSwipeGesture? =
        when {
            verticalDragPx <= -thresholdPx -> HomeSwipeGesture.UP
            verticalDragPx >= thresholdPx -> HomeSwipeGesture.DOWN
            else -> null
        }
}
