package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
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
        val slop = viewConfiguration.touchSlop
        // Null: the long-press time passed in the clock; true: an event stamped after it arrived; false: not ours.
        val held =
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.changedToUpIgnoreConsumed() || event.changes.size > 1) {
                        return@withTimeoutOrNull false
                    }
                    if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                        return@withTimeoutOrNull true
                    }
                    if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
        if (held == false) return@awaitEachGesture
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
