package com.riffle.app.launcher

enum class HomeSwipeGesture {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

class HomeSwipeGestureInterpreter(
    private val thresholdPx: Float,
) {
    fun gestureFor(
        horizontalDragPx: Float,
        verticalDragPx: Float,
    ): HomeSwipeGesture? =
        if (kotlin.math.abs(horizontalDragPx) > kotlin.math.abs(verticalDragPx)) {
            horizontalGestureFor(horizontalDragPx)
        } else {
            verticalGestureFor(verticalDragPx)
        }

    private fun verticalGestureFor(verticalDragPx: Float): HomeSwipeGesture? =
        when {
            verticalDragPx <= -thresholdPx -> HomeSwipeGesture.UP
            verticalDragPx >= thresholdPx -> HomeSwipeGesture.DOWN
            else -> null
        }

    private fun horizontalGestureFor(horizontalDragPx: Float): HomeSwipeGesture? =
        when {
            horizontalDragPx <= -thresholdPx -> HomeSwipeGesture.LEFT
            horizontalDragPx >= thresholdPx -> HomeSwipeGesture.RIGHT
            else -> null
        }
}
