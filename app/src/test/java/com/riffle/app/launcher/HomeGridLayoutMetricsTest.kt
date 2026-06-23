package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridLayoutMetricsTest {
    private val metrics = HomeGridLayoutMetrics()

    @Test
    fun cellSizeUsesWidthBoundWhenWidthIsTighter() {
        assertEquals(
            100f,
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
            80f,
            metrics.cellSizePx(
                grid = GridDimensions(columns = 4, rows = 5),
                maxWidthPx = 800f,
                maxHeightPx = 400f,
            ),
        )
    }

    @Test
    fun cellSizeCanShrinkIntoTinyWorkspace() {
        assertEquals(
            2f,
            metrics.cellSizePx(
                grid = GridDimensions(columns = 4, rows = 5),
                maxWidthPx = 10f,
                maxHeightPx = 10f,
            ),
        )
    }
}
