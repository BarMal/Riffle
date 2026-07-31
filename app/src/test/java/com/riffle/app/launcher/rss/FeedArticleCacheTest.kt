package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedArticleCacheTest {
    @Test
    fun rejectsMalformedDigestsOnConstruction() {
        assertFalse(isValidFeedItemDigest("not-hex"))
        assertFalse(isValidFeedItemDigest("A".repeat(64)))
        assertFalse(isValidFeedItemDigest("a".repeat(63)))
        assertTrue(isValidFeedItemDigest("a".repeat(64)))

        assertThrows(IllegalArgumentException::class.java) {
            article(digest = "not-a-digest")
        }
    }

    @Test
    fun boundFeedArticlesKeepsNewestFirstThenBoundsToPerFeedLimit() {
        val articles =
            listOf(
                article(digest = digest(1), publishedAtEpochMillis = 100L, sourceOrder = 0),
                article(digest = digest(2), publishedAtEpochMillis = 300L, sourceOrder = 1),
                article(digest = digest(3), publishedAtEpochMillis = 200L, sourceOrder = 2),
            )

        val bounded = boundFeedArticles(articles)

        assertEquals(listOf(digest(2), digest(3), digest(1)), bounded.map { it.digest })
    }

    @Test
    fun boundFeedArticlesPlacesUndatedArticlesAfterDatedOnesInSourceOrder() {
        val articles =
            listOf(
                article(digest = digest(1), publishedAtEpochMillis = null, sourceOrder = 1),
                article(digest = digest(2), publishedAtEpochMillis = 500L, sourceOrder = 0),
                article(digest = digest(3), publishedAtEpochMillis = null, sourceOrder = 0),
            )

        val bounded = boundFeedArticles(articles)

        assertEquals(listOf(digest(2), digest(3), digest(1)), bounded.map { it.digest })
    }

    @Test
    fun boundFeedArticlesEnforcesThePerFeedCap() {
        val articles =
            (0 until MAX_CACHED_ARTICLES_PER_FEED + 10).map { index ->
                article(digest = digest(index), publishedAtEpochMillis = index.toLong(), sourceOrder = index)
            }

        val bounded = boundFeedArticles(articles)

        assertEquals(MAX_CACHED_ARTICLES_PER_FEED, bounded.size)
        // Newest (highest publishedAt) articles are retained.
        assertTrue(bounded.all { it.publishedAtEpochMillis!! >= 10L })
    }

    @Test
    fun evictGlobalArticlesRetainsNewestAcrossFeedsDeterministically() {
        val oldFeed =
            CachedFeed(
                feedId = FeedId("old-feed"),
                fetchedAtEpochMillis = 1_000L,
                articles = listOf(article(digest = digest(1), publishedAtEpochMillis = 1_000L, sourceOrder = 0)),
            )
        val newFeed =
            CachedFeed(
                feedId = FeedId("new-feed"),
                fetchedAtEpochMillis = 5_000L,
                articles = listOf(article(digest = digest(2), publishedAtEpochMillis = 5_000L, sourceOrder = 0)),
            )

        // Force a tiny global cap by feeding exactly MAX_CACHED_ARTICLES_GLOBAL + 1 total articles.
        val fillerFeeds =
            (0 until MAX_CACHED_ARTICLES_GLOBAL - 1).map { index ->
                CachedFeed(
                    feedId = FeedId("filler-$index"),
                    fetchedAtEpochMillis = 2_000L,
                    articles =
                        listOf(
                            article(digest = digest(1_000 + index), publishedAtEpochMillis = 2_000L, sourceOrder = 0),
                        ),
                )
            }

        val evicted = evictGlobalArticles(listOf(oldFeed, newFeed) + fillerFeeds)
        val survivingDigests = evicted.flatMap { it.articles }.map { it.digest }.toSet()

        assertEquals(MAX_CACHED_ARTICLES_GLOBAL, survivingDigests.size)
        assertTrue(digest(2) in survivingDigests)
        assertFalse(digest(1) in survivingDigests)
    }

    @Test
    fun evictGlobalArticlesIsDeterministicRegardlessOfInputOrder() {
        val feeds =
            (0 until 5).map { index ->
                CachedFeed(
                    feedId = FeedId("feed-$index"),
                    fetchedAtEpochMillis = index.toLong(),
                    articles =
                        listOf(
                            article(digest = digest(index), publishedAtEpochMillis = index.toLong(), sourceOrder = 0),
                        ),
                )
            }

        val forward = evictGlobalArticles(feeds)
        val reversed = evictGlobalArticles(feeds.reversed())

        assertEquals(
            forward.flatMap { it.articles }.map { it.digest }.toSet(),
            reversed.flatMap { it.articles }.map { it.digest }.toSet(),
        )
    }

    @Test
    fun evictImagesDropsOrphansAndKeepsNewestWithinByteAndCountCaps() {
        val referenced = setOf(digest(1), digest(2))
        val images =
            listOf(
                CachedImage(digest = digest(1), bytes = ByteArray(10), cachedAtEpochMillis = 100L),
                CachedImage(digest = digest(2), bytes = ByteArray(10), cachedAtEpochMillis = 200L),
                CachedImage(digest = digest(3), bytes = ByteArray(10), cachedAtEpochMillis = 300L),
            )

        val evicted = evictImages(images, referenced)

        assertEquals(setOf(digest(1), digest(2)), evicted.map { it.digest }.toSet())
    }

    @Test
    fun evictImagesEnforcesGlobalByteBudgetNewestFirst() {
        val referenced = (0 until 3).map { digest(it) }.toSet()
        val halfBudget = (MAX_CACHED_IMAGE_BYTES_GLOBAL / 2).toInt()
        val images =
            listOf(
                CachedImage(digest(0), ByteArray(halfBudget), cachedAtEpochMillis = 1L),
                CachedImage(digest(1), ByteArray(halfBudget), cachedAtEpochMillis = 2L),
                CachedImage(digest(2), ByteArray(halfBudget), cachedAtEpochMillis = 3L),
            )

        val evicted = evictImages(images, referenced)

        assertEquals(setOf(digest(1), digest(2)), evicted.map { it.digest }.toSet())
    }

    @Test
    fun freshnessOfReportsFreshWithinWindowAndStaleAfter() {
        assertEquals(
            CacheFreshness.FRESH,
            freshnessOf(fetchedAtEpochMillis = 0L, nowEpochMillis = 1_000L, staleAfterMillis = 2_000L),
        )
        assertEquals(
            CacheFreshness.STALE,
            freshnessOf(fetchedAtEpochMillis = 0L, nowEpochMillis = 2_001L, staleAfterMillis = 2_000L),
        )
    }

    @Test
    fun advancesTheSnapshotRevisionForEveryWriteAndWrapsAtItsMaximumValue() {
        assertEquals(0L, nextFeedArticleCacheSnapshotRevision(null))
        assertEquals(1L, nextFeedArticleCacheSnapshotRevision(0L))
        assertEquals(0L, nextFeedArticleCacheSnapshotRevision(Long.MAX_VALUE))
    }

    private fun digest(seed: Int): String = seed.toString().padStart(64, '0')

    private fun article(
        digest: String,
        publishedAtEpochMillis: Long? = null,
        sourceOrder: Int = 0,
    ): CachedFeedArticle =
        CachedFeedArticle(
            digest = digest,
            title = "Title",
            publishedAtEpochMillis = publishedAtEpochMillis,
            sourceOrder = sourceOrder,
        )
}
