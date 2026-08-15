package com.riffle.app.launcher.widgets

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// WidgetPreviewCache is backed by android.util.LruCache, which the plain "mockable android.jar"
// used by non-Robolectric JVM unit tests stubs to throw on every call. Robolectric runs the real
// platform implementation instead.
@RunWith(RobolectricTestRunner::class)
class WidgetPreviewCacheTest {
    @Test
    fun boundsEntriesAndEvictsLeastRecentlyUsed() {
        val cache = WidgetPreviewCache<String>(maxEntries = 2)
        val first = identity(".First")
        val second = identity(".Second")
        val third = identity(".Third")

        cache[first] = "first"
        cache[second] = "second"
        assertEquals("first", cache[first])
        cache[third] = "third"

        assertNull(cache[second])
        assertEquals("first", cache[first])
        assertEquals("third", cache[third])
        assertEquals(2, cache.size)
    }

    @Test
    fun remainsBoundedAndPreservesLruOrderDuringConcurrentAccess() {
        val cache = WidgetPreviewCache<String>(maxEntries = 3)
        val identities = (0 until 8).map { index -> identity(".Provider$index") }
        identities.take(3).forEachIndexed { index, identity -> cache[identity] = "preview-$index" }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)

        try {
            val tasks =
                (0 until 8).map { worker ->
                    executor.submit {
                        start.await()
                        repeat(2_000) { iteration ->
                            val index = (worker + iteration) % identities.size
                            val identity = identities[index]
                            cache[identity] = "preview-$index"
                            cache[identity]
                        }
                    }
                }
            start.countDown()
            tasks.forEach { task -> task.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertTrue(cache.size <= 3)
        cache[identities[0]] = "most-recent"
        cache[identities[1]] = "second-most-recent"
        cache[identities[2]] = "third-most-recent"
        assertEquals("most-recent", cache[identities[0]])
        cache[identities[3]] = "new"

        assertNull(cache[identities[1]])
        assertEquals("most-recent", cache[identities[0]])
        assertEquals("third-most-recent", cache[identities[2]])
        assertEquals("new", cache[identities[3]])
        assertEquals(3, cache.size)
    }

    @Test
    fun invalidationEvictsEntriesAndRejectsAnOlderInFlightLoad() {
        val cache = WidgetPreviewCache<String>(maxEntries = 2)
        val identity = identity(".Clock")
        val loadingRevision = cache.revision
        cache[identity] = "old preview"

        cache.invalidate()

        assertNull(cache[identity])
        assertEquals(loadingRevision + 1, cache.revision)
        assertEquals(false, cache.putIfRevision(identity, "stale completion", loadingRevision))
        assertNull(cache[identity])
        assertTrue(cache.putIfRevision(identity, "fresh preview", cache.revision))
        assertEquals("fresh preview", cache[identity])
    }

    private fun identity(className: String): WidgetProviderIdentity =
        WidgetProviderIdentity(
            packageName = AppPackageName("com.example.cache"),
            className = WidgetProviderClassName(className),
        )
}
