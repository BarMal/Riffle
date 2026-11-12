package com.riffle.app.launcher.glance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.HomeItemDragState
import com.riffle.app.launcher.HomeWorkspaceActions
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.ShortcutContextMenu
import com.riffle.app.launcher.ShortcutContextSurface
import com.riffle.app.launcher.WidgetEditHandles
import com.riffle.app.launcher.WidgetGestureLayer
import com.riffle.app.launcher.homeItemDrag
import com.riffle.app.launcher.widgetPlaceholderContextMenuItems
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.WidgetItem

/**
 * A glance widget as an item of the Home grid: the same drag, resize handles, context menu and removal as a hosted
 * widget, around the app's own drawing instead of a platform view. Outside edit mode the widget is live and its
 * controls take their own taps; a long press still starts a drag (or opens the menu when the finger does not move),
 * and the release of that long press never also presses a control ([GlanceWidget]'s `interactive`). In edit mode the
 * gesture layer covers it, exactly as it covers a hosted widget.
 */
@Composable
internal fun GlanceHomeWidget(
    widget: WidgetItem,
    kind: GlanceWidgetKind,
    isEditing: Boolean,
    contextSurface: ShortcutContextSurface,
    dragState: HomeItemDragState?,
    workspaceActions: HomeWorkspaceActions?,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isContextMenuExpanded = remember(widget.id) { mutableStateOf(false) }
    val editing = rememberUpdatedState(isEditing)
    val interactive =
        remember(workspaceActions) { { !editing.value && workspaceActions?.currentDragSession() == null } }
    val dragModifier =
        if (!isEditing && dragState != null && workspaceActions != null) {
            Modifier.homeItemDrag(
                enabled = true,
                item = widget,
                dragState = dragState,
                actions = workspaceActions,
                onStationaryLongPress = { isContextMenuExpanded.value = true },
            )
        } else {
            Modifier
        }
    Box(modifier = Modifier.fillMaxSize()) {
        GlanceWidget(
            kind = kind,
            span = widget.placement?.span ?: kind.defaultSpan,
            interactive = interactive,
            modifier = Modifier.fillMaxSize().then(dragModifier),
        )
        if (isEditing) {
            WidgetGestureLayer(
                widget = widget,
                dragState = dragState,
                workspaceActions = workspaceActions,
                onStationaryLongPress = { isContextMenuExpanded.value = true },
            )
            WidgetEditHandles(
                widget = widget,
                grid = dragState?.grid,
                pageItems = dragState?.pageItems ?: listOf(widget),
                contextSurface = contextSurface,
                onAction = onAction,
            )
        }
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                widgetPlaceholderContextMenuItems(
                    widget = widget,
                    grid = dragState?.grid,
                    pageItems = dragState?.pageItems ?: listOf(widget),
                    contextSurface = contextSurface,
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = onAction,
        )
    }
}
