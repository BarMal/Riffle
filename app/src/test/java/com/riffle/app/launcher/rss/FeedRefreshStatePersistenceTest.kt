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
import com.riffle.core.domain.launcher.rss.FeedRefreshState
import com.riffle.core.domain.launcher.rss.FeedRefreshTrigger
import com.riffle.core.domain.launcher.rss.FeedSourceError
import com.riffle.core.domain.launcher.rss.FeedTransport
import com.riffle.core.domain.launcher.rss.FeedTransportResult
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.rss.FeedValidators
import com.riffle.core.domain.launcher.rss.NormalizedFeed
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunRecord
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Validators, failures and the last background run survive process death (issue #1393). */
class FeedRefreshStatePersistenceTest {
    private val feedA = FeedConfiguration(FeedId("a"), FeedUrl.parse("https://example.com/a.xml").getOrThrow())
    private var now = 1_000_000L
    private val store = PersistingCache()
    private val calls = mutableListOf<FeedFetchRequest>()
    private var result: FeedTransportResult = FeedTransportResult.Content("body", FeedValidators("e1", "Mon"))

    private fun newCoordinator() =
        FeedRefreshCoordinator(
            transport =
                FeedTransport { request ->
                    calls += request
                    result
                },
            parser = FeedParser { Result.success(normalized()) },
            cache = store,
            configuredFeeds = ConfiguredFeedSource { listOf(feedA) },
            executor = { task -> task.run() },
            clock = { now },
        )

    private fun normalized() =
        NormalizedFeed(
            format = FeedFormat.RSS_2,
            title = "t",
            items = listOf(FeedItem("id-1", null, "Title", null, Instant.ofEpochSecond(1), null, null, 1)),
        )

    @Test
    fun validatorsSurviveProcessDeathBecauseANewCoordinatorReadsThemBack() {
        newCoordinator().refreshBlocking(FeedRefreshScope.All)
        now += 120_000
        result = FeedTransportResult.NotModified(FeedValidators())

        val afterRestart = newCoordinator()
        afterRestart.refreshBlocking(FeedRefreshScope.All)

        assertEquals(FeedValidators("e1", "Mon"), calls.last().validators)
    }

    @Test
    fun backoffSurvivesProcessDeath() {
        result = FeedTransportResult.Failure(FeedSourceError.NETWORK)
        newCoordinator().refreshBlocking(FeedRefreshScope.All)
        now += 20_000

        val report = newCoordinator().refreshScheduledBlocking(minFeedGapMillis = 0)!!

        assertEquals(FeedRefreshSkip.BACKING_OFF, report.skipped[feedA.id])
        assertEquals(1, calls.size)
    }

    @Test
    fun scheduledRunsSendTheScheduledTriggerAndHonourTheMinimumGap() {
        val coordinator = newCoordinator()
        coordinator.refreshBlocking(FeedRefreshScope.All)
        now += 600_000

        val tooSoon = coordinator.refreshScheduledBlocking(minFeedGapMillis = 30 * 60_000)!!
        now += 31 * 60_000
        coordinator.refreshScheduledBlocking(minFeedGapMillis = 30 * 60_000)

        assertEquals(FeedRefreshSkip.TOO_SOON, tooSoon.skipped[feedA.id])
        assertEquals(listOf(FeedRefreshTrigger.USER, FeedRefreshTrigger.SCHEDULED), calls.map { it.trigger })
    }

    @Test
    fun aSecondCoordinatorAdoptsNewerPersistedStateFromTheBackgroundWorker() {
        val app = newCoordinator()
        app.refreshBlocking(FeedRefreshScope.All)
        // The worker's own coordinator later refreshed the feed and stored newer state.
        store.saveRefreshStates(
            mapOf(feedA.id to FeedRefreshState(lastAttemptAtEpochMillis = now + 500_000, lastSuccessAtEpochMillis = 1)),
        )
        now += 501_000

        val report = app.refreshBlocking(FeedRefreshScope.All)!!

        assertEquals(FeedRefreshSkip.TOO_SOON, report.skipped[feedA.id])
    }

    @Test
    fun failuresArePersistedAsTypedReasonsOnly() {
        result = FeedTransportResult.Failure(FeedSourceError.TIMEOUT)
        newCoordinator().refreshBlocking(FeedRefreshScope.All)

        val saved = store.loadRefreshStates().getValue(feedA.id)

        assertEquals(FeedRefreshFailure.TIMEOUT, saved.lastFailure)
        assertEquals(1, saved.consecutiveFailures)
        assertFalse(encodeRefreshStates(store.loadRefreshStates()).toString().contains("example.com"))
    }

    @Test
    fun nothingIsPersistedWhenNothingWasAttempted() {
        newCoordinator().refreshBlocking(FeedRefreshScope.All)
        store.saves = 0
        now += 1_000

        newCoordinator().refreshBlocking(FeedRefreshScope.All)

        assertEquals(0, store.saves)
    }

    @Test
    fun theBackgroundRunRecordRoundTripsThroughTheCache() {
        val record = FeedBackgroundRunRecord(123L, FeedBackgroundRunRecord.Kind.UPDATED)
        store.saveBackgroundRun(record)

        assertEquals(record, newCoordinator().lastBackgroundRun())
    }

    @Test
    fun statesRoundTripThroughTheCacheDocumentJson() {
        val state =
            FeedRefreshState(
                lastAttemptAtEpochMillis = 10,
                lastSuccessAtEpochMillis = 5,
                consecutiveFailures = 3,
                lastFailure = FeedRefreshFailure.BAD_STATUS,
                validators = FeedValidators("\"abc\"", "Wed, 01 Jan 2025 00:00:00 GMT"),
            )
        val document =
            FeedArticleCacheDocument(
                refreshStates = mapOf(feedA.id to state),
                backgroundRun = FeedBackgroundRunRecord(9, FeedBackgroundRunRecord.Kind.FAILED),
            )

        val decoded = decodeFeedArticleCacheDocument(encodeFeedArticleCacheDocument(document))!!

        assertEquals(document, decoded)
    }

    @Test
    fun aDocumentWrittenBeforeThisChangeDecodesWithEmptyState() {
        val old = JSONObject().put("version", CURRENT_FEED_ARTICLE_CACHE_VERSION).toString()

        val decoded = decodeFeedArticleCacheDocument(old)!!

        assertTrue(decoded.refreshStates.isEmpty())
        assertNull(decoded.backgroundRun)
    }

    @Test
    fun decodeDropsMalformedEntriesClampsNumbersAndIgnoresUnknownEnums() {
        val array =
            JSONArray()
                .put("not an object")
                .put(JSONObject().put("failures", 3))
                .put(
                    JSONObject()
                        .put("feedId", "ok")
                        .put("failures", 9_999)
                        .put("lastAttemptAt", -5)
                        .put("lastFailure", "NOT_A_REASON")
                        .put("etag", "x".repeat(MAX_PERSISTED_VALIDATOR_LENGTH + 1))
                        .put("lastModified", "Mon"),
                )

        val decoded = decodeRefreshStates(array)

        val state = decoded.getValue(FeedId("ok"))
        assertEquals(1, decoded.size)
        assertEquals(MAX_PERSISTED_CONSECUTIVE_FAILURES, state.consecutiveFailures)
        assertNull(state.lastAttemptAtEpochMillis)
        assertNull(state.lastFailure)
        assertEquals(FeedValidators(etag = null, lastModified = "Mon"), state.validators)
    }

    @Test
    fun decodeAndMergeAreBoundedToTheConfigurableFeedCount() {
        val many = (0 until MAX_PERSISTED_REFRESH_STATES + 20).associate { FeedId("f$it") to FeedRefreshState() }

        assertEquals(MAX_PERSISTED_REFRESH_STATES, encodeRefreshStates(many).length())
        assertEquals(MAX_PERSISTED_REFRESH_STATES, mergeRefreshStates(emptyMap(), many).size)
        val merged = mergeRefreshStates(mapOf(FeedId("old") to FeedRefreshState()), many)
        assertFalse(FeedId("old") in merged)
        assertTrue(FeedId("f${MAX_PERSISTED_REFRESH_STATES + 19}") in merged)
    }

    @Test
    fun corruptBackgroundRunDecodesToNull() {
        assertNull(decodeBackgroundRun(JSONObject().put("at", 5).put("kind", "NOPE")))
        assertNull(decodeBackgroundRun(JSONObject().put("kind", "FAILED")))
        assertNull(decodeBackgroundRun(null))
    }

    @Test
    fun failedOutcomeTypeIsStillReportedNotThrown() {
        result = FeedTransportResult.Failure(FeedSourceError.HTTP)

        val report = newCoordinator().refreshBlocking(FeedRefreshScope.All)!!

        assertEquals(FeedRefreshOutcome.Failed(FeedRefreshFailure.BAD_STATUS), report.outcomes[feedA.id])
    }
}

private class PersistingCache : FeedArticleCacheRepository by NoopFeedArticleCacheRepository {
    private val feeds = HashMap<FeedId, List<CachedFeedArticle>>()
    private var states = emptyMap<FeedId, FeedRefreshState>()
    private var run: FeedBackgroundRunRecord? = null
    var saves = 0

    override fun loadFeed(
        feedId: FeedId,
        staleAfterMillis: Long,
    ): FeedCacheResult =
        feeds[feedId]?.let { articles ->
            FeedCacheResult.Available(
                CachedFeedSnapshot(CachedFeed(feedId, articles, 777L), CacheFreshness.FRESH),
            )
        } ?: FeedCacheResult.Empty

    override fun replaceFeed(
        feedId: FeedId,
        articles: List<CachedFeedArticle>,
    ) {
        feeds[feedId] = boundFeedArticles(articles)
    }

    override fun loadRefreshStates(): Map<FeedId, FeedRefreshState> = states

    override fun saveRefreshStates(states: Map<FeedId, FeedRefreshState>) {
        saves++
        this.states = mergeRefreshStates(this.states, states)
    }

    override fun loadBackgroundRun(): FeedBackgroundRunRecord? = run

    override fun saveBackgroundRun(record: FeedBackgroundRunRecord) {
        run = record
    }
}
