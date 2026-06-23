package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridLayoutMetricsTest {
    private val metrics = HomeGridLayoutMetrics(cellSpacingPx = 12f)

    @Test
    fun cellSizeUsesWidthBoundWhenWidthIsTighter() {
        assertEquals(
            91f,
            metrics.cellSizePx(
                grid = GridDimensions(columns = 4, rows = 5),
                maxWidthPx = 400f,
                maxHeightPx = 800f,
            ),
        )
    }

    @Test
    fun cellSizeUsesHeightBoundWhenHeightIsTighter() {
        assertEquals(
            70.4f,
            metrics.cellSizePx(
                grid = GridDimensions(columns = 4, rows = 5),
                maxWidthPx = 800f,
                maxHeightPx = 400f,
            ),
        )
    }

    @Test
    fun cellSizeDoesNotGoNegativeWhenWorkspaceIsTooSmall() {
        assertEquals(
            0f,
            metrics.cellSizePx(
                grid = GridDimensions(columns = 4, rows = 5),
                maxWidthPx = 10f,
                maxHeightPx = 10f,
            ),
        )
    }
}
