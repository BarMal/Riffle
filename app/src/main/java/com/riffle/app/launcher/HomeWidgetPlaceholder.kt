package com.riffle.app.launcher

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.WidgetItem

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HomeWidgetPlaceholder(
    widget: WidgetItem,
    isEditing: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    haptics: LauncherHaptics = NoopLauncherHaptics,
) {
    val isContextMenuExpanded = remember(widget.id) { mutableStateOf(false) }
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
        if (isEditing || hostedWidgetView == null) {
            WidgetGestureLayer(
                widget = widget,
                isEditing = isEditing,
                haptics = haptics,
                onMenuRequest = { isContextMenuExpanded.value = true },
                onAction = onAction,
            )
        }
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BoxScope.WidgetGestureLayer(
    widget: WidgetItem,
    isEditing: Boolean,
    haptics: LauncherHaptics,
    onMenuRequest: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .combinedClickable(
                    onClick = {
                        if (isEditing) {
                            onMenuRequest()
                        }
                    },
                    onLongClick = {
                        haptics.longPress()
                        if (isEditing) {
                            onMenuRequest()
                        } else {
                            onAction(LauncherShellAction.EnterHomeEditMode)
                        }
                    },
                    onLongClickLabel = "Show ${widget.label} actions",
                ),
    )
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
            label = "Make wider",
            action = widget.resizeAction(columnsDelta = 1, rowsDelta = 0),
        ),
        ShortcutContextMenuItem(
            label = "Make narrower",
            action = widget.resizeAction(columnsDelta = -1, rowsDelta = 0),
        ),
        ShortcutContextMenuItem(
            label = "Make taller",
            action = widget.resizeAction(columnsDelta = 0, rowsDelta = 1),
        ),
        ShortcutContextMenuItem(
            label = "Make shorter",
            action = widget.resizeAction(columnsDelta = 0, rowsDelta = -1),
        ),
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
