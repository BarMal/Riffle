package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// AdaptiveStageArtworkCache is backed by android.util.LruCache, which the plain "mockable
// android.jar" used by non-Robolectric JVM unit tests stubs to throw on every call. Robolectric
// runs the real platform implementation instead.
@RunWith(RobolectricTestRunner::class)
class AdaptiveStageArtworkCacheTest {
    @Test
    fun artworkCacheCachesDecodeFailuresAndEvictsLeastRecentlyUsedArtwork() {
        var decodeCalls = 0
        val cache =
            AdaptiveStageArtworkCache<Int>(maxEntries = 2) {
                decodeCalls += 1
                if (it == "corrupt") null else it?.length
            }

        assertEquals(null, cache.getOrDecode("corrupt-card", "corrupt"))
        assertEquals(null, cache.getOrDecode("corrupt-card", "corrupt"))
        assertEquals(1, decodeCalls)

        assertEquals(1, cache.getOrDecode("a", "a"))
        assertEquals(2, cache.getOrDecode("bb", "bb"))
        assertEquals(1, cache.getOrDecode("a", "a"))
        assertEquals(3, cache.getOrDecode("ccc", "ccc"))
        assertEquals(2, cache.getOrDecode("bb", "bb"))

        assertEquals(5, decodeCalls)
        assertEquals(2, cache.sizeForTest())
    }
}
