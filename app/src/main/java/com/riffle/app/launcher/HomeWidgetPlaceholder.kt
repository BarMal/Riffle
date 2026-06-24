package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.WidgetItem

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HomeWidgetPlaceholder(
    widget: WidgetItem,
    isEditing: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isContextMenuExpanded = remember(widget.id) { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                    if (isEditing) {
                        Modifier.combinedClickable(
                            onClick = { isContextMenuExpanded.value = true },
                            onLongClick = { isContextMenuExpanded.value = true },
                            onLongClickLabel = "Show ${widget.label} actions",
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = widget.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isEditing) {
            ShortcutContextMenu(
                expanded = isContextMenuExpanded.value,
                items = widgetPlaceholderContextMenuItems(widget),
                onDismissRequest = { isContextMenuExpanded.value = false },
                onAction = onAction,
            )
        }
    }
}

internal fun widgetPlaceholderContextMenuItems(widget: WidgetItem): List<ShortcutContextMenuItem> =
    listOf(
        ShortcutContextMenuItem(
            label = "Remove from home",
            action = LauncherShellAction.RemoveHomeShortcut(widget.id),
        ),
    )
