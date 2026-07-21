package com.riffle.app.launcher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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

internal fun dockShelfGestureClaimsDrag(
    isExpanded: Boolean,
    horizontalDragPx: Float,
    verticalDragPx: Float,
): Boolean =
    abs(verticalDragPx) >= DOCK_SHELF_GESTURE_CLAIM_THRESHOLD_PX &&
        abs(verticalDragPx) > abs(horizontalDragPx) &&
        if (isExpanded) {
            verticalDragPx > 0f
        } else {
            verticalDragPx < 0f
        }

internal fun dockShelfExpandedStateAfterBackgroundTap(isExpanded: Boolean): Boolean {
    return if (isExpanded) false else isExpanded
}

internal fun dockShelfExpandedStateForContent(
    isExpanded: Boolean,
    hasContent: Boolean,
): Boolean = isExpanded && hasContent

internal fun dockHasExpandedContent(
    hasOverflow: Boolean,
    notificationShelfState: DockNotificationShelfState,
): Boolean = hasOverflow || notificationShelfState != DockNotificationShelfState.Hidden

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
    return composed {
        val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
        pointerInput(isExpanded) {
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
                        val trackedChange = event.changes.firstOrNull { change -> change.id == down.id }
                        if (trackedChange == null) {
                            handled = true
                        } else {
                            val drag = trackedChange.position - start
                            if (
                                dockShelfGestureClaimsDrag(
                                    isExpanded = isExpanded,
                                    horizontalDragPx = drag.x,
                                    verticalDragPx = drag.y,
                                )
                            ) {
                                trackedChange.consume()
                            }
                            dockShelfGestureExpandedState(
                                isExpanded = isExpanded,
                                horizontalDragPx = drag.x,
                                verticalDragPx = drag.y,
                            )?.let { expanded ->
                                currentOnExpandedChange(expanded)
                                handled = true
                            }
                            if (!trackedChange.pressed) {
                                handled = true
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val DOCK_SHELF_GESTURE_THRESHOLD_PX = 80f
private const val DOCK_SHELF_GESTURE_CLAIM_THRESHOLD_PX = 24f
