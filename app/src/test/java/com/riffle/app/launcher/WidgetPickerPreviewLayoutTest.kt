package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
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
