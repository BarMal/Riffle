package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
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
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem

internal sealed interface DockSlotItemState {
    val id: LauncherItemId
    val label: String

    data class Shortcut(val shortcut: AppShortcutItem) : DockSlotItemState {
        override val id: LauncherItemId = shortcut.id
        override val label: String = shortcut.label
    }

    data class Folder(val folder: FolderItem) : DockSlotItemState {
        override val id: LauncherItemId = folder.id
        override val label: String = folder.label
    }

    data class Placeholder(
        override val id: LauncherItemId,
        override val label: String,
        val kind: DockSlotPlaceholderKind,
    ) : DockSlotItemState
}

internal enum class DockSlotPlaceholderKind(
    val label: String,
) {
    FOLDER("folder"),
    WIDGET("widget"),
}

internal fun dockSlotItemState(item: LauncherItem?): DockSlotItemState? =
    when (item) {
        null -> null
        is AppShortcutItem -> DockSlotItemState.Shortcut(item)
        is FolderItem -> DockSlotItemState.Folder(item)
        is WidgetItem ->
            DockSlotItemState.Placeholder(
                id = item.id,
                label = item.label,
                kind = DockSlotPlaceholderKind.WIDGET,
            )
    }

@Composable
internal fun DockItemPlaceholder(
    item: DockSlotItemState.Placeholder,
    iconSizeDp: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .requiredSize(iconSizeDp.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .semantics {
                    contentDescription = "${item.label} ${item.kind.label} dock item"
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.label.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
