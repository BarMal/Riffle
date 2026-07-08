package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.riffle.core.domain.launcher.home.WidgetItem

@Composable
internal fun DockWidgetSlot(
    widget: WidgetItem,
    iconSizeDp: Int,
    isEditing: Boolean,
    shortcutIndex: Int,
    shortcutCount: Int,
    presentation: DockPresentation,
) {
    val isContextMenuExpanded = remember(widget.id) { mutableStateOf(false) }

    DockWidget(
        widget = widget,
        widgetViewFactory = presentation.widgetViewFactory,
        iconSizeDp = iconSizeDp,
        onLongClick = {
            presentation.interactions.haptics.longPress()
            isContextMenuExpanded.value = true
        },
    )
    ShortcutContextMenu(
        expanded = isContextMenuExpanded.value,
        items =
            dockWidgetContextMenuItems(
                widget = widget,
                isEditing = isEditing,
                shortcutIndex = shortcutIndex,
                shortcutCount = shortcutCount,
            ),
        onDismissRequest = { isContextMenuExpanded.value = false },
        onAction = presentation.interactions.onAction,
    )
}
