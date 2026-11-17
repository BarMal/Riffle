package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.pair.PairIcon
import com.riffle.app.launcher.pair.accessibilityLabel
import com.riffle.core.domain.launcher.home.AppPairItem

/** What a pair's tile needs beyond the pair: where it stands in the Dock, and what to show on it. */
internal data class DockPairTileState(
    val iconSizeDp: Int,
    val shortcutIndex: Int,
    val shortcutCount: Int,
    val isEditing: Boolean,
    val notificationCount: Int,
)

/**
 * An app pair in the Dock: the two apps fused into one icon. A tap opens them side by side; a long press shows the
 * pair's menu ("Open side by side", "Swap sides", each app, "Unpair", and what every pinned item has).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun DockPairTile(
    pair: AppPairItem,
    state: DockPairTileState,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val isMenuExpanded = remember(pair.id) { mutableStateOf(false) }
    val haptics = presentation.interactions.haptics
    val shape = RoundedCornerShape(DOCK_ICON_MASK_CORNER_RADIUS_DP.dp)
    val label = pair.accessibilityLabel
    val gesture =
        if (state.isEditing) {
            Modifier.clickable(onClick = { isMenuExpanded.value = true })
        } else {
            Modifier.combinedClickable(
                onClick = {
                    haptics.perform(LauncherHapticEvent.COMMIT)
                    presentation.interactions.onAction(LauncherShellAction.LaunchAppPair(pair))
                },
                onLongClick = {
                    haptics.longPress()
                    isMenuExpanded.value = true
                },
                onLongClickLabel = "Show $label actions",
            )
        }
    Box(modifier = Modifier.requiredSize(state.iconSizeDp.dp)) {
        PairIcon(
            pair = pair,
            iconLoader = appIconLoader,
            size = state.iconSizeDp.dp,
            shape = shape,
            modifier =
                Modifier
                    .requiredSize(state.iconSizeDp.dp)
                    .then(gesture)
                    .clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Button
                    },
        )
        if (!state.isEditing) {
            NotificationCountBadge(
                count = state.notificationCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        ShortcutContextMenu(
            expanded = isMenuExpanded.value,
            items =
                dockPairContextMenuItems(
                    pair = pair,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                ),
            onDismissRequest = { isMenuExpanded.value = false },
            onAction = presentation.interactions.onAction,
        )
    }
}
