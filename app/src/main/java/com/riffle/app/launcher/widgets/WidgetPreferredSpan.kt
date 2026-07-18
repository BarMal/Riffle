package com.riffle.app.launcher.widgets

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import kotlin.math.ceil

fun WidgetProviderDimensions.preferredGridSpan(
    grid: GridDimensions,
    availableWidthDp: Int,
    availableHeightDp: Int,
): GridSpan {
    return GridSpan(
        columns =
            targetCellWidth?.takeIf { it > 0 }
                ?: minWidthDp.spanCells(availableDp = availableWidthDp, gridCells = grid.columns),
        rows =
            targetCellHeight?.takeIf { it > 0 }
                ?: minHeightDp.spanCells(availableDp = availableHeightDp, gridCells = grid.rows),
    ).fitWidgetPreferredSpan(grid)
}

fun WidgetProviderDimensions.resizeConstraints(
    grid: GridDimensions,
    availableWidthDp: Int,
    availableHeightDp: Int,
    supportsHorizontalResize: Boolean,
    supportsVerticalResize: Boolean,
): WidgetResizeConstraints {
    val preferred = preferredGridSpan(grid, availableWidthDp, availableHeightDp)
    val minWidth = minResizeWidthDp ?: minWidthDp
    val minHeight = minResizeHeightDp ?: minHeightDp
    val maxWidth = maxResizeWidthDp ?: availableWidthDp
    val maxHeight = maxResizeHeightDp ?: availableHeightDp
    val minimum =
        GridSpan(
            columns = minWidth.spanCells(availableWidthDp, grid.columns),
            rows = minHeight.spanCells(availableHeightDp, grid.rows),
        ).fitWidgetPreferredSpan(grid)
    val maximum =
        GridSpan(
            columns = maxWidth.spanCells(availableWidthDp, grid.columns),
            rows = maxHeight.spanCells(availableHeightDp, grid.rows),
        ).fitWidgetPreferredSpan(grid)
    return WidgetResizeConstraints(
        minSpan =
            GridSpan(
                columns = if (supportsHorizontalResize) minimum.columns else preferred.columns,
                rows = if (supportsVerticalResize) minimum.rows else preferred.rows,
            ),
        maxSpan =
            GridSpan(
                columns =
                    if (supportsHorizontalResize) {
                        maxOf(minimum.columns, maximum.columns)
                    } else {
                        preferred.columns
                    },
                rows =
                    if (supportsVerticalResize) {
                        maxOf(minimum.rows, maximum.rows)
                    } else {
                        preferred.rows
                    },
            ),
        supportsHorizontalResize = supportsHorizontalResize,
        supportsVerticalResize = supportsVerticalResize,
    )
}

fun GridSpan.fitWidgetPreferredSpan(grid: GridDimensions): GridSpan =
    GridSpan(
        columns = columns.coerceIn(1, grid.columns.validGridCells()),
        rows = rows.coerceIn(1, grid.rows.validGridCells()),
    )

fun widgetSpanAdjustmentToast(
    label: String,
    idealSpan: GridSpan,
    actualSpan: GridSpan,
): String? =
    when {
        actualSpan.columns >= idealSpan.columns && actualSpan.rows >= idealSpan.rows -> null
        else ->
            "$label ideal size is ${idealSpan.columns}x${idealSpan.rows}; " +
                "added as ${actualSpan.columns}x${actualSpan.rows}"
    }

private fun Int.spanCells(
    availableDp: Int,
    gridCells: Int,
): Int {
    val boundedGridCells = gridCells.validGridCells()
    val boundedAvailableDp = availableDp.coerceAtLeast(1)
    val cellDp = boundedAvailableDp.toFloat() / boundedGridCells.toFloat()

    return ceil(coerceAtLeast(0) / cellDp)
        .toInt()
        .coerceIn(1, boundedGridCells)
}

private fun Int.validGridCells(): Int = coerceAtLeast(1)
