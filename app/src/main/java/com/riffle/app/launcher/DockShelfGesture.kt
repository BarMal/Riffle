package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.DockPosition
import kotlin.math.abs

/**
 * Whether a drag opens or closes the shelf, or means neither.
 *
 * The shelf comes out of the edge the dock is on, so the gesture that opens it is a pull away from
 * that edge and the one that closes it is a push back toward it. Reading the drag through the
 * dock's own edge is what makes that one comparison instead of one per edge -- on a bottom dock it
 * is the same upward pull it has always been.
 */
internal fun dockShelfGestureExpandedState(
    isExpanded: Boolean,
    horizontalDragPx: Float,
    verticalDragPx: Float,
    position: DockPosition = DockPosition.BOTTOM,
): Boolean? {
    val awayPx = position.dragAwayFromEdgePx(horizontalDragPx, verticalDragPx)
    val alongRunPx = position.dragAlongRunPx(horizontalDragPx, verticalDragPx)
    return when {
        abs(awayPx) < DOCK_SHELF_GESTURE_THRESHOLD_PX || abs(awayPx) <= abs(alongRunPx) -> null
        isExpanded && awayPx < 0f -> false
        !isExpanded && awayPx > 0f -> true
        else -> null
    }
}

internal fun dockShelfGestureClaimsDrag(
    isExpanded: Boolean,
    horizontalDragPx: Float,
    verticalDragPx: Float,
    position: DockPosition = DockPosition.BOTTOM,
): Boolean {
    val awayPx = position.dragAwayFromEdgePx(horizontalDragPx, verticalDragPx)
    val alongRunPx = position.dragAlongRunPx(horizontalDragPx, verticalDragPx)
    return abs(awayPx) >= DOCK_SHELF_GESTURE_CLAIM_THRESHOLD_PX &&
        abs(awayPx) > abs(alongRunPx) &&
        if (isExpanded) awayPx < 0f else awayPx > 0f
}

internal fun dockShelfExpandedStateAfterBackgroundTap(isExpanded: Boolean): Boolean {
    return if (isExpanded) false else isExpanded
}

internal fun dockShelfExpandedStateForContent(
    isExpanded: Boolean,
    hasContent: Boolean,
): Boolean = isExpanded && hasContent

/**
 * Whether the shelf has anything to show: the user's panel, the notification section, or both.
 *
 * Not apps past the dock's capacity -- those are reached by scrolling the strip. Expanding used to
 * show *fewer* apps in the dock's own row than the collapsed dock did, which was the clearest sign
 * the shelf was answering the wrong question.
 */
internal fun dockHasExpandedContent(
    hasPanel: Boolean,
    notificationShelfState: DockNotificationShelfState,
): Boolean = hasPanel || notificationShelfState != DockNotificationShelfState.Hidden

internal fun Modifier.dockShelfGestureInput(interactions: DockInteractions): Modifier =
    fillMaxWidth()
        .dockShelfGestureInput(
            isExpanded = interactions.isShelfExpanded,
            position = interactions.position,
            // A dock whose shelf is reached by button never claims the drag, which is what hands
            // swipe-up back to the dock's own gesture action.
            onExpandedChange =
                interactions.onShelfExpandedChange
                    ?.takeIf { interactions.shelfExpandAffordance == DockExpandAffordance.GESTURE },
        )

private fun Modifier.dockShelfGestureInput(
    isExpanded: Boolean,
    position: DockPosition,
    onExpandedChange: ((Boolean) -> Unit)?,
): Modifier {
    if (onExpandedChange == null) {
        return this
    }
    return composed {
        val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
        pointerInput(isExpanded, position) {
            awaitEachGesture {
                // Default (Main) pass, not Initial: Main dispatches descendant-first, so a
                // descendant gesture (e.g. a horizontal scroll on the dock icon or notification
                // row) gets first look at the same event and can consume it before this
                // ancestor-level handler runs -- Initial dispatched ancestor-first instead,
                // letting this claim any qualifying drag regardless of what a descendant wanted
                // it for.
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                var handled = false
                while (!handled) {
                    val event = awaitPointerEvent()
                    val trackedChange = event.changes.firstOrNull { change -> change.id == down.id }
                    if (trackedChange == null) {
                        handled = true
                    } else if (trackedChange.isConsumed) {
                        // A descendant already claimed this drag for itself.
                        handled = true
                    } else {
                        val drag = trackedChange.position - start
                        if (
                            dockShelfGestureClaimsDrag(
                                isExpanded = isExpanded,
                                horizontalDragPx = drag.x,
                                verticalDragPx = drag.y,
                                position = position,
                            )
                        ) {
                            trackedChange.consume()
                        }
                        dockShelfGestureExpandedState(
                            isExpanded = isExpanded,
                            horizontalDragPx = drag.x,
                            verticalDragPx = drag.y,
                            position = position,
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

private const val DOCK_SHELF_GESTURE_THRESHOLD_PX = 80f

// DockShelfGestureInteractionTest pins this to 24f (claiming no later than a 24px drag, ahead of
// homeGestureInput's own threshold) -- raising it broke that guarantee against those tests' fixed
// two-step synthetic drags. The eager-capture fix here is the Main-pass + isConsumed change above,
// not this threshold.
private const val DOCK_SHELF_GESTURE_CLAIM_THRESHOLD_PX = 24f
