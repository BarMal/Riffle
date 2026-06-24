package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
                shortcut = dock.items.getOrNull(index) as? AppShortcutItem,
                iconSizeDp = dock.iconSizeDp,
                isEditing = isEditing,
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

private data class DockShortcutState(
    val iconSizeDp: Int,
    val isEditing: Boolean,
    val notificationCount: Int,
    val appShortcuts: List<AppShortcut>,
)

@Composable
private fun DockSlot(
    modifier: Modifier,
    shortcut: AppShortcutItem?,
    iconSizeDp: Int,
    isEditing: Boolean,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val editingSlotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .then(if (isEditing) Modifier.background(editingSlotColor) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (shortcut != null) {
            DockShortcut(
                shortcut = shortcut,
                state =
                    DockShortcutState(
                        iconSizeDp = iconSizeDp,
                        isEditing = isEditing,
                        notificationCount =
                            presentation.notificationCountsByPackage[shortcut.appIdentity.packageName] ?: 0,
                        appShortcuts = presentation.appShortcutsByApp[shortcut.appIdentity].orEmpty(),
                    ),
                presentation = presentation,
                appIconLoader = appIconLoader,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BoxScope.DockShortcut(
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
                        enabled = !state.isEditing,
                        onClick = { presentation.onAction(shortcut.launchAction()) },
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
        if (!state.isEditing) {
            ShortcutContextMenu(
                expanded = isContextMenuExpanded.value,
                items =
                    shortcutContextMenuItems(
                        shortcut = shortcut,
                        surface = ShortcutContextSurface.DOCK,
                        appShortcuts = state.appShortcuts,
                    ),
                onDismissRequest = { isContextMenuExpanded.value = false },
                onAction = presentation.onAction,
            )
        }
    }

    if (state.isEditing) {
        MoveDockShortcutControls(
            shortcut = shortcut,
            onAction = presentation.onAction,
        )
        RemoveDockShortcutButton(
            label = shortcut.label,
            onClick = { presentation.onAction(LauncherShellAction.RemoveDockShortcut(shortcut.id)) },
        )
        AppInfoShortcutButton(
            label = shortcut.label,
            onClick = { presentation.onAction(shortcut.openAppInfoAction()) },
        )
    }
}

@Composable
private fun BoxScope.MoveDockShortcutControls(
    shortcut: AppShortcutItem,
    onAction: (LauncherShellAction) -> Unit,
) {
    MoveDockShortcutButton(
        label = shortcut.label,
        direction = DockItemMoveDirection.LEFT,
        text = "<",
        alignment = Alignment.CenterStart,
        onClick = {
            onAction(
                LauncherShellAction.MoveDockShortcut(
                    itemId = shortcut.id,
                    direction = DockItemMoveDirection.LEFT,
                ),
            )
        },
    )
    MoveDockShortcutButton(
        label = shortcut.label,
        direction = DockItemMoveDirection.RIGHT,
        text = ">",
        alignment = Alignment.CenterEnd,
        onClick = {
            onAction(
                LauncherShellAction.MoveDockShortcut(
                    itemId = shortcut.id,
                    direction = DockItemMoveDirection.RIGHT,
                ),
            )
        },
    )
}

@Composable
private fun BoxScope.MoveDockShortcutButton(
    label: String,
    direction: DockItemMoveDirection,
    text: String,
    alignment: Alignment,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .align(alignment)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Move $label in dock ${direction.name.lowercase()}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun BoxScope.RemoveDockShortcutButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Remove $label from dock" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "X",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
