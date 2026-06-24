package com.riffle.app.launcher.widgets

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import kotlin.math.ceil

fun WidgetProviderDimensions.preferredGridSpan(
    grid: GridDimensions,
    availableWidthDp: Int,
    availableHeightDp: Int,
): GridSpan =
    GridSpan(
        columns =
            targetCellWidth
                ?.coerceIn(1, grid.columns)
                ?: minWidthDp.spanCells(availableDp = availableWidthDp, gridCells = grid.columns),
        rows =
            targetCellHeight
                ?.coerceIn(1, grid.rows)
                ?: minHeightDp.spanCells(availableDp = availableHeightDp, gridCells = grid.rows),
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
    val cellDp = availableDp.toFloat() / gridCells.toFloat()

    return ceil(this / cellDp)
        .toInt()
        .coerceIn(1, gridCells)
}
