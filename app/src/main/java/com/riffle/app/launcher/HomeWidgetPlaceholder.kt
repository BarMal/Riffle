@file:Suppress("TooManyFunctions")

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.riffle.app.R
import com.riffle.app.launcher.glance.GlanceHomeWidget
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetStackEngine
import kotlin.math.roundToInt

@Composable
@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
internal fun HomeWidgetPlaceholder(
    widget: WidgetItem,
    isEditing: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    contextSurface: ShortcutContextSurface = ShortcutContextSurface.HOME,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    dragState: HomeItemDragState? = null,
    workspaceActions: HomeWorkspaceActions? = null,
    stackPresentation: WidgetStackPresentation = WidgetStackPresentation(),
) {
    val glance = widget.glance
    if (widget.isStack) {
        HomeWidgetStack(
            widget = widget,
            isEditing = isEditing,
            onAction = onAction,
            contextSurface = contextSurface,
            widgetViewFactory = widgetViewFactory,
            dragState = dragState,
            workspaceActions = workspaceActions,
            presentation = stackPresentation,
        )
    } else if (glance != null) {
        GlanceHomeWidget(
            widget = widget,
            kind = glance,
            isEditing = isEditing,
            contextSurface = contextSurface,
            dragState = dragState,
            workspaceActions = workspaceActions,
            onAction = onAction,
        )
    } else {
        HostedHomeWidget(
            widget = widget,
            isEditing = isEditing,
            onAction = onAction,
            contextSurface = contextSurface,
            widgetViewFactory = widgetViewFactory,
            dragState = dragState,
            workspaceActions = workspaceActions,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HostedHomeWidget(
    widget: WidgetItem,
    isEditing: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    contextSurface: ShortcutContextSurface,
    widgetViewFactory: HomeWidgetViewFactory,
    dragState: HomeItemDragState?,
    workspaceActions: HomeWorkspaceActions?,
) {
    val isContextMenuExpanded = remember(widget.id) { mutableStateOf(false) }

    // A platform widget keeps its own taps and scrolls: Home watches from above the view (never over it) and takes
    // the press only at the long press, which lifts it and opens its menu. An edit covers it with the gesture layer.
    val actionsLabel = stringResource(R.string.widget_actions_description, widget.label)
    val lift =
        if (!isEditing && dragState != null && workspaceActions != null) {
            Modifier
                .homeItemDrag(
                    enabled = true,
                    item = widget,
                    dragState = dragState,
                    actions = workspaceActions,
                    onStationaryLongPress = { isContextMenuExpanded.value = true },
                    onMenuDismiss = { isContextMenuExpanded.value = false },
                    passive = true,
                )
                .semantics {
                    onLongClick(label = actionsLabel) {
                        isContextMenuExpanded.value = true
                        true
                    }
                }
        } else {
            Modifier
        }
    HostedWidgetFrame(widget = widget, widgetViewFactory = widgetViewFactory, modifier = lift) {
        if (isEditing) {
            WidgetGestureLayer(
                widget = widget,
                dragState = dragState,
                workspaceActions = workspaceActions,
                onStationaryLongPress = { isContextMenuExpanded.value = true },
                onMenuDismiss = { isContextMenuExpanded.value = false },
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
                    text = rememberSettingsText(),
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = onAction,
        )
    }
}

/**
 * A platform widget in the launcher's glass frame: its host view, sized to the frame, or its name where it has none.
 * [overlay] draws inside the frame, over the widget (a lone widget's gestures, handles and menu). A stack draws one of
 * these for each of the widgets it holds.
 */
@Composable
internal fun HostedWidgetFrame(
    widget: WidgetItem,
    widgetViewFactory: HomeWidgetViewFactory,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
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

    // The widget's frame: Regular glass in the launcher's card shape, behind the hosted view.
    GlassSurface(
        modifier = Modifier.fillMaxSize().then(modifier),
        weight = GlassWeight.Regular,
        shape = LocalLauncherCardShape.current,
        tint = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
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
            overlay()
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BoxScope.WidgetGestureLayer(
    widget: WidgetItem,
    dragState: HomeItemDragState?,
    workspaceActions: HomeWorkspaceActions?,
    onStationaryLongPress: () -> Unit,
    onMenuDismiss: () -> Unit = {},
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
                    onMenuDismiss = onMenuDismiss,
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
internal fun BoxScope.WidgetEditHandles(
    widget: WidgetItem,
    grid: GridDimensions?,
    pageItems: List<LauncherItem>,
    contextSurface: ShortcutContextSurface,
    onAction: (LauncherShellAction) -> Unit,
) {
    RemoveShortcutButton(
        label = widget.label,
        onClick = { onAction(widget.removeActionFor(contextSurface)) },
    )
    val resizeHandleLabels = widget.resizeHandleLabels()
    if ("Make wider" in resizeHandleLabels) {
        WidgetResizeHandle(
            modifier = Modifier.align(Alignment.CenterEnd),
            label = "+",
            contentDescription = stringResource(R.string.widget_make_wider_description, widget.label),
            enabled = widget.canResize(columnsDelta = 1, rowsDelta = 0, grid = grid, pageItems = pageItems),
            onClick = { onAction(widget.resizeAction(columnsDelta = 1, rowsDelta = 0)) },
        )
        WidgetResizeHandle(
            modifier = Modifier.align(Alignment.CenterStart),
            label = "-",
            contentDescription = stringResource(R.string.widget_make_narrower_description, widget.label),
            enabled = widget.canResize(columnsDelta = -1, rowsDelta = 0, grid = grid, pageItems = pageItems),
            onClick = { onAction(widget.resizeAction(columnsDelta = -1, rowsDelta = 0)) },
        )
    }
    if ("Make taller" in resizeHandleLabels) {
        WidgetResizeHandle(
            modifier = Modifier.align(Alignment.BottomCenter),
            label = "+",
            contentDescription = stringResource(R.string.widget_make_taller_description, widget.label),
            enabled = widget.canResize(columnsDelta = 0, rowsDelta = 1, grid = grid, pageItems = pageItems),
            onClick = { onAction(widget.resizeAction(columnsDelta = 0, rowsDelta = 1)) },
        )
        WidgetResizeHandle(
            modifier = Modifier.align(Alignment.TopCenter),
            label = "-",
            contentDescription = stringResource(R.string.widget_make_shorter_description, widget.label),
            enabled = widget.canResize(columnsDelta = 0, rowsDelta = -1, grid = grid, pageItems = pageItems),
            onClick = { onAction(widget.resizeAction(columnsDelta = 0, rowsDelta = -1)) },
        )
    }
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
                // Resize controls must win hit testing over the full-widget move/menu layer.
                .zIndex(1f)
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
    contextSurface: ShortcutContextSurface = ShortcutContextSurface.HOME,
    text: SettingsText,
): List<ShortcutContextMenuItem> {
    return widget.stackMenuItems(contextSurface, text) +
        listOf(
            ShortcutContextMenuItem(
                label = text.text(R.string.menu_make_wider),
                action = widget.resizeAction(columnsDelta = 1, rowsDelta = 0),
                enabled = widget.canResize(columnsDelta = 1, rowsDelta = 0, grid = grid, pageItems = pageItems),
            ),
            ShortcutContextMenuItem(
                label = text.text(R.string.menu_make_narrower),
                action = widget.resizeAction(columnsDelta = -1, rowsDelta = 0),
                enabled = widget.canResize(columnsDelta = -1, rowsDelta = 0, grid = grid, pageItems = pageItems),
            ),
            ShortcutContextMenuItem(
                label = text.text(R.string.menu_make_taller),
                action = widget.resizeAction(columnsDelta = 0, rowsDelta = 1),
                enabled = widget.canResize(columnsDelta = 0, rowsDelta = 1, grid = grid, pageItems = pageItems),
            ),
            ShortcutContextMenuItem(
                label = text.text(R.string.menu_make_shorter),
                action = widget.resizeAction(columnsDelta = 0, rowsDelta = -1),
                enabled = widget.canResize(columnsDelta = 0, rowsDelta = -1, grid = grid, pageItems = pageItems),
            ),
            ShortcutContextMenuItem(
                label = contextSurface.widgetRemoveLabel(text),
                action = widget.removeActionFor(contextSurface),
            ),
        )
}

/**
 * The stack entries of a widget's menu (Home only: the Dock and its panel never hold a stack): "Edit stack" for a
 * stack, and "Add widget to stack" for any widget while the stack has room.
 */
private fun WidgetItem.stackMenuItems(
    surface: ShortcutContextSurface,
    text: SettingsText,
): List<ShortcutContextMenuItem> =
    if (surface != ShortcutContextSurface.HOME) {
        emptyList()
    } else {
        listOfNotNull(
            ShortcutContextMenuItem(
                label = text.text(R.string.menu_edit_stack),
                action = LauncherShellAction.OpenWidgetStackEditor(id),
            ).takeIf { isStack },
            ShortcutContextMenuItem(
                label = text.text(R.string.menu_add_widget_to_stack),
                action = LauncherShellAction.OpenWidgetPickerForStack(id),
                enabled = members.size < WidgetStackEngine.MAX_MEMBERS,
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

internal fun WidgetItem.resizeHandleLabels(): List<String> =
    buildList {
        if (resizeConstraints.supportsHorizontalResize) {
            add("Make wider")
            add("Make narrower")
        }
        if (resizeConstraints.supportsVerticalResize) {
            add("Make taller")
            add("Make shorter")
        }
    }
