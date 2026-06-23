package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions

class HomeGridLayoutMetrics(
    private val cellSpacingPx: Float,
) {
    fun cellSizePx(
        grid: GridDimensions,
        maxWidthPx: Float,
        maxHeightPx: Float,
    ): Float {
        val widthSpacingPx = cellSpacingPx * (grid.columns - 1).coerceAtLeast(0)
        val heightSpacingPx = cellSpacingPx * (grid.rows - 1).coerceAtLeast(0)
        val widthBoundPx = (maxWidthPx - widthSpacingPx) / grid.columns
        val heightBoundPx = (maxHeightPx - heightSpacingPx) / grid.rows

        return minOf(widthBoundPx, heightBoundPx).coerceAtLeast(0f)
    }
}
