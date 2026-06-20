package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel

@Composable
fun Dock(
    dock: DockModel,
    isEditing: Boolean,
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
                .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(16.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (shortcut != null) {
            DockShortcut(
                shortcut = shortcut,
                isEditing = isEditing,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun BoxScope.DockShortcut(
    shortcut: AppShortcutItem,
    isEditing: Boolean,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    LauncherAppIcon(
        identity = shortcut.appIdentity,
        label = shortcut.label,
        iconLoader = appIconLoader,
        modifier =
            Modifier
                .size(44.dp)
                .clickable(enabled = !isEditing) {
                    onAction(LauncherShellAction.LaunchApp(shortcut.appIdentity))
                },
    )

    if (isEditing) {
        RemoveDockShortcutButton(
            label = shortcut.label,
            onClick = { onAction(LauncherShellAction.RemoveDockShortcut(shortcut.id)) },
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
