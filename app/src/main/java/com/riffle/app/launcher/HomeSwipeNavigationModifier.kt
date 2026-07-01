package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker

internal fun Modifier.homeSwipeNavigation(
    state: HomeSwipeNavigationState,
    onPageDragStarted: () -> Unit,
    onPageDragOffsetChange: (Float) -> Unit,
    onPageDragReleased: (Int?, Float) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
): Modifier =
    if (!state.enabled) {
        this
    } else {
        pointerInput(
            state.thresholdPx,
            state.homeSwipeGestures,
            state.selectedPageIndex,
            state.pageCount,
        ) {
            var horizontalDragPx = 0f
            var verticalDragPx = 0f
            val interpreter = HomeSwipeGestureInterpreter(thresholdPx = state.thresholdPx)
            val actionMapper = HomeSwipeGestureActionMapper()
            val velocityTracker = VelocityTracker()

            detectDragGestures(
                onDragStart = {
                    horizontalDragPx = 0f
                    verticalDragPx = 0f
                    velocityTracker.resetTracking()
                    onPageDragStarted()
                    onPageDragOffsetChange(0f)
                },
                onDrag = { change, dragAmount ->
                    horizontalDragPx += dragAmount.x
                    verticalDragPx += dragAmount.y
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    onPageDragOffsetChange(state.pageDragOffset(horizontalDragPx))
                    change.consume()
                },
                onDragEnd = {
                    val horizontalVelocityPxPerSecond = velocityTracker.calculateVelocity().x
                    val action =
                        interpreter
                            .gestureFor(horizontalDragPx, verticalDragPx)
                            ?.let { gesture -> actionMapper.actionFor(gesture, state.homeSwipeGestures) }
                    onPageDragReleased(
                        state.pageSwipeMotion.pageSettleTargetIndex(
                            action = action,
                            selectedPageIndex = state.selectedPageIndex,
                            pageCount = state.pageCount,
                        ),
                        horizontalVelocityPxPerSecond,
                    )
                    action?.let(onAction)
                },
                onDragCancel = {
                    horizontalDragPx = 0f
                    verticalDragPx = 0f
                    velocityTracker.resetTracking()
                    onPageDragReleased(null, 0f)
                },
            )
        }
    }
