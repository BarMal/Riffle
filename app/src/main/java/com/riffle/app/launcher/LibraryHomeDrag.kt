package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.riffle.core.domain.launcher.apps.InstalledApp

/**
 * What a Library row needs to hand a held app over to Home as a drag (the Library's "Drag to Home"): the Home surface's
 * bridge, whether the Library is hidden while the drag runs, what closes the Library afterwards, and how far the held
 * finger moves before the menu becomes a drag (Drag tuning).
 */
class LibraryHomeDrag(
    val externalDrag: () -> HomeExternalDrag?,
    val active: MutableState<Boolean> = mutableStateOf(false),
    val onFinished: () -> Unit = {},
    val slopDp: Int = com.riffle.core.domain.launcher.home.HomeDragTuning.DEFAULT_LIBRARY_DRAG_SLOP_DP,
)

/**
 * The drag of a Library app to Home, on top of the app's tap and long-press menu. Watched from the initial pass and
 * never consuming until it starts, so the tap, the menu and the list's scroll work as before:
 * - a press that moves past touch slop before the long-press time is not ours (the list scrolls);
 * - held still for the long-press time the platform opens the app's menu (the row's own long-click);
 * - released without moving, the menu stays; moved past [LibraryHomeDrag.slopDp] while pressed, [onMenuDismiss] closes
 *   the menu, the Library goes away and the finger carries the app as a Home drag (a new item for the app).
 * A drop on a target adds it; a drop anywhere else, or a cancel, adds nothing. Either way the Library is closed.
 */
internal fun Modifier.libraryHomeDrag(
    app: InstalledApp,
    drag: LibraryHomeDrag?,
    onMenuDismiss: () -> Unit,
): Modifier =
    if (drag == null) {
        this
    } else {
        composed {
            val latestDrag by rememberUpdatedState(drag)
            val latestDismiss by rememberUpdatedState(onMenuDismiss)
            val coordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }

            fun toRoot(local: Offset): Offset = coordinates.value?.takeIf { it.isAttached }?.localToRoot(local) ?: local
            this
                .onGloballyPositioned { coordinates.value = it }
                .pointerInput(app.identity) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // Not a hold (the list scrolls, a tap): not ours. Held, the platform opens the menu.
                        if (!heldStillForLongPress(down)) return@awaitEachGesture
                        val start = awaitDragDistance(down, latestDrag.slopDp * density) ?: return@awaitEachGesture
                        val bridge = startDrag(app, latestDrag, toRoot(start.position), latestDismiss)
                        if (bridge) carryDrag(down.id, latestDrag, ::toRoot)
                    }
                }
        }
    }

/**
 * Waits, with the menu open, for the finger to move [distancePx] from where it landed: that event, or null when it
 * lifted first (the menu stays) or the pointer is gone. The event is consumed, so the list beneath does not scroll.
 */
private suspend fun AwaitPointerEventScope.awaitDragDistance(
    down: PointerInputChange,
    distancePx: Float,
): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || change.changedToUpIgnoreConsumed()) return null
        if ((change.position - down.position).getDistance() > distancePx) {
            event.changes.forEach { it.consume() }
            return change
        }
    }
}

private fun startDrag(
    app: InstalledApp,
    drag: LibraryHomeDrag,
    pointerInRoot: Offset,
    onMenuDismiss: () -> Unit,
): Boolean {
    val bridge = drag.externalDrag()
    onMenuDismiss()
    val started = bridge?.start(ExternalHomeDragSource.Library(app), pointerInRoot) == true
    if (started) drag.active.value = true
    return started
}

private suspend fun AwaitPointerEventScope.carryDrag(
    pointerId: PointerId,
    drag: LibraryHomeDrag,
    toRoot: (Offset) -> Offset,
) {
    var finished = false
    try {
        var carrying = true
        while (carrying) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId }
            event.changes.forEach { it.consume() }
            if (change == null) {
                carrying = false
            } else if (change.changedToUpIgnoreConsumed()) {
                drag.externalDrag()?.end()
                finished = true
                carrying = false
            } else {
                drag.externalDrag()?.move(toRoot(change.position))
            }
        }
    } finally {
        if (!finished) drag.externalDrag()?.cancel()
        // [LibraryHomeDrag.active] stays set until the Library leaves composition, so Home does not flicker between.
        drag.onFinished()
    }
}
