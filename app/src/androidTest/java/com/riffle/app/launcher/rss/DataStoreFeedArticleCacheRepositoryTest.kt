package com.riffle.app.launcher.rss

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riffle.core.domain.launcher.rss.FeedId
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreFeedArticleCacheRepositoryTest {
    private val clock = FakeEpochMillisProvider(1_000L)
    private val repository by lazy {
        DataStoreFeedArticleCacheRepository(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            epochMillisProvider = clock,
        )
    }

    @Before
    fun clearBeforeEachTest() {
        repository.clear()
    }

    @After
    fun clearAfterEachTest() {
        repository.clear()
    }

    @Test
    fun persistsAndReloadsAFeedAsFresh() {
        val feedId = FeedId("news")
        repository.replaceFeed(feedId, listOf(article("a")))

        val result = repository.loadFeed(feedId)

        assertTrue(result is FeedCacheResult.Available)
        val snapshot = (result as FeedCacheResult.Available).snapshot
        assertEquals(CacheFreshness.FRESH, snapshot.freshness)
        assertEquals(listOf("a".repeat(64)), snapshot.feed.articles.map { it.digest })
    }

    @Test
    fun reportsStaleCacheInsteadOfNothingWhenPastTheStaleWindow() {
        val feedId = FeedId("news")
        repository.replaceFeed(feedId, listOf(article("a")))

        clock.value = 1_000L + DEFAULT_STALE_AFTER_MILLIS + 1
        val result = repository.loadFeed(feedId)

        assertTrue(result is FeedCacheResult.Available)
        val snapshot = (result as FeedCacheResult.Available).snapshot
        assertEquals(CacheFreshness.STALE, snapshot.freshness)
        assertEquals(1, snapshot.feed.articles.size)
    }

    @Test
    fun reportsEmptyWhenNoFeedHasEverBeenCached() {
        val result = repository.loadFeed(FeedId("unknown"))

        assertEquals(FeedCacheResult.Empty, result)
    }

    @Test
    fun readAndDismissedStateIsKeyedOnlyByDigestAndSurvivesRefetch() {
        val feedId = FeedId("news")
        val digest = "a".repeat(64)
        repository.replaceFeed(feedId, listOf(article("a")))

        assertTrue(!repository.isRead(digest))
        repository.markRead(digest)
        assertTrue(repository.isRead(digest))

        repository.markDismissed(digest)
        assertTrue(repository.isDismissed(digest))

        // Refetching (replacing the cached content) must not clear read/dismiss intent state.
        repository.replaceFeed(feedId, listOf(article("a")))
        assertTrue(repository.isRead(digest))
        assertTrue(repository.isDismissed(digest))
    }

    @Test
    fun cachesAndReturnsImageBytesForAReferencedDigest() {
        val feedId = FeedId("news")
        val digest = "a".repeat(64)
        repository.replaceFeed(feedId, listOf(article("a")))
        val bytes = byteArrayOf(1, 2, 3, 4)

        repository.cacheImage(digest, bytes)

        assertArrayEquals(bytes, repository.cachedImage(digest))
    }

    @Test
    fun ignoresAnImageForADigestThatIsNotCurrentlyCached() {
        repository.cacheImage("a".repeat(64), byteArrayOf(1, 2, 3))

        assertNull(repository.cachedImage("a".repeat(64)))
    }

    @Test
    fun ignoresAnOversizedImage() {
        val feedId = FeedId("news")
        val digest = "a".repeat(64)
        repository.replaceFeed(feedId, listOf(article("a")))

        repository.cacheImage(digest, ByteArray(MAX_CACHED_IMAGE_BYTES + 1))

        assertNull(repository.cachedImage(digest))
    }

    @Test
    fun clearFeedRemovesItsArticlesAndOrphanedImagesButNotOtherFeeds() {
        val newsId = FeedId("news")
        val sportsId = FeedId("sports")
        repository.replaceFeed(newsId, listOf(article("a")))
        repository.replaceFeed(sportsId, listOf(article("b")))
        repository.cacheImage("a".repeat(64), byteArrayOf(1))

        repository.clearFeed(newsId)

        assertEquals(FeedCacheResult.Empty, repository.loadFeed(newsId))
        assertTrue(repository.loadFeed(sportsId) is FeedCacheResult.Available)
        assertNull(repository.cachedImage("a".repeat(64)))
    }

    @Test
    fun clearRemovesEverythingIncludingReadDismissStateAndImages() {
        val feedId = FeedId("news")
        val digest = "a".repeat(64)
        repository.replaceFeed(feedId, listOf(article("a")))
        repository.markRead(digest)
        repository.markDismissed(digest)
        repository.cacheImage(digest, byteArrayOf(9))

        repository.clear()

        assertEquals(FeedCacheResult.Empty, repository.loadFeed(feedId))
        assertTrue(!repository.isRead(digest))
        assertTrue(!repository.isDismissed(digest))
        assertNull(repository.cachedImage(digest))
    }

    @Test
    fun recoversFromACorruptOrObsoletePersistedCacheWithoutCrashing() {
        assertNull(decodeFeedArticleCacheDocument("not-json"))
        assertEquals(FeedCacheResult.Empty, repository.loadFeed(FeedId("news")))

        // A subsequent write must still succeed even after prior corruption.
        repository.replaceFeed(FeedId("news"), listOf(article("a")))
        assertTrue(repository.loadFeed(FeedId("news")) is FeedCacheResult.Available)
    }

    private fun article(seed: String): CachedFeedArticle =
        CachedFeedArticle(
            digest = seed.repeat(64 / seed.length),
            title = "Title $seed",
            sourceOrder = 0,
        )

    private class FakeEpochMillisProvider(
        var value: Long,
    ) : com.riffle.app.launcher.EpochMillisProvider {
        override fun nowEpochMillis(): Long = value
    }
}
