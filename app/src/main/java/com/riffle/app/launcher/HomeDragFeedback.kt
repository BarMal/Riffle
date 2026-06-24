package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.WidgetItem
import kotlin.math.roundToInt

@Composable
internal fun HomeDragPlaceholder() {
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier =
            Modifier
                .size(HOME_ICON_SIZE_DP.dp)
                .drawBehind {
                    drawRoundRect(
                        color = outlineColor,
                        style =
                            Stroke(
                                width = DRAG_PLACEHOLDER_STROKE_WIDTH_DP.dp.toPx(),
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        intervals =
                                            floatArrayOf(
                                                DRAG_PLACEHOLDER_DASH_DP.dp.toPx(),
                                                DRAG_PLACEHOLDER_GAP_DP.dp.toPx(),
                                            ),
                                    ),
                            ),
                    )
                },
    )
}

@Composable
internal fun BoxScope.HomeDraggedItemOverlay(
    session: HomeDragSession,
    grid: GridDimensions,
    maxWidthPx: Float,
    maxHeightPx: Float,
    cellSizePx: Float,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
) {
    val cellWidthPx = maxWidthPx / grid.columns
    val cellHeightPx = maxHeightPx / grid.rows
    val translationX =
        ((session.originCell.column + 0.5f) * cellWidthPx) +
            session.dragOffsetX -
            (cellSizePx / 2f)
    val translationY =
        ((session.originCell.row + 0.5f) * cellHeightPx) +
            session.dragOffsetY -
            (cellSizePx / 2f)
    val cellSize = with(LocalDensity.current) { cellSizePx.toDp() }

    Box(
        modifier =
            Modifier
                .size(cellSize)
                .graphicsLayer {
                    this.translationX = translationX
                    this.translationY = translationY
                    shadowElevation = DRAG_GHOST_ELEVATION
                    alpha = DRAG_GHOST_ALPHA
                },
        contentAlignment = Alignment.Center,
    ) {
        HomeDraggedItemContent(
            item = session.item,
            presentation = presentation,
            appIconLoader = appIconLoader,
        )
    }
}

internal fun Modifier.homeItemDrag(
    enabled: Boolean,
    item: LauncherItem,
    dragState: HomeItemDragState,
    actions: HomeWorkspaceActions,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(item.id, dragState.cell, dragState.cellSizePx) {
            var dragX = 0f
            var dragY = 0f

            detectDragGestures(
                onDragStart = {
                    dragX = 0f
                    dragY = 0f
                    actions.haptics.longPress()
                    actions.onDragSessionChanged(
                        HomeDragSession(
                            item = item,
                            originCell = dragState.cell,
                            projectedCell = dragState.cell,
                        ),
                    )
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragX += dragAmount.x
                    dragY += dragAmount.y
                    actions.onDragSessionChanged(
                        HomeDragSession(
                            item = item,
                            originCell = dragState.cell,
                            dragOffsetX = dragX,
                            dragOffsetY = dragY,
                            projectedCell = dragState.projectedCell(dragX = dragX, dragY = dragY),
                        ),
                    )
                },
                onDragEnd = {
                    actions.onDragSessionChanged(null)
                    dragState.dropCell(dragX = dragX, dragY = dragY)
                        ?.let { targetCell ->
                            actions.onAction(
                                LauncherShellAction.MoveHomeShortcutToCell(
                                    itemId = item.id,
                                    cell = targetCell,
                                ),
                            )
                        }
                },
                onDragCancel = {
                    actions.onDragSessionChanged(null)
                },
            )
        }
    }

@Composable
private fun HomeDraggedItemContent(
    item: LauncherItem,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (item) {
            is AppShortcutItem ->
                LauncherAppIcon(
                    identity = item.appIdentity,
                    label = item.label,
                    iconLoader = appIconLoader,
                    modifier = Modifier.size(HOME_ICON_SIZE_DP.dp),
                )

            is FolderItem ->
                FolderPreviewIcon(
                    folder = item,
                    appIconLoader = appIconLoader,
                )

            is WidgetItem ->
                HomeWidgetPlaceholder(
                    widget = item,
                    isEditing = false,
                    onAction = {},
                )
        }
        WallpaperReadableLabel(
            text = item.dragLabel,
            settings = presentation.labelSettings,
        )
    }
}

private fun HomeItemDragState.projectedCell(
    dragX: Float,
    dragY: Float,
): GridCell =
    GridCell(
        column = cell.column + (dragX / cellSizePx).roundToInt(),
        row = cell.row + (dragY / cellSizePx).roundToInt(),
    ).coerceIn(grid)

private fun GridCell.coerceIn(grid: GridDimensions): GridCell =
    GridCell(
        column = column.coerceIn(0, grid.columns - 1),
        row = row.coerceIn(0, grid.rows - 1),
    )

private fun HomeItemDragState.dropCell(
    dragX: Float,
    dragY: Float,
): GridCell? {
    val columnDelta = (dragX / cellSizePx).roundToInt()
    val rowDelta = (dragY / cellSizePx).roundToInt()

    return when {
        columnDelta == 0 && rowDelta == 0 -> null
        else ->
            GridCell(
                column = cell.column + columnDelta,
                row = cell.row + rowDelta,
            ).coerceIn(grid)
    }
}

private val LauncherItem.dragLabel: String
    get() =
        when (this) {
            is AppShortcutItem -> label
            is FolderItem -> label
            is WidgetItem -> label
        }

private const val DRAG_PLACEHOLDER_STROKE_WIDTH_DP = 2
private const val DRAG_PLACEHOLDER_DASH_DP = 8
private const val DRAG_PLACEHOLDER_GAP_DP = 6
private const val DRAG_GHOST_ELEVATION = 12f
private const val DRAG_GHOST_ALPHA = 0.92f
