package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.ConfiguredFeedSource
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedFetchRequest
import com.riffle.core.domain.launcher.rss.FeedFormat
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedItem
import com.riffle.core.domain.launcher.rss.FeedParser
import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import com.riffle.core.domain.launcher.rss.FeedRefreshScope
import com.riffle.core.domain.launcher.rss.FeedRefreshSkip
import com.riffle.core.domain.launcher.rss.FeedSourceError
import com.riffle.core.domain.launcher.rss.FeedTransport
import com.riffle.core.domain.launcher.rss.FeedTransportResult
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.rss.FeedValidators
import com.riffle.core.domain.launcher.rss.NormalizedFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Executor

class FeedRefreshCoordinatorTest {
    private val feedA = feed("a")
    private var feeds = listOf(feedA)
    private var now = 1_000_000L
    private val cache = InMemoryFeedCache()
    private val calls = mutableListOf<FeedFetchRequest>()
    private var transportResult: () -> FeedTransportResult = {
        FeedTransportResult.Content(
            "body",
            FeedValidators("e1"),
        )
    }
    private var parsed: Result<NormalizedFeed> = Result.success(normalized(1, 2))
    private val transport =
        FeedTransport { request ->
            calls += request
            transportResult()
        }
    private val coordinator =
        FeedRefreshCoordinator(
            transport = transport,
            parser = FeedParser { parsed },
            cache = cache,
            configuredFeeds = ConfiguredFeedSource { feeds },
            executor = Executor { task -> task.run() },
            clock = { now },
        )

    @Test
    fun nothingTouchesTheNetworkUnlessTheUserTriggersARefresh() {
        coordinator.statusOf(feedA.id)
        coordinator.cacheChanges.observe {}
        coordinator.statusChanges.observe {}
        cache.loadFeed(feedA.id)

        assertTrue(calls.isEmpty())
        assertFalse(coordinator.isRefreshing)
    }

    @Test
    fun successStoresArticlesCountsNewOnesAndNotifiesTheCache() {
        var cacheChanges = 0
        coordinator.cacheChanges.observe { cacheChanges++ }

        val report = coordinator.refreshBlocking(FeedRefreshScope.All)!!

        assertEquals(FeedRefreshOutcome.Updated(newArticles = 2, totalArticles = 2), report.outcomes[feedA.id])
        assertEquals(2, cache.articles(feedA.id).size)
        assertEquals(1, cacheChanges)

        now += 60_000
        parsed = Result.success(normalized(1, 3))
        val second = coordinator.refreshBlocking(FeedRefreshScope.All)!!
        assertEquals(FeedRefreshOutcome.Updated(newArticles = 1, totalArticles = 3), second.outcomes[feedA.id])
    }

    @Test
    fun secondRefreshSendsValidatorsAndNotModifiedKeepsTheCacheWithoutNotifying() {
        var cacheChanges = 0
        coordinator.refreshBlocking(FeedRefreshScope.All)
        coordinator.cacheChanges.observe { cacheChanges++ }
        now += 60_000
        transportResult = { FeedTransportResult.NotModified(FeedValidators()) }

        val report = coordinator.refreshBlocking(FeedRefreshScope.All)!!

        assertEquals(FeedValidators("e1"), calls.last().validators)
        assertEquals(FeedRefreshOutcome.NotModified, report.outcomes[feedA.id])
        assertEquals(2, cache.articles(feedA.id).size)
        assertEquals(0, cacheChanges)
    }

    @Test
    fun validatorsAreDroppedWhenTheCacheNoLongerHoldsTheContent() {
        coordinator.refreshBlocking(FeedRefreshScope.All)
        cache.clearFeed(feedA.id)
        now += 60_000

        coordinator.refreshBlocking(FeedRefreshScope.All)

        assertNull(calls.last().validators)
    }

    @Test
    fun transportFailuresAreTypedAndLeaveTheCacheUntouched() {
        coordinator.refreshBlocking(FeedRefreshScope.All)
        val expected =
            mapOf(
                FeedSourceError.NETWORK to FeedRefreshFailure.NETWORK,
                FeedSourceError.TIMEOUT to FeedRefreshFailure.TIMEOUT,
                FeedSourceError.HTTP to FeedRefreshFailure.BAD_STATUS,
                FeedSourceError.RESPONSE_TOO_LARGE to FeedRefreshFailure.OVERSIZE,
                FeedSourceError.INVALID_ENCODING to FeedRefreshFailure.MALFORMED,
                FeedSourceError.INVALID_REDIRECT to FeedRefreshFailure.UNSAFE_URL,
                FeedSourceError.REDIRECT_LIMIT to FeedRefreshFailure.UNSAFE_URL,
            )
        expected.forEach { (error, reason) ->
            now += 24L * 60 * 60 * 1000
            transportResult = { FeedTransportResult.Failure(error) }

            val report = coordinator.refreshBlocking(FeedRefreshScope.All)!!

            assertEquals(FeedRefreshOutcome.Failed(reason), report.outcomes[feedA.id])
            assertEquals(2, cache.articles(feedA.id).size)
            assertEquals(reason, coordinator.statusOf(feedA.id).lastFailure)
        }
    }

    @Test
    fun malformedBodiesAndEmptyParsesDoNotReplaceGoodCache() {
        coordinator.refreshBlocking(FeedRefreshScope.All)

        now += 60_000
        parsed = Result.failure(IllegalArgumentException("https://secret.example/?token=abc"))
        val malformed = coordinator.refreshBlocking(FeedRefreshScope.All)!!
        assertEquals(FeedRefreshOutcome.Failed(FeedRefreshFailure.MALFORMED), malformed.outcomes[feedA.id])

        now += 24L * 60 * 60 * 1000
        parsed = Result.success(normalized(1, 0))
        val empty = coordinator.refreshBlocking(FeedRefreshScope.All)!!
        assertEquals(FeedRefreshOutcome.Failed(FeedRefreshFailure.MALFORMED), empty.outcomes[feedA.id])
        assertEquals(2, cache.articles(feedA.id).size)
    }

    @Test
    fun articleCountIsCappedPerFeed() {
        parsed = Result.success(normalized(1, MAX_CACHED_ARTICLES_PER_FEED + 50))

        val report = coordinator.refreshBlocking(FeedRefreshScope.All)!!

        val updated = report.outcomes[feedA.id] as FeedRefreshOutcome.Updated
        assertEquals(MAX_CACHED_ARTICLES_PER_FEED, updated.totalArticles)
        assertEquals(MAX_CACHED_ARTICLES_PER_FEED, cache.articles(feedA.id).size)
    }

    @Test
    fun transportExceptionsBecomeNetworkFailuresWithoutLeakingTheMessage() {
        transportResult = { throw IllegalStateException("https://secret.example/?token=abc") }

        val report = coordinator.refreshBlocking(FeedRefreshScope.All)!!

        assertEquals(FeedRefreshOutcome.Failed(FeedRefreshFailure.NETWORK), report.outcomes[feedA.id])
        assertFalse(report.toString().contains("secret"))
    }

    @Test
    fun disabledFeedsAndRepeatedTapsDoNotFetch() {
        feeds = listOf(feed("off", enabled = false))
        val disabled = coordinator.refreshBlocking(FeedRefreshScope.All)!!
        assertEquals(FeedRefreshSkip.DISABLED, disabled.skipped[FeedId("off")])
        assertTrue(calls.isEmpty())

        feeds = listOf(feedA)
        coordinator.refreshBlocking(FeedRefreshScope.All)
        val again = coordinator.refreshBlocking(FeedRefreshScope.All)!!
        assertEquals(FeedRefreshSkip.TOO_SOON, again.skipped[feedA.id])
        assertEquals(1, calls.size)
    }

    @Test
    fun onlyOneRefreshRunsAtATimeAndItRunsOnTheExecutor() {
        val queued = mutableListOf<Runnable>()
        val queuedCoordinator =
            FeedRefreshCoordinator(
                transport = transport,
                parser = FeedParser { parsed },
                cache = cache,
                configuredFeeds = ConfiguredFeedSource { feeds },
                executor = Executor { task -> queued += task },
                clock = { now },
            )
        var report: FeedRefreshReport? = null

        assertTrue(queuedCoordinator.refresh(FeedRefreshScope.All) { report = it })
        assertFalse(queuedCoordinator.refresh(FeedRefreshScope.All))
        assertTrue(calls.isEmpty())
        assertTrue(queuedCoordinator.isRefreshing)

        queued.single().run()

        assertEquals(1, calls.size)
        assertFalse(queuedCoordinator.isRefreshing)
        assertEquals(2, (report!!.outcomes[feedA.id] as FeedRefreshOutcome.Updated).newArticles)
    }

    @Test
    fun statusIsInMemoryAndTheCacheTimeSurvivesRestarts() {
        assertNull(coordinator.statusOf(feedA.id).lastUpdatedAtEpochMillis)
        assertNull(coordinator.lastCachedAtMillis(feedA.id))
        cache.replaceFeed(feedA.id, listOf(cached(1)))

        assertEquals(cache.fetchedAt, coordinator.lastCachedAtMillis(feedA.id))
        assertNull(coordinator.statusOf(feedA.id).lastUpdatedAtEpochMillis)

        coordinator.refreshBlocking(FeedRefreshScope.All)
        assertEquals(now, coordinator.statusOf(feedA.id).lastUpdatedAtEpochMillis)
    }

    private fun feed(
        id: String,
        enabled: Boolean = true,
    ) = FeedConfiguration(FeedId(id), FeedUrl.parse("https://example.com/$id.xml").getOrThrow(), enabled = enabled)

    private fun normalized(
        from: Int,
        to: Int,
    ) = NormalizedFeed(
        format = FeedFormat.RSS_2,
        title = "t",
        items =
            (from..to).map { n ->
                FeedItem("id-$n", null, "Title $n", null, Instant.ofEpochSecond(1_000L - n), null, null, n)
            },
    )

    private fun cached(n: Int) =
        CachedFeedArticle(digest = n.toString().padStart(64, '0'), title = "t", sourceOrder = n)
}

private class InMemoryFeedCache : FeedArticleCacheRepository by NoopFeedArticleCacheRepository {
    val fetchedAt = 777L
    private val feeds = HashMap<FeedId, List<CachedFeedArticle>>()

    fun articles(feedId: FeedId): List<CachedFeedArticle> = feeds[feedId].orEmpty()

    override fun loadFeed(
        feedId: FeedId,
        staleAfterMillis: Long,
    ): FeedCacheResult =
        feeds[feedId]?.let { articles ->
            FeedCacheResult.Available(
                CachedFeedSnapshot(CachedFeed(feedId, articles, fetchedAt), CacheFreshness.FRESH),
            )
        } ?: FeedCacheResult.Empty

    override fun replaceFeed(
        feedId: FeedId,
        articles: List<CachedFeedArticle>,
    ) {
        feeds[feedId] = boundFeedArticles(articles)
    }

    override fun clearFeed(feedId: FeedId) {
        feeds.remove(feedId)
    }
}
