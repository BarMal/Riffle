package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.LauncherGestureAction

/**
 * Maps the Dock's swipe-up gesture action to a shell action. Restricted to the three actions the
 * Dock physically supports: staying put, returning to Standard Home from Cards mode, and opening
 * the app drawer. Distinct from [dockShelfGestureInput], which only expands/collapses the
 * notification overflow shelf and never navigates.
 *
 * [viewMode] is what the launcher is showing now, because one of those three only means anything
 * from one mode.
 */
internal fun LauncherGestureAction.toDockSwipeUpShellAction(viewMode: LauncherViewMode): LauncherShellAction? =
    when (this) {
        LauncherGestureAction.NONE -> null
        // Only from Cards, where there is something to exit. Firing it everywhere turned a swipe on
        // the Library dock into a jump to Standard -- a mode the user had not asked for, and one
        // whose layout has a dock of its own, so the dock they had swiped on was replaced too.
        // Where it returns to is the layout set's call, not this mapper's -- see ExitAdaptiveStage.
        LauncherGestureAction.EXIT_ADAPTIVE_STAGE ->
            LauncherShellAction.ExitAdaptiveStage.takeIf { viewMode == LauncherViewMode.CARD_INTERFACE }
        LauncherGestureAction.OPEN_APP_DRAWER -> LauncherShellAction.OpenAppDrawer
        // The Dock swipe-up binding only persists one of the three actions above; any other value
        // (e.g. from a future migration) is treated as a no-op rather than crashing.
        else -> null
    }

/**
 * Attaches the Dock swipe-up mode-switch gesture. Callers gate [enabled] to avoid overlapping
 * the shelf-expand gesture's own swipe-up claim on the same touch region.
 *
 * Axis-intent (vertical vs. horizontal) and touch-slop filtering are delegated to
 * [detectVerticalDragGestures] rather than a hand-rolled post-hoc `|dy| > |dx|` comparison --
 * that comparison was the same category of bug this app's card-stack fling gesture shipped with
 * (ADR 0002). [onVerticalDrag] only starts firing once Foundation's own touch-slop lock has
 * already decided the drag is vertical.
 */
internal fun Modifier.dockSwipeUpGestureInput(
    enabled: Boolean,
    action: LauncherGestureAction,
    viewMode: LauncherViewMode,
    onAction: (LauncherShellAction) -> Unit,
): Modifier {
    val shellAction = action.toDockSwipeUpShellAction(viewMode)
    if (!enabled || shellAction == null) {
        return this
    }
    return composed {
        val currentOnAction by rememberUpdatedState(onAction)
        val currentShellAction by rememberUpdatedState(shellAction)
        pointerInput(action) {
            var accumulatedVerticalDragPx = 0f
            var triggered = false
            detectVerticalDragGestures(
                onDragStart = {
                    accumulatedVerticalDragPx = 0f
                    triggered = false
                },
                onVerticalDrag = { change, dragAmount ->
                    if (triggered) return@detectVerticalDragGestures
                    accumulatedVerticalDragPx += dragAmount
                    if (accumulatedVerticalDragPx <= -DOCK_SWIPE_UP_GESTURE_THRESHOLD_PX) {
                        change.consume()
                        triggered = true
                        currentOnAction(currentShellAction)
                    }
                },
            )
        }
    }
}

private const val DOCK_SWIPE_UP_GESTURE_THRESHOLD_PX = 80f
