package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import kotlin.math.abs

/**
 * Maps the Dock's swipe-up gesture action to a shell action. Restricted to the three actions the
 * Dock physically supports: staying put, returning to Standard Home from Cards mode, and opening
 * the app drawer. Distinct from [dockShelfGestureInput], which only expands/collapses the
 * notification overflow shelf and never navigates.
 */
internal fun LauncherGestureAction.toDockSwipeUpShellAction(): LauncherShellAction? =
    when (this) {
        LauncherGestureAction.NONE -> null
        LauncherGestureAction.EXIT_ADAPTIVE_STAGE ->
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER)
        LauncherGestureAction.OPEN_APP_DRAWER -> LauncherShellAction.OpenAppDrawer
        // The Dock swipe-up binding only persists one of the three actions above; any other value
        // (e.g. from a future migration) is treated as a no-op rather than crashing.
        else -> null
    }

internal fun dockSwipeUpGestureTriggered(
    horizontalDragPx: Float,
    verticalDragPx: Float,
): Boolean =
    verticalDragPx <= -DOCK_SWIPE_UP_GESTURE_THRESHOLD_PX &&
        abs(verticalDragPx) > abs(horizontalDragPx)

/** Attaches the Dock swipe-up mode-switch gesture. Callers gate [enabled] to avoid overlapping
 * the shelf-expand gesture's own swipe-up claim on the same touch region. */
internal fun Modifier.dockSwipeUpGestureInput(
    enabled: Boolean,
    action: LauncherGestureAction,
    onAction: (LauncherShellAction) -> Unit,
): Modifier {
    val shellAction = action.toDockSwipeUpShellAction()
    if (!enabled || shellAction == null) {
        return this
    }
    return composed {
        val currentOnAction by rememberUpdatedState(onAction)
        val currentShellAction by rememberUpdatedState(shellAction)
        pointerInput(action) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val start = down.position
                var handled = false
                while (!handled) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val trackedChange = event.changes.firstOrNull { change -> change.id == down.id }
                    if (trackedChange == null) {
                        handled = true
                    } else {
                        val drag = trackedChange.position - start
                        if (dockSwipeUpGestureTriggered(horizontalDragPx = drag.x, verticalDragPx = drag.y)) {
                            trackedChange.consume()
                            currentOnAction(currentShellAction)
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

private const val DOCK_SWIPE_UP_GESTURE_THRESHOLD_PX = 80f
