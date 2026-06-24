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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.WidgetItem

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HomeWidgetPlaceholder(
    widget: WidgetItem,
    isEditing: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    haptics: LauncherHaptics = NoopLauncherHaptics,
    dragState: HomeItemDragState? = null,
    workspaceActions: HomeWorkspaceActions? = null,
) {
    val context = LocalContext.current
    val hostedWidgetView =
        remember(context, widget.appWidgetId, widgetViewFactory) {
            widgetViewFactory.createHostedWidgetView(context, widget)
        }

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
                    view.setOnLongClickListener {
                        haptics.longPress()
                        onAction(LauncherShellAction.EnterHomeEditMode)
                        true
                    }
                },
            )
        }
        WidgetGestureLayer(
            widget = widget,
            isEditing = isEditing,
            dragState = dragState,
            workspaceActions = workspaceActions,
            haptics = haptics,
            onAction = onAction,
        )
        if (isEditing) {
            WidgetEditHandles(
                widget = widget,
                onAction = onAction,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BoxScope.WidgetGestureLayer(
    widget: WidgetItem,
    isEditing: Boolean,
    dragState: HomeItemDragState?,
    workspaceActions: HomeWorkspaceActions?,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
) {
    val dragModifier =
        when {
            dragState != null && workspaceActions != null ->
                Modifier.homeItemDrag(
                    enabled = isEditing,
                    item = widget,
                    dragState = dragState,
                    actions = workspaceActions,
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
                    onLongClick = {
                        haptics.longPress()
                        if (!isEditing) {
                            onAction(LauncherShellAction.EnterHomeEditMode)
                        }
                    },
                    onLongClickLabel = "Show ${widget.label} actions",
                )
                .then(dragModifier),
    )
}

@Composable
private fun BoxScope.WidgetEditHandles(
    widget: WidgetItem,
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
        onClick = { onAction(widget.resizeAction(columnsDelta = 1, rowsDelta = 0)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.CenterStart),
        label = "-",
        contentDescription = "Make ${widget.label} narrower",
        onClick = { onAction(widget.resizeAction(columnsDelta = -1, rowsDelta = 0)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.BottomCenter),
        label = "+",
        contentDescription = "Make ${widget.label} taller",
        onClick = { onAction(widget.resizeAction(columnsDelta = 0, rowsDelta = 1)) },
    )
    WidgetResizeHandle(
        modifier = Modifier.align(Alignment.TopCenter),
        label = "-",
        contentDescription = "Make ${widget.label} shorter",
        onClick = { onAction(widget.resizeAction(columnsDelta = 0, rowsDelta = -1)) },
    )
}

@Composable
private fun WidgetResizeHandle(
    modifier: Modifier,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(WIDGET_RESIZE_HANDLE_SIZE_DP.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onClick)
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

internal fun widgetPlaceholderContextMenuItems(widget: WidgetItem): List<ShortcutContextMenuItem> =
    listOf(
        ShortcutContextMenuItem(
            label = "Remove from home",
            action = LauncherShellAction.RemoveHomeShortcut(widget.id),
        ),
    )

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
