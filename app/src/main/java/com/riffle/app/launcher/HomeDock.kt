package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.DockModel

@Composable
fun Dock(
    dock: DockModel,
    isEditing: Boolean,
    notificationCountsByPackage: Map<AppPackageName, Int>,
    appShortcutsByApp: AppShortcutsByApp,
    appIconLoader: AppIconLoader,
    haptics: LauncherHaptics = NoopLauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
) {
    val presentation =
        DockPresentation(
            notificationCountsByPackage = notificationCountsByPackage,
            appShortcutsByApp = appShortcutsByApp,
            haptics = haptics,
            onAction = onAction,
        )

    Row(
        modifier =
            Modifier
                .widthIn(max = DOCK_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .height(dockHeightDp(dock.iconSizeDp).dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = dock.backgroundAlphaPercent / 100f,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(dock.itemSpacingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(dock.capacity) { index ->
            DockSlot(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                state =
                    DockSlotState(
                        shortcut = dock.items.getOrNull(index) as? AppShortcutItem,
                        shortcutIndex = index,
                        shortcutCount = dock.items.size,
                        iconSizeDp = dock.iconSizeDp,
                        isEditing = isEditing,
                    ),
                presentation = presentation,
                appIconLoader = appIconLoader,
            )
        }
    }
}

private const val DOCK_MAX_WIDTH_DP = 560
private const val DOCK_VERTICAL_CHROME_DP = 32

internal fun dockHeightDp(iconSizeDp: Int): Int = iconSizeDp + DOCK_VERTICAL_CHROME_DP

private data class DockPresentation(
    val notificationCountsByPackage: Map<AppPackageName, Int>,
    val appShortcutsByApp: AppShortcutsByApp,
    val haptics: LauncherHaptics,
    val onAction: (LauncherShellAction) -> Unit,
)

private data class DockSlotState(
    val shortcut: AppShortcutItem?,
    val shortcutIndex: Int,
    val shortcutCount: Int,
    val iconSizeDp: Int,
    val isEditing: Boolean,
)

private data class DockShortcutState(
    val iconSizeDp: Int,
    val shortcutIndex: Int,
    val shortcutCount: Int,
    val isEditing: Boolean,
    val notificationCount: Int,
    val appShortcuts: List<AppShortcut>,
)

@Composable
private fun DockSlot(
    modifier: Modifier,
    state: DockSlotState,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val editingSlotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .then(if (state.isEditing) Modifier.background(editingSlotColor) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (state.shortcut != null) {
            DockShortcut(
                shortcut = state.shortcut,
                state =
                    DockShortcutState(
                        iconSizeDp = state.iconSizeDp,
                        shortcutIndex = state.shortcutIndex,
                        shortcutCount = state.shortcutCount,
                        isEditing = state.isEditing,
                        notificationCount =
                            presentation.notificationCountsByPackage[state.shortcut.appIdentity.packageName] ?: 0,
                        appShortcuts = presentation.appShortcutsByApp[state.shortcut.appIdentity].orEmpty(),
                    ),
                presentation = presentation,
                appIconLoader = appIconLoader,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DockShortcut(
    shortcut: AppShortcutItem,
    state: DockShortcutState,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val isContextMenuExpanded = remember(shortcut.id) { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .size(state.iconSizeDp.dp),
    ) {
        LauncherAppIcon(
            identity = shortcut.appIdentity,
            label = shortcut.label,
            iconLoader = appIconLoader,
            modifier =
                Modifier
                    .size(state.iconSizeDp.dp)
                    .combinedClickable(
                        onClick = {
                            if (state.isEditing) {
                                isContextMenuExpanded.value = true
                            } else {
                                presentation.onAction(shortcut.launchAction())
                            }
                        },
                        onLongClick = {
                            presentation.haptics.longPress()
                            isContextMenuExpanded.value = true
                        },
                        onLongClickLabel = "Show ${shortcut.label} actions",
                    ),
        )

        if (!state.isEditing) {
            NotificationCountBadge(
                count = state.notificationCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                dockShortcutContextMenuItems(
                    shortcut = shortcut,
                    appShortcuts = state.appShortcuts,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = presentation.onAction,
        )
    }
}

internal fun dockShortcutContextMenuItems(
    shortcut: AppShortcutItem,
    appShortcuts: List<AppShortcut> = emptyList(),
    isEditing: Boolean = false,
    shortcutIndex: Int = 0,
    shortcutCount: Int = 1,
): List<ShortcutContextMenuItem> {
    val editItems =
        if (isEditing) {
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.LEFT,
                        ),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.RIGHT,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
            )
        } else {
            emptyList()
        }

    return editItems +
        shortcutContextMenuItems(
            shortcut = shortcut,
            surface = ShortcutContextSurface.DOCK,
            appShortcuts = appShortcuts,
            includeEditHome = !isEditing,
        )
}
