package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.DockModel

@Composable
fun Dock(
    dock: DockModel,
    isEditing: Boolean,
    notificationCountsByPackage: Map<AppPackageName, Int>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(dock.capacity) { index ->
            DockSlot(
                modifier =
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                shortcut = dock.items.getOrNull(index) as? AppShortcutItem,
                isEditing = isEditing,
                notificationCountsByPackage = notificationCountsByPackage,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun DockSlot(
    modifier: Modifier,
    shortcut: AppShortcutItem?,
    isEditing: Boolean,
    notificationCountsByPackage: Map<AppPackageName, Int>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
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
                isEditing = isEditing,
                notificationCount = notificationCountsByPackage[shortcut.appIdentity.packageName] ?: 0,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BoxScope.DockShortcut(
    shortcut: AppShortcutItem,
    isEditing: Boolean,
    notificationCount: Int,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherAppIcon(
        identity = shortcut.appIdentity,
        label = shortcut.label,
        iconLoader = appIconLoader,
        modifier =
            Modifier
                .size(48.dp)
                .combinedClickable(
                    enabled = !isEditing,
                    onClick = { onAction(shortcut.launchAction()) },
                    onLongClick = { onAction(LauncherShellAction.EnterHomeEditMode) },
                ),
    )

    if (isEditing) {
        MoveDockShortcutControls(
            shortcut = shortcut,
            onAction = onAction,
        )
        RemoveDockShortcutButton(
            label = shortcut.label,
            onClick = { onAction(LauncherShellAction.RemoveDockShortcut(shortcut.id)) },
        )
        AppInfoShortcutButton(
            label = shortcut.label,
            onClick = { onAction(shortcut.openAppInfoAction()) },
        )
    } else {
        NotificationCountBadge(
            count = notificationCount,
            modifier = Modifier.align(Alignment.TopEnd),
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
