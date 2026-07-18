package com.riffle.app.launcher

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.home.WidgetItem
import kotlin.math.roundToInt

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

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(LocalLauncherCardShape.current)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val widthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
        val heightDp = maxHeight.value.roundToInt().coerceAtLeast(1)

        LaunchedEffect(widget.appWidgetId, widthDp, heightDp) {
            widgetViewFactory.updateHostedWidgetSize(
                widget = widget,
                widthDp = widthDp,
                heightDp = heightDp,
            )
        }
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
                pageItems = dragState?.pageItems ?: listOf(widget),
                onAction = onAction,
            )
        }
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items = widgetPlaceholderContextMenuItems(widget, dragState?.grid, dragState?.pageItems ?: listOf(widget)),
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
    pageItems: List<LauncherItem>,
    onAction: (LauncherShellAction) -> Unit,
) {
    RemoveShortcutButton(
        label = widget.label,
        onClick = { onAction(LauncherShellAction.RemoveHomeShortcut(widget.id)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.CenterEnd),
        label = "+",
        contentDescription = "Make ${widget.label} wider",
        enabled = widget.canResize(columnsDelta = 1, rowsDelta = 0, grid = grid, pageItems = pageItems),
        onClick = { onAction(widget.resizeAction(columnsDelta = 1, rowsDelta = 0)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.CenterStart),
        label = "-",
        contentDescription = "Make ${widget.label} narrower",
        enabled = widget.canResize(columnsDelta = -1, rowsDelta = 0, grid = grid, pageItems = pageItems),
        onClick = { onAction(widget.resizeAction(columnsDelta = -1, rowsDelta = 0)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.BottomCenter),
        label = "+",
        contentDescription = "Make ${widget.label} taller",
        enabled = widget.canResize(columnsDelta = 0, rowsDelta = 1, grid = grid, pageItems = pageItems),
        onClick = { onAction(widget.resizeAction(columnsDelta = 0, rowsDelta = 1)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.TopCenter),
        label = "-",
        contentDescription = "Make ${widget.label} shorter",
        enabled = widget.canResize(columnsDelta = 0, rowsDelta = -1, grid = grid, pageItems = pageItems),
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
    pageItems: List<LauncherItem> = listOf(widget),
): List<ShortcutContextMenuItem> {
    return listOf(
        ShortcutContextMenuItem(
            label = "Make wider",
            action = widget.resizeAction(columnsDelta = 1, rowsDelta = 0),
            enabled = widget.canResize(columnsDelta = 1, rowsDelta = 0, grid = grid, pageItems = pageItems),
        ),
        ShortcutContextMenuItem(
            label = "Make narrower",
            action = widget.resizeAction(columnsDelta = -1, rowsDelta = 0),
            enabled = widget.canResize(columnsDelta = -1, rowsDelta = 0, grid = grid, pageItems = pageItems),
        ),
        ShortcutContextMenuItem(
            label = "Make taller",
            action = widget.resizeAction(columnsDelta = 0, rowsDelta = 1),
            enabled = widget.canResize(columnsDelta = 0, rowsDelta = 1, grid = grid, pageItems = pageItems),
        ),
        ShortcutContextMenuItem(
            label = "Make shorter",
            action = widget.resizeAction(columnsDelta = 0, rowsDelta = -1),
            enabled = widget.canResize(columnsDelta = 0, rowsDelta = -1, grid = grid, pageItems = pageItems),
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

private fun WidgetItem.canResize(
    columnsDelta: Int,
    rowsDelta: Int,
    grid: GridDimensions?,
    pageItems: List<LauncherItem>,
): Boolean {
    val placement = placement
    val resizedSpan =
        placement?.let { currentPlacement ->
            GridSpan(
                columns = currentPlacement.span.columns + columnsDelta,
                rows = currentPlacement.span.rows + rowsDelta,
            )
        }

    return when {
        placement == null || resizedSpan == null -> false
        columnsDelta != 0 && !resizeConstraints.supportsHorizontalResize -> false
        rowsDelta != 0 && !resizeConstraints.supportsVerticalResize -> false
        !resizeConstraints.permits(resizedSpan) -> false
        grid == null -> resizedSpan.columns >= 1 && resizedSpan.rows >= 1
        else ->
            GridPlacementEngine().resizeItem(
                page =
                    LauncherPage(
                        id = WIDGET_RESIZE_PREVIEW_PAGE_ID,
                        grid = grid,
                        items = pageItems,
                    ),
                itemId = id,
                span = resizedSpan,
            ) is PlaceLauncherItemResult.Placed
    }
}

private const val WIDGET_RESIZE_HANDLE_SIZE_DP = 28
private const val WIDGET_RESIZE_HANDLE_DISABLED_ALPHA = 0.38f
private val WIDGET_RESIZE_PREVIEW_PAGE_ID = LauncherPageId("widget-resize-preview")
