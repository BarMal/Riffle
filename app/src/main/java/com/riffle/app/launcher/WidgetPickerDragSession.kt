package com.riffle.app.launcher

import com.riffle.app.launcher.widgets.preferredGridSpan
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
    availableWidthDp: Int,
    availableHeightDp: Int,
): WidgetPickerDragPlacementPreview {
    val span =
        provider.dimensions.preferredGridSpan(
            grid = page.grid,
            availableWidthDp = availableWidthDp,
            availableHeightDp = availableHeightDp,
        )
    val isInBounds = page.grid.contains(origin = cell, span = span)
    val candidateCells = if (isInBounds) span.cellsAt(cell) else emptyList()
    val conflictingItems =
        page.items
            .filter { item -> candidateCells.any(item::occupies) }
            .map { item -> item.id }
            .toSet()
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

private fun GridSpan.cellsAt(origin: GridCell): List<GridCell> =
    (origin.column until origin.column + columns.coerceAtLeast(1)).flatMap { column ->
        (origin.row until origin.row + rows.coerceAtLeast(1)).map { row ->
            GridCell(column = column, row = row)
        }
    }

private fun GridDimensions.contains(
    origin: GridCell,
    span: GridSpan,
): Boolean {
    return columns > 0 &&
        rows > 0 &&
        origin.column >= 0 &&
        origin.row >= 0 &&
        span.columns > 0 &&
        span.rows > 0 &&
        span.columns <= columns &&
        span.rows <= rows &&
        origin.column <= columns - span.columns &&
        origin.row <= rows - span.rows
}
