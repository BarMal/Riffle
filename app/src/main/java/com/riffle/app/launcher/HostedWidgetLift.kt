package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The long-press drag of an item that has to keep its own touches (a hosted platform widget), watching from the
 * initial pass so the view beneath is never covered: nothing is consumed until the press has been held still for the
 * long-press time, so a tap, a scroll or a swipe reaches the widget as it would anywhere else. At the long press the
 * drag starts and every later event is consumed on the way down, so the widget's view sees a cancel and the pager does
 * not scroll. A press that moves past touch slop or lifts first is not ours and nothing was taken.
 */
internal suspend fun PointerInputScope.detectDragAfterLongPressPassively(
    onDragStart: (Offset) -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!heldStillForLongPress(down)) return@awaitEachGesture
        onDragStart(down.position)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onDragCancel()
                return@awaitEachGesture
            }
            val lifted = change.changedToUpIgnoreConsumed()
            val delta = change.positionChange()
            event.changes.forEach { it.consume() }
            if (lifted) {
                onDragEnd()
                return@awaitEachGesture
            }
            onDrag(change, delta)
        }
    }
}

/**
 * Whether the press [down] stays within touch slop, with one finger down, until the long-press time has passed,
 * watching from the initial pass and consuming nothing. An event stamped after that time counts as well as the clock
 * (a test or a delayed frame delivers it late). False: the press lifted, moved or grew a second finger first, so it
 * is not a hold.
 */
internal suspend fun AwaitPointerEventScope.heldStillForLongPress(down: PointerInputChange): Boolean {
    val slop = viewConfiguration.touchSlop
    val timeout = viewConfiguration.longPressTimeoutMillis
    // Null: the long-press time passed in the clock; true: an event stamped after it arrived; false: not a hold.
    val held =
        withTimeoutOrNull(timeout) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                val ended = change == null || change.changedToUpIgnoreConsumed() || event.changes.size > 1
                val moved = change != null && (change.position - down.position).getDistance() > slop
                val late = change != null && change.uptimeMillis - down.uptimeMillis >= timeout
                if (ended || (moved && !late)) return@withTimeoutOrNull false
                if (late) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
    return held != false
}
