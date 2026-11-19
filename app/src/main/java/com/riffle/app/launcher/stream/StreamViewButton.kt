package com.riffle.app.launcher.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

internal const val STREAM_VIEW_BUTTON_TEST_TAG = "stream-view-button"

/**
 * A small visible control for what a long-press of a Dock tile (or the Stream's empty room) opens: the page's options,
 * Show as, Move and Hide. A 48 dp target with a Thin glass disc; [onClick] is given the button's centre in root
 * coordinates, where the menu hangs from. It does not move.
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
