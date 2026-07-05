package com.riffle.app.launcher.widgets

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPreferredSpanTest {
    @Test
    fun calculatesPreferredSpanFromProviderDimensionsAndAvailableGridSize() {
        val span =
            WidgetProviderDimensions(
                minWidthDp = 240,
                minHeightDp = 160,
            ).preferredGridSpan(
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertEquals(GridSpan(columns = 3, rows = 2), span)
    }

    @Test
    fun prefersAndroidTargetCellsWhenAvailable() {
        val span =
            WidgetProviderDimensions(
                minWidthDp = 40,
                minHeightDp = 40,
                targetCellWidth = 3,
                targetCellHeight = 2,
            ).preferredGridSpan(
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertEquals(GridSpan(columns = 3, rows = 2), span)
    }

    @Test
    fun clampsPreferredSpanToGridBounds() {
        val span =
            WidgetProviderDimensions(
                minWidthDp = 800,
                minHeightDp = 700,
            ).preferredGridSpan(
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertEquals(GridSpan(columns = 4, rows = 5), span)
    }

    @Test
    fun clampsDegenerateGridBoundsToOneCellSpan() {
        val span =
            WidgetProviderDimensions(
                minWidthDp = 240,
                minHeightDp = 160,
                targetCellWidth = 0,
                targetCellHeight = -2,
            ).preferredGridSpan(
                grid = GridDimensions(columns = 0, rows = -3),
                availableWidthDp = 0,
                availableHeightDp = -10,
            )

        assertEquals(GridSpan(columns = 1, rows = 1), span)
    }

    @Test
    fun keepsSpanValidWhenAvailableBoundsAreDegenerate() {
        val span =
            WidgetProviderDimensions(
                minWidthDp = 240,
                minHeightDp = 160,
            ).preferredGridSpan(
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 0,
                availableHeightDp = -10,
            )

        assertEquals(GridSpan(columns = 4, rows = 5), span)
    }

    @Test
    fun keepsSpanValidWhenProviderMinimumsAreDegenerate() {
        val span =
            WidgetProviderDimensions(
                minWidthDp = -20,
                minHeightDp = 0,
            ).preferredGridSpan(
                grid = GridDimensions(columns = 4, rows = 5),
                availableWidthDp = 400,
                availableHeightDp = 500,
            )

        assertEquals(GridSpan(columns = 1, rows = 1), span)
    }

    @Test
    fun reportsWhenWidgetWasShrunkFromIdealSpan() {
        assertEquals(
            "Weather ideal size is 3x2; added as 2x2",
            widgetSpanAdjustmentToast(
                label = "Weather",
                idealSpan = GridSpan(columns = 3, rows = 2),
                actualSpan = GridSpan(columns = 2, rows = 2),
            ),
        )
    }

    @Test
    fun omitsToastWhenWidgetWasNotShrunk() {
        assertNull(
            widgetSpanAdjustmentToast(
                label = "Weather",
                idealSpan = GridSpan(columns = 2, rows = 2),
                actualSpan = GridSpan(columns = 2, rows = 2),
            ),
        )
    }
}
