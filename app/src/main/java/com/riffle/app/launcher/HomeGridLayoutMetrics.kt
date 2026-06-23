package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions

class HomeGridLayoutMetrics {
    fun cellSizePx(
        grid: GridDimensions,
        maxWidthPx: Float,
        maxHeightPx: Float,
    ): Float {
        val widthBoundPx = maxWidthPx / grid.columns
        val heightBoundPx = maxHeightPx / grid.rows

        return minOf(widthBoundPx, heightBoundPx).coerceAtLeast(0f)
    }
}
