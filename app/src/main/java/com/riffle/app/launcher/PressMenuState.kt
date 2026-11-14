package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A menu that a long-press opens where the finger is (dock modes plan, "Long-press coverage").
 *
 * The press point is kept in root coordinates, so any surface can open the menu another one hosts: a page of Home, the
 * margin around it, the Dock's room, the handle. [PressMenuAnchor] places the menu at that point and the platform's
 * popup keeps it on screen.
 */
@Stable
internal class PressMenuState {
    /** Whether the menu is showing. */
    var expanded by mutableStateOf(false)
        private set

    /** Where the press landed, in root coordinates (px). */
    var pointInRoot by mutableStateOf(Offset.Zero)
        private set

    fun show(point: Offset) {
        pointInRoot = point
        expanded = true
    }

    fun dismiss() {
        expanded = false
    }
}

/**
 * The platform's tap and long-press on this node, as one empty-space handler: a long-press that stays within touch slop
 * for the platform's long-press timeout plays the light haptic and opens [state] at the finger ([haptics] is the
 * launcher's wrapper); [onTap] is the empty tap, when the surface has one. A touch that something else under or over
 * it already consumed (an item, a drag, a scroll) never reaches it, and a drag cancels it, so it only ever answers a
 * press on room nothing else claims. Accessibility gets the same menu as a long-click action, labelled [longClickLabel]
 * (and a click action when [onTap] is given, labelled [onClickLabel]).
 */
internal fun Modifier.pressMenu(
    state: PressMenuState,
    haptics: LauncherHaptics,
    longClickLabel: String,
    onTap: (() -> Unit)? = null,
    /** What a click does, for assistive technology (with [onTap]). */
    onClickLabel: String? = null,
): Modifier =
    composed {
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val latestTap by rememberUpdatedState(onTap)
        val hasTap = onTap != null
        val open: (Offset) -> Unit = { local ->
            haptics.longPress()
            val node = coordinates
            state.show(if (node != null && node.isAttached) node.localToRoot(local) else local)
        }
        val latestOpen by rememberUpdatedState(open)
        // Whether the finger has left touch slop: the platform's tap detector times the long-press from the touch down,
        // so a finger that has moved away (a swipe nothing claimed) and then rests is told apart here, and is not one.
        val moved = remember { BooleanArray(1) }
        this
            .onGloballyPositioned { coordinates = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    moved[0] = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            moved[0] = true
                        }
                    }
                }
            }
            .pointerInput(hasTap) {
                detectTapGestures(
                    onTap = if (hasTap) ({ latestTap?.invoke() }) else null,
                    onLongPress = { local -> if (!moved[0]) latestOpen(local) },
                )
            }
            .semantics {
                if (hasTap) {
                    onClick(label = onClickLabel, action = {
                        latestTap?.invoke()
                        true
                    })
                }
                onLongClick(
                    label = longClickLabel,
                    action = {
                        // No finger to follow: the node's middle.
                        val node = coordinates
                        val centre = node?.let { Offset(it.size.width / 2f, it.size.height / 2f) } ?: Offset.Zero
                        latestOpen(centre)
                        true
                    },
                )
            }
    }

/**
 * Where [PressMenuState] is hosted: an empty node at the press point, which the menu hangs from (so it opens at the
 * finger and the popup flips or slides to stay on screen). Place it in the [Box] that holds the surface; [menu] is the
 * popup itself, shown while [PressMenuState.expanded].
 */
@Composable
internal fun BoxScope.PressMenuAnchor(
    state: PressMenuState,
    menu: @Composable () -> Unit,
) {
    var parentOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .onGloballyPositioned { coordinates ->
                    parentOrigin = coordinates.parentLayoutCoordinates?.positionInRoot() ?: Offset.Zero
                }
                .offset {
                    val point = state.pointInRoot - parentOrigin
                    IntOffset(point.x.roundToInt(), point.y.roundToInt())
                }
                .testTag(PRESS_MENU_ANCHOR_TEST_TAG)
                .size(0.dp),
    ) {
        menu()
    }
}

/** The point to put the menu at for something that cannot say where it was pressed: the middle of [node]. */
internal fun LayoutCoordinates.centreInRoot(): Offset = localToRoot(Offset(size.width / 2f, size.height / 2f))

/** The test tag of the empty node a [PressMenuAnchor] puts at the press point, which the menu hangs from. */
internal const val PRESS_MENU_ANCHOR_TEST_TAG = "press-menu-anchor"
