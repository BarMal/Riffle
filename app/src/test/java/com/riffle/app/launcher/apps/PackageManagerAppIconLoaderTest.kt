package com.riffle.app.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageManagerAppIconLoaderTest {
    @Test
    fun usesEnoughPixelsForTheLargestLauncherIconAtTypicalHighDensity() {
        assertEquals(320, launcherIconBitmapSizePx(displayDensity = 4f))
    }

    @Test
    fun keepsAUsefulMinimumForLowDensityDisplays() {
        assertEquals(96, launcherIconBitmapSizePx(displayDensity = 1f))
    }

    @Test
    fun capsBitmapSizeToKeepThePreloadedIconCacheBounded() {
        assertEquals(320, launcherIconBitmapSizePx(displayDensity = 5f))
    }
}
