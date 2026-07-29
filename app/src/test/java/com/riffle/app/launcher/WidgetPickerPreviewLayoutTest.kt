package com.riffle.app.launcher

import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPickerPreviewLayoutTest {
    @Test
    fun previewAspectRatioUsesTargetCellShapeWhenAvailable() {
        assertEquals(
            2f,
            widgetProvider(targetCellWidth = 4, targetCellHeight = 2).widgetPickerPreviewAspectRatio(),
        )
    }

    @Test
    fun previewAspectRatioFallsBackToMinimumDimensions() {
        assertEquals(
            1.5f,
            widgetProvider(minWidthDp = 150, minHeightDp = 100).widgetPickerPreviewAspectRatio(),
        )
    }

    @Test
    fun previewAspectRatioPreservesExtremeProviderShapes() {
        assertEquals(
            8f,
            widgetProvider(minWidthDp = 800, minHeightDp = 100).widgetPickerPreviewAspectRatio(),
        )
        assertEquals(
            0.125f,
            widgetProvider(minWidthDp = 100, minHeightDp = 800).widgetPickerPreviewAspectRatio(),
        )
    }

    @Test
    fun previewSizePreservesExtremeProviderShapesWithinTheHeightBound() {
        assertEquals(
            300.dp to 75.dp,
            widgetPickerPreviewSize(maxWidth = 300.dp, preferredAspectRatio = 4f).let { it.width to it.height },
        )
        assertEquals(
            180.dp to 96.dp,
            widgetPickerPreviewSize(maxWidth = 300.dp, preferredAspectRatio = 0.125f).let {
                it.width to it.height
            },
        )
    }

    @Test
    fun previewSizeUsesALegibleConstrainedStateForExtremeProviderRatios() {
        assertEquals(
            180.dp to 96.dp,
            widgetPickerPreviewSize(maxWidth = 300.dp, preferredAspectRatio = 0.0001f).let {
                it.width to it.height
            },
        )
        assertEquals(
            180.dp to 96.dp,
            widgetPickerPreviewSize(maxWidth = 300.dp, preferredAspectRatio = 10_000f).let {
                it.width to it.height
            },
        )
        assertTrue(widgetPickerPreviewIsConstrained(maxWidth = 300.dp, preferredAspectRatio = 0.0001f))
        assertTrue(widgetPickerPreviewIsConstrained(maxWidth = 300.dp, preferredAspectRatio = 10_000f))
    }

    @Test
    fun previewSizeFallsBackToSquareForInvalidAspectRatios() {
        assertEquals(
            240.dp to 240.dp,
            widgetPickerPreviewSize(maxWidth = 300.dp, preferredAspectRatio = Float.NaN).let {
                it.width to it.height
            },
        )
    }

    private fun widgetProvider(
        minWidthDp: Int = 120,
        minHeightDp: Int = 80,
        targetCellWidth: Int? = null,
        targetCellHeight: Int? = null,
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName("com.example.widget"),
                    className = WidgetProviderClassName(".Widget"),
                ),
            label = "Widget",
            dimensions =
                WidgetProviderDimensions(
                    minWidthDp = minWidthDp,
                    minHeightDp = minHeightDp,
                    targetCellWidth = targetCellWidth,
                    targetCellHeight = targetCellHeight,
                ),
        )
}
