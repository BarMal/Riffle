package com.riffle.app.launcher.widgets

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
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
