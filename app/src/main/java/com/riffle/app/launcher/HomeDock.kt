@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
internal fun Dock(
    dock: DockModel,
    isEditing: Boolean,
    notificationGroupsByApp: List<AppNotificationGroup>,
    appShortcutsByApp: AppShortcutsByApp,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    interactions: DockInteractions,
) {
    val presentation = DockPresentation(notificationGroupsByApp, appShortcutsByApp, widgetViewFactory, interactions)

    BoxWithConstraints(
        modifier = Modifier.dockShelfGestureInput(interactions),
        contentAlignment = Alignment.Center,
    ) {
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock = dock,
                isEditing = isEditing,
                availableWidthDp = maxWidth.value.toInt(),
            ) ?: return@BoxWithConstraints
        HomeBackgroundContextMenu(
            haptics = interactions.haptics,
            onAction = interactions.onAction,
            modifier = Modifier.matchParentSize(),
        )
        DockSurfaceRow(
            modifier =
                Modifier
                    .dockShelfPolicies(interactions),
            dock = dock,
            surfaceMetrics = surfaceMetrics,
            isEditing = isEditing,
            presentation = presentation,
            appIconLoader = appIconLoader,
        )
    }
}

@Suppress("LongMethod")
@Composable
internal fun DockSlotsRow(
    dock: DockModel,
    renderedSlotCount: Int,
    contentViewportWidthDp: Int,
    slotMetrics: DockSlotRenderMetrics,
    isEditing: Boolean,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val scrollState = rememberScrollState()
    val dragState = remember { mutableStateOf<DockDragState?>(null) }
    val moveToHomeItemId = remember { mutableStateOf<LauncherItemId?>(null) }
    val homeLayout = presentation.interactions.homeLayout
    val slotPresentation =
        presentation.copy(
            interactions =
                presentation.interactions.copy(
                    onAction = { action ->
                        if (action is LauncherShellAction.MoveDockItemToHome && homeLayout != null) {
                            moveToHomeItemId.value = action.itemId
                        } else {
                            presentation.interactions.onAction(action)
                        }
                    },
                ),
        )
    val previewItems = dock.items.dockItemsForPreview(dragState.value)
    val overflowAffordance =
        DockOverflowAffordance(
            scrollOffsetPx = scrollState.value,
            maxScrollOffsetPx = scrollState.maxValue,
        )
    val fadeColor = dockSurfaceColor(dock)

    Box(
        modifier =
            Modifier
                .width(contentViewportWidthDp.dp)
                .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .width(dockSlotContentWidthDp(renderedSlotCount, slotMetrics).dp)
                    .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(slotMetrics.itemSpacingDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(renderedSlotCount) { index ->
                val previewItem = previewItems.getOrNull(index)
                // A preview reflow moves items between visual slots. Key actual items by their
                // stable launcher ID so the dragged node keeps its pointer-input coroutine until
                // the gesture commits or cancels.
                key(previewItem?.id ?: "dock-placeholder:$index") {
                    DockSlot(
                        modifier = Modifier.requiredSize(slotMetrics.iconSizeDp.dp),
                        state =
                            DockSlotState(
                                item = dockSlotItemState(previewItem),
                                shortcutIndex = dock.items.indexOfFirst { item -> item.id == previewItem?.id },
                                visualIndex = index,
                                shortcutCount = dock.items.size,
                                iconSizeDp = slotMetrics.iconSizeDp,
                                itemSpacingDp = slotMetrics.itemSpacingDp,
                                isEditing = isEditing,
                            ),
                        presentation = slotPresentation,
                        appIconLoader = appIconLoader,
                        dragState = dragState.value,
                        dragViewport = DockDragViewport(scrollState, contentViewportWidthDp),
                        onDragStateChanged = { dragState.value = it },
                    )
                }
            }
        }

        if (overflowAffordance.showStart) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(DOCK_OVERFLOW_FADE_WIDTH_DP.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(fadeColor, fadeColor.copy(alpha = 0f)),
                            ),
                        ),
            )
        }
        if (overflowAffordance.showEnd) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(DOCK_OVERFLOW_FADE_WIDTH_DP.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(fadeColor.copy(alpha = 0f), fadeColor),
                            ),
                        ),
            )
        }
    }

    moveToHomeItemId.value?.let { itemId ->
        homeLayout?.let { layout ->
            DockToHomeDestinationDialog(
                itemId = itemId,
                layout = layout,
                onDismissRequest = { moveToHomeItemId.value = null },
                onDestinationSelected = { pageId, cell ->
                    presentation.interactions.onAction(dockMoveToHomeAction(itemId, pageId, cell))
                    moveToHomeItemId.value = null
                },
            )
        }
    }
}

private const val DOCK_MAX_WIDTH_DP = 560
internal const val DOCK_VERTICAL_CHROME_DP = 32
internal const val DOCK_HORIZONTAL_PADDING_DP = 14
internal const val DOCK_VERTICAL_PADDING_DP = 10
private const val DOCK_OVERFLOW_FADE_WIDTH_DP = 20
private const val DOCK_EDGE_AUTO_SCROLL_ZONE_DP = 28
private const val DOCK_EDGE_AUTO_SCROLL_MAX_PX_PER_EVENT = 24f
private const val DOCK_DRAG_SLOT_HYSTERESIS = 0.15f
private const val DOCK_EDGE_AUTO_SCROLL_FRAME_DELAY_MILLIS = 16L

internal fun dockHeightDp(iconSizeDp: Int): Int = iconSizeDp + DOCK_VERTICAL_CHROME_DP

internal fun dockContentViewportWidthDp(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    availableDockWidthDp: Int = DOCK_MAX_WIDTH_DP,
): Int {
    if (slotCount <= 0) {
        return 0
    }
    val contentWidth = (slotCount * iconSizeDp) + ((slotCount - 1) * itemSpacingDp)
    val maxDockWidth = min(availableDockWidthDp, DOCK_MAX_WIDTH_DP)
    val maxContentWidth = (maxDockWidth - (DOCK_HORIZONTAL_PADDING_DP * 2)).coerceAtLeast(0)
    return min(contentWidth, maxContentWidth)
}

internal fun dockContainerWidthDp(
    availableWidthDp: Int,
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    backgroundSizing: DockBackgroundSizing,
): Int {
    val maxDockWidth = min(availableWidthDp, DOCK_MAX_WIDTH_DP).coerceAtLeast(0)
    if (backgroundSizing == DockBackgroundSizing.FIXED) {
        return maxDockWidth
    }
    val contentViewportWidth =
        dockContentViewportWidthDp(
            slotCount = slotCount,
            iconSizeDp = iconSizeDp,
            itemSpacingDp = itemSpacingDp,
            availableDockWidthDp = maxDockWidth,
        )
    return min(maxDockWidth, contentViewportWidth + (DOCK_HORIZONTAL_PADDING_DP * 2))
}

internal fun dockRenderedSlotCount(
    capacity: Int,
    itemCount: Int,
    isEditing: Boolean,
): Int =
    when {
        capacity <= 0 -> 0
        itemCount <= 0 && !isEditing -> 0
        isEditing -> capacity.coerceAtLeast(itemCount)
        else -> capacity
    }

@Suppress("UnusedParameter")
internal fun dockBackgroundVisible(
    capacity: Int,
    itemCount: Int,
    isEditing: Boolean,
    backgroundSizing: DockBackgroundSizing,
): Boolean =
    when {
        backgroundSizing == DockBackgroundSizing.FIXED -> true
        capacity <= 0 -> false
        // An empty app catalog can be a transient recovery state. Keep an enabled dynamic dock
        // visible so it does not disappear until app discovery completes.
        else -> true
    }

internal data class DockOverflowAffordance(
    val showStart: Boolean,
    val showEnd: Boolean,
) {
    constructor(
        scrollOffsetPx: Int,
        maxScrollOffsetPx: Int,
    ) : this(
        showStart = maxScrollOffsetPx > 0 && scrollOffsetPx > 0,
        showEnd = maxScrollOffsetPx > 0 && scrollOffsetPx < maxScrollOffsetPx,
    )
}

internal data class DockPresentation(
    val notificationGroupsByApp: List<AppNotificationGroup>,
    val appShortcutsByApp: AppShortcutsByApp,
    val widgetViewFactory: HomeWidgetViewFactory,
    val interactions: DockInteractions,
)

internal data class DockInteractions(
    val haptics: LauncherHaptics = NoopLauncherHaptics,
    val onFolderOpen: (FolderItem) -> Unit = {},
    val isShelfExpanded: Boolean = false,
    val onShelfExpandedChange: ((Boolean) -> Unit)? = null,
    val reducedMotion: Boolean = false,
    val homeInsetPolicy: HomeInsetPolicy = HomeInsetPolicy(),
    val homeLayout: HomeLayout? = null,
    val onAction: (LauncherShellAction) -> Unit,
)

internal fun dockMoveToHomeAction(
    itemId: LauncherItemId,
    pageId: LauncherPageId,
    cell: GridCell,
): LauncherShellAction.MoveDockItemToHome =
    LauncherShellAction.MoveDockItemToHome(
        itemId = itemId,
        pageId = pageId,
        cell = cell,
    )

@Composable
private fun DockToHomeDestinationDialog(
    itemId: LauncherItemId,
    layout: HomeLayout,
    onDismissRequest: () -> Unit,
    onDestinationSelected: (LauncherPageId, GridCell) -> Unit,
) {
    val pages = layout.pages.filter { page -> page.type !is LauncherPageType.Generated }
    if (pages.isEmpty()) {
        return
    }
    val selectedPageId =
        remember(itemId) {
            mutableStateOf(
                layout.selectedPageId.takeIf { it in pages.map(LauncherPage::id) } ?: pages.first().id,
            )
        }
    val selectedPage = pages.first { page -> page.id == selectedPageId.value }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Move to Home") },
        text = {
            Column {
                Text("Choose page")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    pages.forEachIndexed { index, page ->
                        TextButton(onClick = { selectedPageId.value = page.id }) {
                            Text(if (page.id == selectedPage.id) "Page ${index + 1}" else "${index + 1}")
                        }
                    }
                }
                Text("Choose cell")
                repeat(selectedPage.grid.rows) { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(selectedPage.grid.columns) { column ->
                            val cell = GridCell(column = column, row = row)
                            TextButton(onClick = { onDestinationSelected(selectedPage.id, cell) }) {
                                Text("${column + 1},${row + 1}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Cancel") } },
    )
}

private data class DockSlotState(
    val item: DockSlotItemState?,
    val shortcutIndex: Int,
    val visualIndex: Int,
    val shortcutCount: Int,
    val iconSizeDp: Int,
    val itemSpacingDp: Int,
    val isEditing: Boolean,
)

internal data class DockDragState(
    val itemId: LauncherItemId,
    val originIndex: Int,
    val targetIndex: Int,
)

private data class DockDragViewport(
    val scrollState: androidx.compose.foundation.ScrollState,
    val contentViewportWidthDp: Int,
)

internal fun dockItemTestTag(itemId: LauncherItemId): String = "dock-item:${itemId.value}"

internal fun DockSlotItemState.isDirectDockDragEligible(): Boolean {
    return this is DockSlotItemState.Shortcut || this is DockSlotItemState.Folder
}

internal fun List<LauncherItem>.dockItemsForPreview(drag: DockDragState?): List<LauncherItem> {
    if (drag == null || drag.itemId !in map { it.id }) return this
    val sourceIndex = indexOfFirst { it.id == drag.itemId }
    val targetIndex = drag.targetIndex.coerceIn(0, lastIndex)
    return toMutableList().apply { add(targetIndex, removeAt(sourceIndex)) }
}

/**
 * Returns a bounded content-scroll delta while a drag is inside either overflow edge zone.
 * Callers apply the result to [androidx.compose.foundation.ScrollState.dispatchRawDelta], which
 * additionally clamps it to the actual scroll range.
 */
internal fun dockEdgeAutoScrollDelta(
    pointerX: Float,
    viewportWidthPx: Float,
    edgeZonePx: Float,
): Float {
    if (viewportWidthPx <= 0f || edgeZonePx <= 0f) return 0f

    val edgePressure =
        when {
            pointerX < edgeZonePx -> (pointerX - edgeZonePx) / edgeZonePx
            pointerX > viewportWidthPx - edgeZonePx -> (pointerX - (viewportWidthPx - edgeZonePx)) / edgeZonePx
            else -> 0f
        }
    return (edgePressure * DOCK_EDGE_AUTO_SCROLL_MAX_PX_PER_EVENT)
        .coerceIn(-DOCK_EDGE_AUTO_SCROLL_MAX_PX_PER_EVENT, DOCK_EDGE_AUTO_SCROLL_MAX_PX_PER_EVENT)
}

/** Keeps a candidate in its current slot until the drag clears a hysteresis-adjusted boundary. */
internal fun dockDragTargetIndex(
    originIndex: Int,
    currentTargetIndex: Int,
    draggedSlotDeltaPx: Float,
    slotWidthPx: Float,
    itemCount: Int,
): Int {
    if (itemCount <= 0 || slotWidthPx <= 0f) return originIndex

    val draggedSlots = draggedSlotDeltaPx / slotWidthPx
    var targetIndex = currentTargetIndex.coerceIn(0, itemCount - 1)
    while (
        targetIndex < itemCount - 1 &&
        draggedSlots > (targetIndex - originIndex) + 0.5f + DOCK_DRAG_SLOT_HYSTERESIS
    ) {
        targetIndex += 1
    }
    while (
        targetIndex > 0 &&
        draggedSlots < (targetIndex - originIndex) - 0.5f - DOCK_DRAG_SLOT_HYSTERESIS
    ) {
        targetIndex -= 1
    }
    return targetIndex
}

private data class DockShortcutState(
    val iconSizeDp: Int,
    val shortcutIndex: Int,
    val shortcutCount: Int,
    val isEditing: Boolean,
    val notificationCount: Int,
    val appShortcuts: List<AppShortcut>,
)

@Composable
private fun DockSlot(
    modifier: Modifier,
    state: DockSlotState,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
    dragState: DockDragState?,
    dragViewport: DockDragViewport,
    onDragStateChanged: (DockDragState?) -> Unit,
) {
    val editingSlotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)

    Box(
        modifier =
            modifier
                .clip(LocalLauncherCardShape.current)
                .then(if (state.isEditing) Modifier.background(editingSlotColor) else Modifier)
                .then(state.item?.let { item -> Modifier.testTag(dockItemTestTag(item.id)) } ?: Modifier)
                .dockItemDrag(
                    state = state,
                    slotWidthDp = state.iconSizeDp,
                    itemSpacingDp = state.itemSpacingDp,
                    dragViewport = dragViewport,
                    onDragStateChanged = onDragStateChanged,
                    onAction = presentation.interactions.onAction,
                )
                .graphicsLayer {
                    if (dragState?.itemId == state.item?.id) {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        shadowElevation = 12f
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        when (val item = state.item) {
            null -> Unit
            is DockSlotItemState.Shortcut ->
                DockShortcut(
                    shortcut = item.shortcut,
                    state =
                        DockShortcutState(
                            iconSizeDp = state.iconSizeDp,
                            shortcutIndex = state.shortcutIndex,
                            shortcutCount = state.shortcutCount,
                            isEditing = state.isEditing,
                            notificationCount =
                                presentation.notificationGroupsByApp.notificationCountFor(
                                    item.shortcut,
                                ),
                            appShortcuts = presentation.appShortcutsByApp[item.shortcut.appIdentity].orEmpty(),
                        ),
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                )
            is DockSlotItemState.Folder ->
                DockFolder(
                    folder = item.folder,
                    state = state,
                    presentation = presentation,
                )
            is DockSlotItemState.Widget ->
                DockWidgetSlot(
                    widget = item.widget,
                    iconSizeDp = state.iconSizeDp,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                    presentation = presentation,
                )
            is DockSlotItemState.Placeholder ->
                DockItemPlaceholder(
                    item = item,
                    iconSizeDp = state.iconSizeDp,
                )
        }
    }
}

private fun Modifier.dockItemDrag(
    state: DockSlotState,
    slotWidthDp: Int,
    itemSpacingDp: Int,
    dragViewport: DockDragViewport,
    onDragStateChanged: (DockDragState?) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
): Modifier {
    return state.item
        ?.takeIf { item -> state.isEditing && state.shortcutCount > 1 && item.isDirectDockDragEligible() }
        ?.id
        ?.let { itemId ->
            pointerInput(itemId, state.shortcutIndex, state.shortcutCount, slotWidthDp, itemSpacingDp) {
                coroutineScope {
                    var horizontalDrag = 0f
                    var targetIndex = state.shortcutIndex
                    var initialScrollOffset = 0
                    var edgeAutoScrollDelta = 0f
                    var autoScrollJob: Job? = null

                    fun updateCandidate(slotWidthPx: Float) {
                        targetIndex =
                            dockDragTargetIndex(
                                originIndex = state.shortcutIndex,
                                currentTargetIndex = targetIndex,
                                draggedSlotDeltaPx =
                                    horizontalDrag + dragViewport.scrollState.value - initialScrollOffset,
                                slotWidthPx = slotWidthPx,
                                itemCount = state.shortcutCount,
                            )
                        onDragStateChanged(DockDragState(itemId, state.shortcutIndex, targetIndex))
                    }

                    fun updateEdgeAutoScroll(slotWidthPx: Float) {
                        if (edgeAutoScrollDelta == 0f) {
                            autoScrollJob?.cancel()
                            return
                        }
                        if (autoScrollJob?.isActive == true) return
                        autoScrollJob =
                            launch {
                                while (isActive && edgeAutoScrollDelta != 0f) {
                                    val scrollOffset = dragViewport.scrollState.value
                                    dragViewport.scrollState.dispatchRawDelta(edgeAutoScrollDelta)
                                    if (dragViewport.scrollState.value == scrollOffset) break
                                    updateCandidate(slotWidthPx)
                                    delay(DOCK_EDGE_AUTO_SCROLL_FRAME_DELAY_MILLIS)
                                }
                            }
                    }

                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            horizontalDrag = 0f
                            targetIndex = state.shortcutIndex
                            initialScrollOffset = dragViewport.scrollState.value
                            edgeAutoScrollDelta = 0f
                            onDragStateChanged(DockDragState(itemId, state.shortcutIndex, state.shortcutIndex))
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            horizontalDrag += amount.x
                            val slotWidthPx = density * (slotWidthDp + itemSpacingDp)
                            val viewportWidthPx = density * dragViewport.contentViewportWidthDp
                            val pointerX =
                                (state.visualIndex * slotWidthPx) - dragViewport.scrollState.value + change.position.x
                            edgeAutoScrollDelta =
                                dockEdgeAutoScrollDelta(
                                    pointerX = pointerX,
                                    viewportWidthPx = viewportWidthPx,
                                    edgeZonePx = density * DOCK_EDGE_AUTO_SCROLL_ZONE_DP,
                                )
                            updateCandidate(slotWidthPx)
                            updateEdgeAutoScroll(slotWidthPx)
                        },
                        onDragEnd = {
                            autoScrollJob?.cancel()
                            if (targetIndex != state.shortcutIndex) {
                                onAction(LauncherShellAction.MoveDockShortcutToIndex(itemId, targetIndex))
                            }
                            onDragStateChanged(null)
                        },
                        onDragCancel = {
                            autoScrollJob?.cancel()
                            onDragStateChanged(null)
                        },
                    )
                }
            }
        } ?: this
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DockFolder(
    folder: FolderItem,
    state: DockSlotState,
    presentation: DockPresentation,
) {
    val isContextMenuExpanded = remember(folder.id) { mutableStateOf(false) }
    val modifier =
        if (state.isEditing) {
            Modifier.clickable(onClick = { isContextMenuExpanded.value = true })
        } else {
            Modifier.clickable(onClick = { presentation.interactions.onFolderOpen(folder) })
        }

    Box(modifier = Modifier.requiredSize(state.iconSizeDp.dp)) {
        DockItemPlaceholder(
            item =
                DockSlotItemState.Placeholder(
                    id = folder.id,
                    label = folder.label,
                    kind = DockSlotPlaceholderKind.FOLDER,
                ),
            iconSizeDp = state.iconSizeDp,
            modifier = modifier,
        )
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                dockFolderContextMenuItems(
                    folder = folder,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = presentation.interactions.onAction,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DockShortcut(
    shortcut: AppShortcutItem,
    state: DockShortcutState,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val isContextMenuExpanded = remember(shortcut.id) { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .requiredSize(state.iconSizeDp.dp),
    ) {
        LauncherAppIcon(
            identity = shortcut.appIdentity,
            label = shortcut.label,
            iconLoader = appIconLoader,
            modifier =
                Modifier.requiredSize(state.iconSizeDp.dp).then(
                    if (state.isEditing) {
                        Modifier.clickable(onClick = { isContextMenuExpanded.value = true })
                    } else {
                        Modifier.combinedClickable(
                            onClick = {
                                presentation.interactions.onAction(shortcut.launchAction())
                            },
                            onLongClick = {
                                presentation.interactions.haptics.longPress()
                                isContextMenuExpanded.value = true
                            },
                            onLongClickLabel = "Show ${shortcut.label} actions",
                        )
                    },
                ),
        )

        if (!state.isEditing) {
            NotificationCountBadge(
                count = state.notificationCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                dockShortcutContextMenuItems(
                    shortcut = shortcut,
                    appShortcuts = state.appShortcuts,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = presentation.interactions.onAction,
        )
    }
}

internal fun dockShortcutContextMenuItems(
    shortcut: AppShortcutItem,
    appShortcuts: List<AppShortcut> = emptyList(),
    isEditing: Boolean = false,
    shortcutIndex: Int = 0,
    shortcutCount: Int = 1,
): List<ShortcutContextMenuItem> {
    val editItems =
        if (isEditing) {
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.LEFT,
                        ),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.RIGHT,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
                ShortcutContextMenuItem(
                    label = "Move to start",
                    action = LauncherShellAction.MoveDockShortcutToIndex(shortcut.id, targetIndex = 0),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move to end",
                    action =
                        LauncherShellAction.MoveDockShortcutToIndex(
                            shortcut.id,
                            targetIndex = shortcutCount - 1,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
            )
        } else {
            emptyList()
        }

    return editItems +
        shortcutContextMenuItems(
            shortcut = shortcut,
            surface = ShortcutContextSurface.DOCK,
            appShortcuts = appShortcuts,
        )
}
