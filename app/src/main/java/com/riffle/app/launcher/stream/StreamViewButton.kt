package com.riffle.app.launcher.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.centreInRoot
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.designsystem.GlassWeight

/** Whether the Stream's pages draw the "Show as" button (Settings > Stream > Show view button); off unless provided. */
internal val LocalStreamViewButton = staticCompositionLocalOf { false }

/**
 * Which pages leave the button's corner clear (ADR 0003, Amendment 18). The button stands at the top end of the
 * Stream's area, over whatever the page draws there, with no room reserved for it (a stack's geometry is the Dock's
 * and the pager's, and must not move for a control). So a page that would reach the corner says so, and the button is
 * not drawn over it: a List starts at the top, and a stack that is tall, or has many strips behind it, can stand up to
 * it. The Dock tile's long-press and the empty room's long-press still open the same options.
 */
@Stable
internal class StreamViewButtonRoom {
    private val blocked = mutableStateMapOf<String, Boolean>()

    /** Whether the page [key] leaves the corner clear; a page that said nothing does (an empty or a notice page). */
    fun isClear(key: String?): Boolean = key == null || blocked[key] != true

    fun report(
        key: String,
        clear: Boolean,
    ) {
        if ((blocked[key] == true) == clear) blocked[key] = !clear
    }
}

internal val LocalStreamViewButtonRoom = staticCompositionLocalOf<StreamViewButtonRoom?> { null }

/** The page [key] reaches the button's corner for as long as this is composed (a List). */
@Composable
internal fun ReachesViewButton(key: String) {
    val room = LocalStreamViewButtonRoom.current ?: return
    SideEffect { room.report(key, clear = false) }
    DisposableEffect(room, key) { onDispose { room.report(key, clear = true) } }
}

internal const val STREAM_VIEW_BUTTON_TEST_TAG = "stream-view-button"

/**
 * A small visible control for what a long-press of a Dock tile (or the Stream's empty room) opens: the page's options,
 * Show as, Move and Hide. A 48 dp target with a Thin glass disc; [onClick] is given the button's centre in root
 * coordinates, where the menu hangs from. It does not move, and it is not drawn over a page that reaches its corner
 * ([StreamViewButtonRoom]).
 */
@Composable
internal fun StreamViewButton(
    onClick: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val label = stringResource(R.string.stream_page_options_label)
    Box(
        modifier =
            modifier
                .size(StreamMinTarget)
                .testTag(STREAM_VIEW_BUTTON_TEST_TAG)
                .onGloballyPositioned { coordinates = it }
                .clickable(role = Role.Button, onClickLabel = label) {
                    onClick(coordinates?.takeIf { it.isAttached }?.centreInRoot() ?: Offset.Zero)
                },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier.size(36.dp),
            weight = GlassWeight.Thin,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(StreamIcons.Views, contentDescription = label, modifier = Modifier.size(20.dp))
            }
        }
    }
}
