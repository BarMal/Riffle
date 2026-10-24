package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.riffle.core.domain.launcher.dockpull.DockPullClaim
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.dockpull.dockPullClaimFor

/**
 * The workspace menu's share of the dock pull binding: the [modifier] for the dock body (identity without a menu),
 * and the keyboard shortcut's equivalent [openFromShortcut]. [isPresent] is false without a menu, which keeps the
 * Home/Library mode pull.
 */
internal class DockMenuPull(
    val isPresent: Boolean,
    val modifier: Modifier,
    private val canOpen: State<Boolean>,
    private val open: () -> Unit,
) {
    /** Opens the menu for the Ctrl+arrow shortcut when it may open now; false otherwise (the shortcut is not used). */
    fun openFromShortcut(): Boolean = (isPresent && canOpen.value).also { opened -> if (opened) open() }
}

@Composable
internal fun rememberDockMenuPull(
    browsing: Boolean,
    direction: DockPullDirection,
    onOpenWorkspaceMenu: (() -> Unit)?,
): DockMenuPull {
    val isPresent = onOpenWorkspaceMenu != null
    val canOpen = rememberUpdatedState(browsing && isPresent)
    val latestOpen = rememberUpdatedState(onOpenWorkspaceMenu)
    val open = remember { { latestOpen.value?.invoke() ?: Unit } }
    val modifier = if (isPresent) Modifier.dockMenuPullInput(direction, canOpen, open) else Modifier
    return DockMenuPull(isPresent, modifier, canOpen, open)
}

/**
 * The dock pull once the Home/Library pair has retired (S9, design 11.5, owner decision Q9): the same drag away from
 * the dock edge, claimed by the same rules as the mode pull ([dockPullClaimFor]: past touch slop, mostly along
 * [direction], before the long-press timeout so a held press stays a long-press and a scroll along the dock stays a
 * scroll), now opens the workspace menu. Nothing moves with the finger: the menu's own open animation (or, under
 * reduced motion, its instant appearance) is the feedback. The rest of the claimed gesture is consumed so it does not
 * reach the pages behind.
 *
 * [enabled] is read at each gesture (browsing, no widget being placed). [onOpen] must be stable (the gesture is only
 * restarted when [direction] changes); pass one that reads the latest callback.
 */
internal fun Modifier.dockMenuPullInput(
    direction: DockPullDirection,
    enabled: State<Boolean>,
    onOpen: () -> Unit,
): Modifier =
    pointerInput(direction) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabled.value) return@awaitEachGesture
            if (awaitMenuPullClaim(down, direction)) {
                onOpen()
                consumeUntilUp(down)
            }
        }
    }

private suspend fun AwaitPointerEventScope.awaitMenuPullClaim(
    down: PointerInputChange,
    direction: DockPullDirection,
): Boolean {
    val touchSlopPx = viewConfiguration.touchSlop
    val claimed =
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var decision: Boolean? = null
            while (decision == null) {
                val change = awaitPointerEvent().changes.firstOrNull { candidate -> candidate.id == down.id }
                if (change == null || !change.pressed || change.isConsumed) {
                    decision = false
                } else {
                    val drag = change.position - down.position
                    when (dockPullClaimFor(direction, drag.x, drag.y, touchSlopPx)) {
                        DockPullClaim.UNDECIDED -> Unit
                        DockPullClaim.REJECT -> decision = false
                        DockPullClaim.CLAIM -> {
                            change.consume()
                            decision = true
                        }
                    }
                }
            }
            decision
        }
    return claimed == true
}

private suspend fun AwaitPointerEventScope.consumeUntilUp(down: PointerInputChange) {
    var pressed = true
    while (pressed) {
        val change = awaitPointerEvent().changes.firstOrNull { candidate -> candidate.id == down.id }
        change?.consume()
        pressed = change?.pressed == true
    }
}
