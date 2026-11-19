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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlinx.coroutines.withTimeoutOrNull

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
                        val touchSlop = viewConfiguration.touchSlop
                        val longPress = viewConfiguration.longPressTimeoutMillis
                        val held =
                            withTimeoutOrNull(longPress) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || change.changedToUpIgnoreConsumed() ||
                                        event.changes.size > 1
                                    ) {
                                        return@withTimeoutOrNull false
                                    }
                                    if (change.uptimeMillis - down.uptimeMillis >= longPress) {
                                        return@withTimeoutOrNull true
                                    }
                                    if ((change.position - down.position).getDistance() > touchSlop) {
                                        return@withTimeoutOrNull false
                                    }
                                }
                                @Suppress("UNREACHABLE_CODE")
                                false
                            }
                        if (held == false) return@awaitEachGesture
                        val dragSlop = latestDrag.slopDp * density
                        // The menu is open now. Released without moving past the drag distance, it stays.
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                            if (change.changedToUpIgnoreConsumed()) return@awaitEachGesture
                            if ((change.position - down.position).getDistance() > dragSlop) {
                                if (!startDrag(app, latestDrag, toRoot(change.position), latestDismiss)) {
                                    return@awaitEachGesture
                                }
                                event.changes.forEach { it.consume() }
                                break
                            }
                        }
                        carryDrag(down.id, latestDrag, ::toRoot)
                    }
                }
        }
    }

private fun startDrag(
    app: InstalledApp,
    drag: LibraryHomeDrag,
    pointerInRoot: Offset,
    onMenuDismiss: () -> Unit,
): Boolean {
    val bridge = drag.externalDrag() ?: return false
    onMenuDismiss()
    if (!bridge.start(ExternalHomeDragSource.Library(app), pointerInRoot)) return false
    drag.active.value = true
    return true
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.carryDrag(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    drag: LibraryHomeDrag,
    toRoot: (Offset) -> Offset,
) {
    var finished = false
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null) break
            val lifted = change.changedToUpIgnoreConsumed()
            event.changes.forEach { it.consume() }
            if (lifted) {
                drag.externalDrag()?.end()
                finished = true
                break
            }
            drag.externalDrag()?.move(toRoot(change.position))
        }
    } finally {
        if (!finished) drag.externalDrag()?.cancel()
        // [LibraryHomeDrag.active] stays set until the Library leaves composition, so Home does not flicker between.
        drag.onFinished()
    }
}
