package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem

internal fun LauncherPage.itemsForDragPreview(session: HomeDragSession?): List<LauncherItem> =
    session
        ?.takeIf { dragSession -> items.any { item -> item.id == dragSession.item.id } }
        ?.let(::itemsForActiveDragPreview)
        ?: items

private fun LauncherPage.itemsForActiveDragPreview(dragSession: HomeDragSession): List<LauncherItem> {
    val draggedItem = items.first { item -> item.id == dragSession.item.id }
    val itemsWithoutDraggedItem = items.filterNot { item -> item.id == dragSession.item.id }
    val originIndex = grid.indexOf(dragSession.originCell)
    val targetIndex = grid.indexOf(dragSession.projectedCell)
    val targetItem = draggedItem.withPreviewCell(dragSession.projectedCell) ?: return items

    return when {
        originIndex == null || targetIndex == null -> items
        originIndex == targetIndex -> itemsWithoutDraggedItem
        itemsWithoutDraggedItem.none { item -> item.occupiesAnyCellWith(targetItem) } -> itemsWithoutDraggedItem
        else -> items.shiftedForDrag(originIndex = originIndex, targetIndex = targetIndex, grid = grid)
    }
}

private fun List<LauncherItem>.shiftedForDrag(
    originIndex: Int,
    targetIndex: Int,
    grid: GridDimensions,
): List<LauncherItem> {
    val itemsByCellIndex = itemsByCellIndex(grid).apply { remove(originIndex) }

    itemsByCellIndex.shiftIndexes(originIndex = originIndex, targetIndex = targetIndex)

    return itemsByCellIndex
        .mapNotNull { (index, item) ->
            grid.cellAt(index)?.let { cell -> item.withPreviewCell(cell) }
        }
}

private fun List<LauncherItem>.itemsByCellIndex(grid: GridDimensions): MutableMap<Int, LauncherItem> =
    mapNotNull { item ->
        item.placement?.cell
            ?.let(grid::indexOf)
            ?.let { index -> index to item }
    }
        .toMap()
        .toMutableMap()

private fun MutableMap<Int, LauncherItem>.shiftIndexes(
    originIndex: Int,
    targetIndex: Int,
) {
    when {
        targetIndex > originIndex ->
            for (index in originIndex until targetIndex) {
                moveIndex(from = index + 1, to = index)
            }

        else ->
            for (index in originIndex downTo targetIndex + 1) {
                moveIndex(from = index - 1, to = index)
            }
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

private fun LauncherItem.occupiesAnyCellWith(other: LauncherItem): Boolean =
    placement?.occupiedCells.orEmpty().intersect(other.placement?.occupiedCells.orEmpty()).isNotEmpty()

private val GridPlacement.occupiedCells: Set<GridCell>
    get() =
        (cell.column until cell.column + span.columns.coerceAtLeast(1))
            .flatMap { column ->
                (cell.row until cell.row + span.rows.coerceAtLeast(1)).map { row ->
                    GridCell(column = column, row = row)
                }
            }
            .toSet()

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
