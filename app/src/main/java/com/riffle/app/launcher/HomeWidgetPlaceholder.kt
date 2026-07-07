package com.riffle.app.launcher

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.WidgetItem

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HomeWidgetPlaceholder(
    widget: WidgetItem,
    isEditing: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    dragState: HomeItemDragState? = null,
    workspaceActions: HomeWorkspaceActions? = null,
) {
    val context = LocalContext.current
    val hostedWidgetView =
        remember(context, widget.appWidgetId, widgetViewFactory) {
            widgetViewFactory.createHostedWidgetView(context, widget)
        }
    val isContextMenuExpanded = remember(widget.id) { mutableStateOf(false) }

    DisposableEffect(hostedWidgetView) {
        onDispose {
            hostedWidgetView?.removeFromParent()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (hostedWidgetView == null) {
            HomeWidgetFallbackLabel(widget)
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { hostedWidgetView },
                update = { view ->
                    view.setOnLongClickListener(null)
                },
            )
        }
        WidgetGestureLayer(
            widget = widget,
            dragState = dragState,
            workspaceActions = workspaceActions,
            onStationaryLongPress = { isContextMenuExpanded.value = true },
        )
        if (isEditing) {
            WidgetEditHandles(
                widget = widget,
                grid = dragState?.grid,
                onAction = onAction,
            )
        }
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items = widgetPlaceholderContextMenuItems(widget, dragState?.grid),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = onAction,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BoxScope.WidgetGestureLayer(
    widget: WidgetItem,
    dragState: HomeItemDragState?,
    workspaceActions: HomeWorkspaceActions?,
    onStationaryLongPress: () -> Unit,
) {
    val dragModifier =
        when {
            dragState != null && workspaceActions != null ->
                Modifier.homeItemDrag(
                    enabled = true,
                    item = widget,
                    dragState = dragState,
                    actions = workspaceActions,
                    onStationaryLongPress = onStationaryLongPress,
                )

            else -> Modifier
        }

    Box(
        modifier =
            Modifier
                .matchParentSize()
                .combinedClickable(
                    onClick = {
                        Unit
                    },
                    onLongClickLabel = "Show ${widget.label} actions",
                )
                .then(dragModifier),
    )
}

@Composable
private fun BoxScope.WidgetEditHandles(
    widget: WidgetItem,
    grid: GridDimensions?,
    onAction: (LauncherShellAction) -> Unit,
) {
    val currentPlacement = widget.placement
    val canGrowColumns =
        currentPlacement != null &&
            grid != null &&
            currentPlacement.cell.column + currentPlacement.span.columns < grid.columns
    val canGrowRows =
        currentPlacement != null &&
            grid != null &&
            currentPlacement.cell.row + currentPlacement.span.rows < grid.rows
    val canShrinkColumns = (currentPlacement?.span?.columns ?: 1) > 1
    val canShrinkRows = (currentPlacement?.span?.rows ?: 1) > 1

    RemoveShortcutButton(
        label = widget.label,
        onClick = { onAction(LauncherShellAction.RemoveHomeShortcut(widget.id)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.CenterEnd),
        label = "+",
        contentDescription = "Make ${widget.label} wider",
        enabled = canGrowColumns,
        onClick = { onAction(widget.resizeAction(columnsDelta = 1, rowsDelta = 0)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.CenterStart),
        label = "-",
        contentDescription = "Make ${widget.label} narrower",
        enabled = canShrinkColumns,
        onClick = { onAction(widget.resizeAction(columnsDelta = -1, rowsDelta = 0)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.BottomCenter),
        label = "+",
        contentDescription = "Make ${widget.label} taller",
        enabled = canGrowRows,
        onClick = { onAction(widget.resizeAction(columnsDelta = 0, rowsDelta = 1)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.TopCenter),
        label = "-",
        contentDescription = "Make ${widget.label} shorter",
        enabled = canShrinkRows,
        onClick = { onAction(widget.resizeAction(columnsDelta = 0, rowsDelta = -1)) },
    )
}

@Composable
private fun WidgetResizeHandle(
    modifier: Modifier,
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(WIDGET_RESIZE_HANDLE_SIZE_DP.dp)
                .alpha(if (enabled) 1f else WIDGET_RESIZE_HANDLE_DISABLED_ALPHA)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun BoxScope.HomeWidgetFallbackLabel(widget: WidgetItem) {
    Text(
        modifier = Modifier.align(Alignment.Center),
        text = widget.label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun View.removeFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}

internal fun widgetPlaceholderContextMenuItems(
    widget: WidgetItem,
    grid: GridDimensions? = null,
): List<ShortcutContextMenuItem> {
    val currentPlacement = widget.placement
    val currentSpan = currentPlacement?.span ?: GridSpan()

    return listOf(
        ShortcutContextMenuItem(
            label = "Make wider",
            action = widget.resizeAction(columnsDelta = 1, rowsDelta = 0),
            enabled =
                currentPlacement != null &&
                    (grid == null || currentPlacement.cell.column + currentSpan.columns < grid.columns),
        ),
        ShortcutContextMenuItem(
            label = "Make narrower",
            action = widget.resizeAction(columnsDelta = -1, rowsDelta = 0),
            enabled = currentSpan.columns > 1,
        ),
        ShortcutContextMenuItem(
            label = "Make taller",
            action = widget.resizeAction(columnsDelta = 0, rowsDelta = 1),
            enabled =
                currentPlacement != null &&
                    (grid == null || currentPlacement.cell.row + currentSpan.rows < grid.rows),
        ),
        ShortcutContextMenuItem(
            label = "Make shorter",
            action = widget.resizeAction(columnsDelta = 0, rowsDelta = -1),
            enabled = currentSpan.rows > 1,
        ),
        ShortcutContextMenuItem(
            label = "Remove from home",
            action = LauncherShellAction.RemoveHomeShortcut(widget.id),
        ),
    )
}

private fun WidgetItem.resizeAction(
    columnsDelta: Int,
    rowsDelta: Int,
): LauncherShellAction.ResizeHomeWidget {
    val currentSpan = placement?.span ?: GridSpan()

    return LauncherShellAction.ResizeHomeWidget(
        itemId = id,
        span =
            GridSpan(
                columns = (currentSpan.columns + columnsDelta).coerceAtLeast(1),
                rows = (currentSpan.rows + rowsDelta).coerceAtLeast(1),
            ),
    )
}

private const val WIDGET_RESIZE_HANDLE_SIZE_DP = 28
private const val WIDGET_RESIZE_HANDLE_DISABLED_ALPHA = 0.38f
