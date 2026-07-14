package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
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
        val activeDragSession = state.activeDragSession
        val activeDragSource =
            activeDragSession?.takeIf { session -> session.originCell == state.cell }?.item
        val visibleItem = activeDragSource ?: state.previewItems.itemAt(cell = state.cell)

        HomeBackgroundContextMenu(
            haptics = actions.haptics,
            onAction = actions.onAction,
            modifier = Modifier.fillMaxSize(),
            onClick = actions.onBackgroundClick,
        )
        Box(
            modifier =
                Modifier
                    .width(state.cellSize)
                    .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (activeDragSession?.projectedCell == state.cell) {
                state.dragPlaceholderAtProjectedCell(activeDragSession)?.let { placeholder ->
                    HomeDragPlaceholder(
                        span = placeholder.span,
                        cellSize = state.cellSize,
                        cellSizePx = state.cellSizePx,
                        fillSpan = placeholder.fillSpan,
                        iconSizeDp = presentation.labelSettings.iconSizeDp,
                    )
                }
            }

            visibleItem?.let { item ->
                key(item.id.value) {
                    HomeGridItem(
                        item = item,
                        state =
                            HomeGridItemState(
                                cell = state.cell,
                                cellSize = state.cellSize,
                                cellSizePx = state.cellSizePx,
                                grid = state.page.grid,
                                pageItems = state.page.items,
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

internal data class HomeGridCellState(
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
                dragState =
                    HomeItemDragState(
                        cell = state.cell,
                        cellSizePx = state.cellSizePx,
                        grid = state.grid,
                        pageItems = state.pageItems,
                    ),
                isEditing = state.isEditing,
                modifier = state.dragSourceModifier,
                presentation =
                    HomeShortcutPresentation(
                        notificationCount = presentation.notificationGroupsByApp.notificationCountFor(item),
                        appShortcuts = presentation.appShortcutsByApp[item.appIdentity].orEmpty(),
                        labelSettings = presentation.labelSettings,
                        reducedMotion = presentation.reducedMotion,
                    ),
                appIconLoader = appIconLoader,
                actions = actions,
            )

        is FolderItem ->
            Box(modifier = state.dragSourceModifier.fillMaxSize()) {
                HomeFolder(
                    folder = item,
                    dragState =
                        HomeItemDragState(
                            cell = state.cell,
                            cellSizePx = state.cellSizePx,
                            grid = state.grid,
                            pageItems = state.pageItems,
                        ),
                    isEditing = state.isEditing,
                    presentation =
                        HomeFolderPresentation(
                            notificationCount = presentation.notificationGroupsByApp.notificationCountFor(item),
                            labelSettings = presentation.labelSettings,
                            reducedMotion = presentation.reducedMotion,
                        ),
                    appIconLoader = appIconLoader,
                    actions = actions,
                )
            }

        is WidgetItem -> {
            val span = item.placement?.span ?: GridSpan()

            Box(
                modifier =
                    state.dragSourceModifier
                        .requiredWidth(state.cellSize * span.columns)
                        .requiredHeight(state.cellSize * span.rows)
                        .graphicsLayer {
                            translationX = ((span.columns - 1) * state.cellSizePx) / 2f
                            translationY = ((span.rows - 1) * state.cellSizePx) / 2f
                        },
            ) {
                HomeWidgetPlaceholder(
                    widget = item,
                    isEditing = state.isEditing,
                    widgetViewFactory = presentation.widgetViewFactory,
                    dragState =
                        HomeItemDragState(
                            cell = state.cell,
                            cellSizePx = state.cellSizePx,
                            grid = state.grid,
                            pageItems = state.pageItems,
                        ),
                    workspaceActions = actions,
                    onAction = actions.onAction,
                )
            }
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
    val pressInteractionSource = remember { MutableInteractionSource() }
    val pressMotionPolicy = homeIconPressMotionPolicy(presentation.reducedMotion)
    val pressIndication =
        if (pressMotionPolicy.usesDefaultPressIndication) LocalIndication.current else null
    val pressHandlers =
        homeShortcutPressHandlers(
            isEditing = isEditing,
            onShowContextMenu = { isContextMenuExpanded.value = true },
            onLaunch = { actions.onAction(shortcut.launchAction()) },
        )
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
                    .homeIconPressMotion(
                        interactionSource = pressInteractionSource,
                        policy = pressMotionPolicy,
                    )
                    .semantics { homeIconPressMotionPolicy = pressMotionPolicy }
                    .combinedClickable(
                        enabled = true,
                        interactionSource = pressInteractionSource,
                        indication = pressIndication,
                        onClick = pressHandlers.onTap,
                        onLongClickLabel = longClickLabel,
                    )
                    .homeItemDrag(
                        enabled = true,
                        item = shortcut,
                        dragState = dragState,
                        actions = actions,
                        onStationaryLongPress = pressHandlers.onLongPress,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(presentation.labelSettings.iconSizeDp.dp)) {
                LauncherAppIcon(
                    identity = shortcut.appIdentity,
                    label = shortcut.label,
                    iconLoader = appIconLoader,
                    modifier = Modifier.size(presentation.labelSettings.iconSizeDp.dp),
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
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = actions.onAction,
        )
    }
}

internal data class HomeGridState(
    val isEditing: Boolean,
    val pageCount: Int,
    val selectedPageIndex: Int,
    val dragSession: HomeDragSession?,
)

internal data class HomeGridItemState(
    val cell: GridCell,
    val cellSize: Dp,
    val cellSizePx: Float,
    val grid: GridDimensions,
    val pageItems: List<LauncherItem>,
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
    val reducedMotion: Boolean,
)

private const val DRAGGED_GRID_ITEM_Z_INDEX = 1f
private const val DRAGGED_GRID_ITEM_ELEVATION = 12f
