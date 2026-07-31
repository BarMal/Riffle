package com.riffle.app.launcher.rss

import android.graphics.Bitmap
import com.riffle.core.domain.launcher.rss.FeedId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class FeedArtworkLoaderTest {
    @Test
    fun emptyLoaderNeverProducesArtwork() {
        runBlocking { assertNull(EmptyFeedArtworkLoader.artworkFor("any-digest")) }
    }

    @Test
    fun decodeFeedArtworkBytesRejectsNullEmptyAndOversizedInput() {
        assertNull(decodeFeedArtworkBytes(null))
        assertNull(decodeFeedArtworkBytes(ByteArray(0)))
        assertNull(decodeFeedArtworkBytes(ByteArray(MAX_CACHED_IMAGE_BYTES + 1)))
    }

    @Test
    fun decodeFeedArtworkBytesRejectsCorruptBytes() {
        assertNull(decodeFeedArtworkBytes(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun decodeFeedArtworkBytesDecodesAValidBoundedImage() {
        val bytes = pngBytes(width = 32, height = 32)

        val decoded = decodeFeedArtworkBytes(bytes)

        assertTrue(decoded != null)
        assertEquals(32, decoded?.width)
        assertEquals(32, decoded?.height)
    }

    @Test
    fun repositoryLoaderDegradesGracefullyWhenNoImageIsCached() {
        val repository = FakeFeedArticleCacheRepository(imagesByDigest = emptyMap())
        val loader = RepositoryFeedArtworkLoader(repository)

        val artwork = runBlocking { loader.artworkFor(digest(1)) }

        assertNull(artwork)
    }

    @Test
    fun repositoryLoaderDecodesCachedBytesAndCachesTheResult() {
        val bytes = pngBytes(width = 16, height = 16)
        val repository = FakeFeedArticleCacheRepository(imagesByDigest = mapOf(digest(1) to bytes))
        val loader = RepositoryFeedArtworkLoader(repository)

        val first = runBlocking { loader.artworkFor(digest(1)) }
        val second = runBlocking { loader.artworkFor(digest(1)) }

        assertTrue(first != null)
        assertEquals(1, repository.cachedImageCallCount)
        assertTrue(second === first || second != null)
    }

    private fun digest(seed: Int): String = seed.toString().padStart(64, '0')

    private fun pngBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private class FakeFeedArticleCacheRepository(
        private val imagesByDigest: Map<String, ByteArray>,
    ) : FeedArticleCacheRepository {
        var cachedImageCallCount = 0
            private set

        override fun loadFeed(
            feedId: FeedId,
            staleAfterMillis: Long,
        ): FeedCacheResult = FeedCacheResult.Empty

        override fun replaceFeed(
            feedId: FeedId,
            articles: List<CachedFeedArticle>,
        ) = Unit

        override fun clearFeed(feedId: FeedId) = Unit

        override fun isRead(digest: String): Boolean = false

        override fun markRead(digest: String) = Unit

        override fun isDismissed(digest: String): Boolean = false

        override fun markDismissed(digest: String) = Unit

        override fun cachedImage(digest: String): ByteArray? {
            cachedImageCallCount++
            return imagesByDigest[digest]
        }

        override fun cacheImage(
            digest: String,
            bytes: ByteArray,
        ) = Unit

        override fun clear() = Unit
    }
}
