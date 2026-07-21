package com.riffle.app.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun evictsLeastRecentlyUsedIconsWhenTheCacheReachesItsBound() {
        val cache = BoundedIconCache<String, String>(maxEntries = 2)

        cache["first"] = "first icon"
        cache["second"] = "second icon"
        assertEquals("first icon", cache["first"])
        cache["third"] = "third icon"

        assertEquals(2, cache.size)
        assertEquals("first icon", cache["first"])
        assertNull(cache["second"])
        assertEquals("third icon", cache["third"])
    }
}
