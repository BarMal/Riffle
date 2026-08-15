package com.riffle.app.launcher.apps

import com.riffle.app.launcher.BoundedCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val cache = BoundedCache<String, String>(maxEntries = 2)

        cache["first"] = "first icon"
        cache["second"] = "second icon"
        assertEquals("first icon", cache["first"])
        cache["third"] = "third icon"

        assertEquals(2, cache.size)
        assertEquals("first icon", cache["first"])
        assertNull(cache["second"])
        assertEquals("third icon", cache["third"])
    }

    /**
     * Mirrors how [PackageManagerAppIconLoader] caches dominant colors: the cached payload is a
     * wrapper around a nullable value, so a computed-but-absent color ([WrappedValue.value] is
     * null) is a present cache hit that must not be confused with a genuine cache miss (no entry
     * at all), and both share the icon cache's exact size bound and LRU eviction policy.
     */
    @Test
    fun cachedNullPayloadIsDistinguishableFromACacheMiss() {
        val cache = BoundedCache<String, WrappedValue>(maxEntries = 2)

        cache["knownNull"] = WrappedValue(null)

        assertNotNull(cache["knownNull"])
        assertNull(cache["knownNull"]?.value)
        assertNull(cache["neverLoaded"])
    }

    @Test
    fun wrappedValueCacheSharesTheSameBoundAndEvictionPolicyAsTheIconCache() {
        val cache = BoundedCache<String, WrappedValue>(maxEntries = 2)

        cache["first"] = WrappedValue("red")
        cache["second"] = WrappedValue(null)
        assertNotNull(cache["first"])
        cache["third"] = WrappedValue("blue")

        assertEquals(2, cache.size)
        assertEquals("red", cache["first"]?.value)
        assertNull(cache["second"])
        assertEquals("blue", cache["third"]?.value)
    }

    private data class WrappedValue(val value: String?)
}
