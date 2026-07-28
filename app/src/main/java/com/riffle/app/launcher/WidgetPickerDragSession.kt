package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

internal enum class WidgetPickerDragTarget {
    HOME,
    DOCK,
}

internal data class WidgetPickerDragPlacementPreview(
    val provider: InstalledWidgetProvider,
    val target: WidgetPickerDragTarget,
    val targetPageId: LauncherPageId,
    val cell: GridCell,
    val span: GridSpan,
    val isValid: Boolean,
    val conflictingItemIds: Set<LauncherItemId> = emptySet(),
)

internal fun widgetPickerDragPlacementPreviewFor(
    page: LauncherPage,
    provider: InstalledWidgetProvider,
    cell: GridCell,
): WidgetPickerDragPlacementPreview {
    val span = provider.widgetPickerDragSpan(page.grid)
    val candidateCells = span.cellsAt(cell)
    val conflictingItems =
        page.items
            .filter { item -> candidateCells.any(item::occupies) }
            .map { item -> item.id }
            .toSet()
    val isInBounds = candidateCells.all { candidate -> page.grid.contains(candidate) }

    return WidgetPickerDragPlacementPreview(
        provider = provider,
        target = WidgetPickerDragTarget.HOME,
        targetPageId = page.id,
        cell = cell,
        span = span,
        isValid = page.type !is LauncherPageType.Generated && isInBounds && conflictingItems.isEmpty(),
        conflictingItemIds = conflictingItems,
    )
}

private fun InstalledWidgetProvider.widgetPickerDragSpan(grid: GridDimensions): GridSpan =
    GridSpan(
        columns = (dimensions.targetCellWidth ?: 1).coerceIn(1, grid.columns.coerceAtLeast(1)),
        rows = (dimensions.targetCellHeight ?: 1).coerceIn(1, grid.rows.coerceAtLeast(1)),
    )

private fun GridSpan.cellsAt(origin: GridCell): List<GridCell> =
    (origin.column until origin.column + columns.coerceAtLeast(1)).flatMap { column ->
        (origin.row until origin.row + rows.coerceAtLeast(1)).map { row ->
            GridCell(column = column, row = row)
        }
    }

private fun GridDimensions.contains(cell: GridCell): Boolean {
    return cell.column in 0 until columns && cell.row in 0 until rows
}
