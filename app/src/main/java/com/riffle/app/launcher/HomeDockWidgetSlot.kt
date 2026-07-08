package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.WidgetItem

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun DockWidgetSlot(
    widget: WidgetItem,
    iconSizeDp: Int,
    isEditing: Boolean,
    shortcutIndex: Int,
    shortcutCount: Int,
    presentation: DockPresentation,
) {
    val isContextMenuExpanded = remember(widget.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier.requiredSize(iconSizeDp.dp),
    ) {
        DockWidget(
            widget = widget,
            widgetViewFactory = presentation.widgetViewFactory,
            iconSizeDp = iconSizeDp,
        )
        Box(
            modifier =
                Modifier
                    .requiredSize(iconSizeDp.dp)
                    .combinedClickable(
                        onClick = {
                            Unit
                        },
                        onLongClick = {
                            presentation.interactions.haptics.longPress()
                            isContextMenuExpanded.value = true
                        },
                        onLongClickLabel = "Show ${widget.label} actions",
                    ),
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
}
