package com.riffle.app.launcher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

internal fun dockHasOverflow(
    capacity: Int,
    itemCount: Int,
): Boolean = capacity > 0 && itemCount > capacity

internal fun dockShelfGestureExpandedState(
    isExpanded: Boolean,
    horizontalDragPx: Float,
    verticalDragPx: Float,
): Boolean? =
    when {
        isExpanded &&
            verticalDragPx >= DOCK_SHELF_GESTURE_THRESHOLD_PX &&
            abs(verticalDragPx) > abs(horizontalDragPx) ->
            false

        !isExpanded &&
            verticalDragPx <= -DOCK_SHELF_GESTURE_THRESHOLD_PX &&
            abs(verticalDragPx) > abs(horizontalDragPx) ->
            true

        else -> null
    }

internal fun dockShelfExpandedStateAfterBackgroundTap(isExpanded: Boolean): Boolean {
    return if (isExpanded) false else isExpanded
}

internal fun dockShelfExpandedStateForOverflow(
    isExpanded: Boolean,
    hasOverflow: Boolean,
): Boolean = isExpanded && hasOverflow

internal fun Modifier.dockShelfGestureInput(interactions: DockInteractions): Modifier =
    fillMaxWidth()
        .dockShelfGestureInput(
            isExpanded = interactions.isShelfExpanded,
            onExpandedChange = interactions.onShelfExpandedChange,
        )

private fun Modifier.dockShelfGestureInput(
    isExpanded: Boolean,
    onExpandedChange: ((Boolean) -> Unit)?,
): Modifier {
    if (onExpandedChange == null) {
        return this
    }
    return pointerInput(isExpanded, onExpandedChange) {
        awaitPointerEventScope {
            while (true) {
                val down =
                    awaitPointerEvent(PointerEventPass.Initial)
                        .changes
                        .firstOrNull { change -> change.pressed }
                        ?: continue
                val start = down.position
                var handled = false
                while (!handled) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val active = event.changes.firstOrNull { change -> change.id == down.id && change.pressed }
                    if (active == null) {
                        handled = true
                    } else {
                        val drag = active.position - start
                        dockShelfGestureExpandedState(
                            isExpanded = isExpanded,
                            horizontalDragPx = drag.x,
                            verticalDragPx = drag.y,
                        )?.let { expanded ->
                            active.consume()
                            onExpandedChange(expanded)
                            handled = true
                        }
                    }
                }
            }
        }
    }
}

private const val DOCK_SHELF_GESTURE_THRESHOLD_PX = 80f
