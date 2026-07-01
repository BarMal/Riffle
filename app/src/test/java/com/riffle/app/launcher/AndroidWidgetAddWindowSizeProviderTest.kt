package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidWidgetAddWindowSizeProviderTest {
    @Test
    fun convertsCurrentWindowBoundsFromPixelsToDp() {
        assertEquals(
            LauncherWidgetAddWindowSize(
                availableWidthDp = 360,
                availableHeightDp = 800,
            ),
            launcherWidgetAddWindowSizeFromPixels(
                widthPx = 1080,
                heightPx = 2400,
                density = 3f,
            ),
        )
    }

    @Test
    fun fallsBackToOneToOnePixelsWhenDensityIsUnavailable() {
        assertEquals(
            LauncherWidgetAddWindowSize(
                availableWidthDp = 1080,
                availableHeightDp = 2400,
            ),
            launcherWidgetAddWindowSizeFromPixels(
                widthPx = 1080,
                heightPx = 2400,
                density = 0f,
            ),
        )
    }
}
