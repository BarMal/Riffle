package com.riffle.core.domain.launcher.gestures

/**
 * Whether [accumulatedVerticalDragPx] -- upward travel past the platform touch slop, negative going
 * up -- has reached [thresholdPx] (resolved from [GestureThresholds.DOCK_SWIPE_UP_DP]).
 */
fun dockSwipeUpTriggered(
    accumulatedVerticalDragPx: Float,
    thresholdPx: Float = GestureThresholdsPx.Reference.dockSwipeUpPx,
): Boolean = accumulatedVerticalDragPx <= -thresholdPx
