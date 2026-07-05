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
    val gridColumns = grid.columns.validGridCells()
    val gridRows = grid.rows.validGridCells()

    return GridSpan(
        columns =
            targetCellWidth
                ?.coerceIn(1, gridColumns)
                ?: minWidthDp.spanCells(availableDp = availableWidthDp, gridCells = gridColumns),
        rows =
            targetCellHeight
                ?.coerceIn(1, gridRows)
                ?: minHeightDp.spanCells(availableDp = availableHeightDp, gridCells = gridRows),
    )
}

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
