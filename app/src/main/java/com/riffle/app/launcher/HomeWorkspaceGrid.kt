package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem

@Composable
internal fun WorkspaceGrid(
    page: LauncherPage,
    gridState: HomeGridState,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val metrics = HomeGridLayoutMetrics()
        val cellSizePx =
            metrics.cellSizePx(
                grid = page.grid,
                maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() },
                maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() },
            )
        val cellSize = with(LocalDensity.current) { cellSizePx.toDp() }
        val previewItems = page.itemsForDragPreview(gridState.dragSession)
        val activeDragSession =
            gridState.dragSession
                ?.takeIf { session -> page.items.any { item -> item.id == session.item.id } }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(page.grid.rows) { row ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(page.grid.columns) { column ->
                        HomeGridCell(
                            state =
                                HomeGridCellState(
                                    cell = GridCell(column = column, row = row),
                                    cellSize = cellSize,
                                    cellSizePx = cellSizePx,
                                    page = page,
                                    previewItems = previewItems,
                                    activeDragSession = activeDragSession,
                                    gridState = gridState,
                                ),
                            presentation = presentation,
                            appIconLoader = appIconLoader,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.HomeGridCell(
    state: HomeGridCellState,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(state.cellSize)
                    .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            val activeDragSession = state.activeDragSession
            val activeDragSource =
                activeDragSession?.takeIf { session -> session.originCell == state.cell }?.item

            if (activeDragSession?.projectedCell == state.cell) {
                HomeDragPlaceholder()
            }
            val visibleItem = activeDragSource ?: state.previewItems.itemAt(cell = state.cell)

            visibleItem?.let { item ->
                key(item.id.value) {
                    HomeGridItem(
                        item = item,
                        state =
                            HomeGridItemState(
                                cell = state.cell,
                                cellSizePx = state.cellSizePx,
                                grid = state.page.grid,
                                isEditing = state.gridState.isEditing,
                                activeDragSession = activeDragSession?.takeIf { session -> session.item.id == item.id },
                            ),
                        presentation = presentation,
                        appIconLoader = appIconLoader,
                        actions = actions,
                    )
                }
            }
        }
    }
}

private data class HomeGridCellState(
    val cell: GridCell,
    val cellSize: Dp,
    val cellSizePx: Float,
    val page: LauncherPage,
    val previewItems: List<LauncherItem>,
    val activeDragSession: HomeDragSession?,
    val gridState: HomeGridState,
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomeGridItem(
    item: LauncherItem,
    state: HomeGridItemState,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    when (item) {
        is AppShortcutItem ->
            HomeShortcut(
                shortcut = item,
                dragState = HomeItemDragState(cell = state.cell, cellSizePx = state.cellSizePx, grid = state.grid),
                isEditing = state.isEditing,
                modifier = state.dragSourceModifier,
                presentation =
                    HomeShortcutPresentation(
                        notificationCount = presentation.notificationCountsByPackage.notificationCountFor(item),
                        appShortcuts = presentation.appShortcutsByApp[item.appIdentity].orEmpty(),
                        labelSettings = presentation.labelSettings,
                    ),
                appIconLoader = appIconLoader,
                actions = actions,
            )

        is FolderItem ->
            Box(modifier = state.dragSourceModifier.fillMaxSize()) {
                HomeFolder(
                    folder = item,
                    dragState = HomeItemDragState(cell = state.cell, cellSizePx = state.cellSizePx, grid = state.grid),
                    isEditing = state.isEditing,
                    notificationCount = presentation.notificationCountsByPackage.notificationCountFor(item),
                    labelSettings = presentation.labelSettings,
                    appIconLoader = appIconLoader,
                    actions = actions,
                )
            }

        is WidgetItem ->
            Box(modifier = state.dragSourceModifier.fillMaxSize()) {
                HomeWidgetPlaceholder(
                    widget = item,
                    isEditing = state.isEditing,
                    widgetViewFactory = presentation.widgetViewFactory,
                    haptics = actions.haptics,
                    onAction = actions.onAction,
                )
            }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomeShortcut(
    shortcut: AppShortcutItem,
    dragState: HomeItemDragState,
    isEditing: Boolean,
    modifier: Modifier,
    presentation: HomeShortcutPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    val metrics = HomeGridLayoutMetrics()
    val isContextMenuExpanded = remember(shortcut.id) { mutableStateOf(false) }
    val longClickLabel =
        if (isEditing) {
            "Show ${shortcut.label} actions"
        } else {
            "Edit ${shortcut.label}"
        }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .heightIn(min = metrics.homeItemContentHeightDp(presentation.labelSettings).dp)
                    .homeItemDrag(
                        enabled = isEditing,
                        item = shortcut,
                        dragState = dragState,
                        actions = actions,
                    )
                    .combinedClickable(
                        enabled = true,
                        onClick = {
                            if (isEditing) {
                                isContextMenuExpanded.value = true
                            } else {
                                actions.onAction(shortcut.launchAction())
                            }
                        },
                        onLongClick = {
                            actions.haptics.longPress()
                            if (isEditing) {
                                isContextMenuExpanded.value = true
                            } else {
                                actions.onAction(LauncherShellAction.EnterHomeEditMode)
                            }
                        },
                        onLongClickLabel = longClickLabel,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(HOME_ICON_SIZE_DP.dp)) {
                LauncherAppIcon(
                    identity = shortcut.appIdentity,
                    label = shortcut.label,
                    iconLoader = appIconLoader,
                    modifier = Modifier.size(HOME_ICON_SIZE_DP.dp),
                )
                if (!isEditing) {
                    NotificationCountBadge(
                        count = presentation.notificationCount,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            WallpaperReadableLabel(
                text = shortcut.label,
                settings = presentation.labelSettings,
            )
        }
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                shortcutContextMenuItems(
                    shortcut = shortcut,
                    surface = ShortcutContextSurface.HOME,
                    appShortcuts = presentation.appShortcuts,
                    includeEditHome = !isEditing,
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = actions.onAction,
        )
    }
}

internal data class HomeGridState(
    val isEditing: Boolean,
    val dragSession: HomeDragSession?,
    val pageDragOffsetPx: Float,
)

internal data class HomeGridItemState(
    val cell: GridCell,
    val cellSizePx: Float,
    val grid: GridDimensions,
    val isEditing: Boolean,
    val activeDragSession: HomeDragSession?,
) {
    val dragSourceModifier: Modifier =
        activeDragSession
            ?.let { session ->
                Modifier
                    .zIndex(DRAGGED_GRID_ITEM_Z_INDEX)
                    .graphicsLayer {
                        translationX = session.dragOffsetX
                        translationY = session.dragOffsetY
                        shadowElevation = DRAGGED_GRID_ITEM_ELEVATION
                    }
            }
            ?: Modifier
}

private data class HomeShortcutPresentation(
    val notificationCount: Int,
    val appShortcuts: List<AppShortcut>,
    val labelSettings: HomeLabelSettings,
)

private fun LauncherPage.itemsForDragPreview(session: HomeDragSession?): List<LauncherItem> =
    session
        ?.takeIf { dragSession -> items.any { item -> item.id == dragSession.item.id } }
        ?.let { dragSession ->
            val originIndex = grid.indexOf(dragSession.originCell)
            val targetIndex = grid.indexOf(dragSession.projectedCell)

            when {
                originIndex == null || targetIndex == null -> items
                originIndex == targetIndex -> items.filterNot { item -> item.id == dragSession.item.id }
                else -> items.shiftedForDrag(originIndex = originIndex, targetIndex = targetIndex, grid = grid)
            }
        }
        ?: items

private fun List<LauncherItem>.shiftedForDrag(
    originIndex: Int,
    targetIndex: Int,
    grid: GridDimensions,
): List<LauncherItem> {
    val itemsByCellIndex =
        mapNotNull { item ->
            item.placement?.cell
                ?.let(grid::indexOf)
                ?.let { index -> index to item }
        }
            .toMap()
            .toMutableMap()
    itemsByCellIndex.remove(originIndex)

    when {
        targetIndex > originIndex ->
            for (index in originIndex until targetIndex) {
                itemsByCellIndex.moveIndex(from = index + 1, to = index)
            }

        else ->
            for (index in originIndex downTo targetIndex + 1) {
                itemsByCellIndex.moveIndex(from = index - 1, to = index)
            }
    }

    return itemsByCellIndex
        .mapNotNull { (index, item) ->
            grid.cellAt(index)?.let { cell -> item.withPreviewCell(cell) }
        }
}

private fun MutableMap<Int, LauncherItem>.moveIndex(
    from: Int,
    to: Int,
) {
    val item = remove(from)

    if (item == null) {
        remove(to)
    } else {
        this[to] = item
    }
}

private fun LauncherItem.withPreviewCell(cell: GridCell): LauncherItem? =
    placement?.copy(cell = cell)?.let { previewPlacement ->
        when (this) {
            is AppShortcutItem -> copy(placement = previewPlacement)
            is FolderItem -> copy(placement = previewPlacement)
            is WidgetItem -> copy(placement = previewPlacement)
        }
    }

private fun GridDimensions.indexOf(cell: GridCell): Int? =
    cell
        .takeIf { checkedCell ->
            checkedCell.column in 0 until columns &&
                checkedCell.row in 0 until rows
        }
        ?.let { checkedCell -> checkedCell.row * columns + checkedCell.column }

private fun GridDimensions.cellAt(index: Int): GridCell? =
    index
        .takeIf { checkedIndex -> checkedIndex in 0 until (columns * rows) }
        ?.let { checkedIndex ->
            GridCell(
                column = checkedIndex % columns,
                row = checkedIndex / columns,
            )
        }

private const val DRAGGED_GRID_ITEM_Z_INDEX = 1f
private const val DRAGGED_GRID_ITEM_ELEVATION = 12f
