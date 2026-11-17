package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.LauncherItemId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** How long the dragged icon is held over the middle of another before it will pair (a lighter hold than a press). */
internal const val DOCK_PAIR_HOLD_MILLIS = 350L

/** The middle share of a tile that counts as "on it": the rest, and the gaps, are reordering. */
internal const val DOCK_PAIR_MIDDLE_FRACTION = 0.5f

/** Which pinned item the dragged icon's centre is over, and whether it is over that tile's middle. */
internal data class DockHover(
    val index: Int,
    val overMiddle: Boolean,
)

/**
 * Where a dragged pinned icon is: [draggedPx] is how far its centre has moved along the run from slot [originIndex],
 * [slotPx] one slot (icon plus gap) and [iconPx] one icon. The centre is over slot `n` when it is nearest `n`'s centre,
 * and over that tile's middle [DOCK_PAIR_MIDDLE_FRACTION] when it is within a quarter of the icon of the centre (so a
 * gap and the outer quarters on each side only reorder). Null when there are no slots.
 */
internal fun dockHover(
    originIndex: Int,
    draggedPx: Float,
    slotPx: Float,
    iconPx: Float,
    itemCount: Int,
): DockHover? {
    if (itemCount <= 0 || slotPx <= 0f || iconPx <= 0f) return null
    val position = originIndex + draggedPx / slotPx
    val index = position.roundToInt().coerceIn(0, itemCount - 1)
    val offsetPx = abs(position - index) * slotPx
    return DockHover(index, offsetPx <= iconPx * DOCK_PAIR_MIDDLE_FRACTION / 2f)
}

/**
 * The item the dragged icon would pair with when held there: the hovered one, when it is over its middle, is another
 * item than the dragged one, and both may be paired ([pairable] by dock index; null for a pair, a widget, a folder or
 * when pairs are not available, so nothing can be dropped onto a pair and a pair cannot be dropped onto anything).
 */
internal fun <T> dockPairCandidate(
    hover: DockHover?,
    originIndex: Int,
    pairable: List<T?>,
): T? =
    hover
        ?.takeIf { it.overMiddle && it.index != originIndex && pairable.getOrNull(originIndex) != null }
        ?.let { pairable.getOrNull(it.index) }

/**
 * The hold of a drag over an app it could pair with: [update] is given the candidate on every move; once the same one
 * has been held for [DOCK_PAIR_HOLD_MILLIS] it is [confirmed] (one light detent) and `onChange` is told; moving off it,
 * or onto another, unconfirms it. [reset] ends the drag. One timer at a time, in the drag's own scope.
 */
internal class DockPairHold(
    private val scope: CoroutineScope,
    private val haptics: LauncherHaptics,
) {
    /** The app the drag has been held over long enough to pair with, if any. */
    var confirmed: LauncherItemId? = null
        private set

    private var holding: LauncherItemId? = null
    private var timer: Job? = null

    fun update(
        candidate: LauncherItemId?,
        onChange: (LauncherItemId?) -> Unit,
    ) {
        if (candidate == holding) return
        timer?.cancel()
        holding = candidate
        if (confirmed != null) {
            confirmed = null
            onChange(null)
        }
        if (candidate != null) {
            timer =
                scope.launch {
                    delay(DOCK_PAIR_HOLD_MILLIS)
                    confirmed = candidate
                    haptics.perform(LauncherHapticEvent.DETENT)
                    onChange(candidate)
                }
        }
    }

    fun reset() {
        timer?.cancel()
        timer = null
        holding = null
        confirmed = null
    }
}
