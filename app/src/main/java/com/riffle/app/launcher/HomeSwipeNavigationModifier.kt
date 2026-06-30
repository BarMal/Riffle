package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

internal fun Modifier.homeSwipeNavigation(
    state: HomeSwipeNavigationState,
    onPageDragOffsetChange: (Float) -> Unit,
    onPageDragReleased: () -> Unit,
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

            detectDragGestures(
                onDragStart = {
                    horizontalDragPx = 0f
                    verticalDragPx = 0f
                    onPageDragOffsetChange(0f)
                },
                onDrag = { change, dragAmount ->
                    horizontalDragPx += dragAmount.x
                    verticalDragPx += dragAmount.y
                    onPageDragOffsetChange(state.pageDragOffset(horizontalDragPx))
                    change.consume()
                },
                onDragEnd = {
                    interpreter
                        .gestureFor(horizontalDragPx, verticalDragPx)
                        ?.let { gesture -> actionMapper.actionFor(gesture, state.homeSwipeGestures) }
                        ?.let(onAction)
                    onPageDragReleased()
                },
                onDragCancel = {
                    horizontalDragPx = 0f
                    verticalDragPx = 0f
                    onPageDragReleased()
                },
            )
        }
    }
