package com.riffle.app.launcher.rss

import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.rss.ConfiguredFeedSource
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedFetchRequest
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedItem
import com.riffle.core.domain.launcher.rss.FeedItemIntentDigest
import com.riffle.core.domain.launcher.rss.FeedParser
import com.riffle.core.domain.launcher.rss.FeedProfileStatus
import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import com.riffle.core.domain.launcher.rss.FeedRefreshPlanner
import com.riffle.core.domain.launcher.rss.FeedRefreshScope
import com.riffle.core.domain.launcher.rss.FeedRefreshSkip
import com.riffle.core.domain.launcher.rss.FeedRefreshState
import com.riffle.core.domain.launcher.rss.FeedRefreshTrigger
import com.riffle.core.domain.launcher.rss.FeedTransport
import com.riffle.core.domain.launcher.rss.FeedTransportResult
import com.riffle.core.domain.launcher.rss.FeedValidators
import com.riffle.core.domain.launcher.rss.after
import com.riffle.core.domain.launcher.rss.toRefreshFailure
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunRecord
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** What a finished refresh did, per feed. Carries no URLs and no article content. */
data class FeedRefreshReport(
    val outcomes: Map<FeedId, FeedRefreshOutcome>,
    val skipped: Map<FeedId, FeedRefreshSkip>,
) {
    companion object {
        val EMPTY = FeedRefreshReport(emptyMap(), emptyMap())
    }
}

/** What a settings row shows for one feed. */
data class FeedRefreshStatus(
    val refreshing: Boolean,
    val lastUpdatedAtEpochMillis: Long?,
    val lastFailure: FeedRefreshFailure?,
)

/**
 * Notifies observers (the RSS source, settings rows) that something changed. Observers may be called from
 * any thread; this class is thread-safe.
 */
class FeedChangeSignal : SourceChangeSource {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override fun observe(onChanged: () -> Unit): () -> Unit {
        listeners += onChanged
        return { listeners -= onChanged }
    }

    fun notifyChanged() {
        listeners.forEach { listener -> listener() }
    }
}

/**
 * User-triggered feed refresh: plan, fetch, parse, store in the cache. Nothing here runs on its own: no
 * timers, no observers of settings or the cache; the network is touched only inside [refresh] /
 * [refreshBlocking], and [refresh] hops to [executor] first. Only one refresh runs at a time.
 *
 * Validators, backoff counters and last errors are kept in memory and persisted to [cache] after every run
 * (and merged back in before the next one), so they survive process death and are shared with the background
 * worker's own coordinator. Article content lives only in [cache]. Failures are reported as typed reasons,
 * never as URLs, response bodies or exception messages.
 */
@Suppress("TooManyFunctions") // One small public surface plus private run steps; splitting would scatter shared state.
class FeedRefreshCoordinator(
    private val transport: FeedTransport,
    private val parser: FeedParser,
    private val cache: FeedArticleCacheRepository,
    private val configuredFeeds: ConfiguredFeedSource,
    private val executor: Executor,
    private val profileStatuses: () -> Map<AppProfileId, FeedProfileStatus> = { emptyMap() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val planner = FeedRefreshPlanner()
    private val running = AtomicBoolean(false)
    private val lock = Any()
    private val states = HashMap<FeedId, FeedRefreshState>()
    private val inFlight = HashSet<FeedId>()

    /** Fires after the cache content changed; hand it to the RSS source so it can be `LIVE`. */
    val cacheChanges = FeedChangeSignal()

    /** Fires when progress or a per-feed status changed. */
    val statusChanges = FeedChangeSignal()

    /** True while a refresh is running. */
    val isRefreshing: Boolean get() = running.get()

    /**
     * Starts a refresh on [executor] and returns true, or returns false when one is already running.
     * [onComplete] runs on the executor thread, so a UI caller must hop to its own thread.
     */
    fun refresh(
        scope: FeedRefreshScope,
        onComplete: (FeedRefreshReport) -> Unit = {},
    ): Boolean {
        if (!running.compareAndSet(false, true)) return false
        executor.execute { onComplete(runAndRelease(scope, FeedRefreshTrigger.USER, 0)) }
        return true
    }

    /** Runs a refresh on the calling thread (which must not be the main thread); null when one is running. */
    fun refreshBlocking(scope: FeedRefreshScope): FeedRefreshReport? =
        if (running.compareAndSet(false, true)) runAndRelease(scope, FeedRefreshTrigger.USER, 0) else null

    /**
     * The background run: only due feeds, obeying backoff and [minFeedGapMillis]. Blocking, so call it from a
     * worker thread; null when a refresh is already running.
     */
    fun refreshScheduledBlocking(minFeedGapMillis: Long): FeedRefreshReport? =
        if (running.compareAndSet(false, true)) {
            runAndRelease(FeedRefreshScope.All, FeedRefreshTrigger.SCHEDULED, minFeedGapMillis)
        } else {
            null
        }

    /** The last background run, surviving restarts. Reads the cache: call off the main thread. */
    fun lastBackgroundRun(): FeedBackgroundRunRecord? = cache.loadBackgroundRun()

    /** In-memory status for a settings row; cheap and safe to call from any thread, including main. */
    fun statusOf(feedId: FeedId): FeedRefreshStatus =
        synchronized(lock) {
            val state = states[feedId]
            FeedRefreshStatus(
                refreshing = feedId in inFlight,
                lastUpdatedAtEpochMillis = state?.lastSuccessAtEpochMillis,
                lastFailure = state?.lastFailure,
            )
        }

    /** When [feedId] was last stored in the cache, surviving restarts. Reads the cache: call off the main thread. */
    fun lastCachedAtMillis(feedId: FeedId): Long? =
        (cache.loadFeed(feedId) as? FeedCacheResult.Available)?.snapshot?.feed?.fetchedAtEpochMillis

    private fun runAndRelease(
        scope: FeedRefreshScope,
        trigger: FeedRefreshTrigger,
        minGapMillis: Long,
    ): FeedRefreshReport =
        try {
            run(scope, trigger, minGapMillis)
        } finally {
            running.set(false)
            statusChanges.notifyChanged()
        }

    private fun run(
        scope: FeedRefreshScope,
        trigger: FeedRefreshTrigger,
        minGapMillis: Long,
    ): FeedRefreshReport {
        val feeds = configuredFeeds.configuredFeeds()
        synchronized(lock) { adoptNewerStates(states, cache.loadRefreshStates()) }
        val plan =
            planner.plan(
                feeds = feeds,
                scope = scope,
                states = usableStates(feeds),
                profileStatuses = profileStatuses(),
                nowEpochMillis = clock(),
                trigger = trigger,
                scheduledMinGapMillis = minGapMillis,
            )
        synchronized(lock) { inFlight += plan.requests.map { it.configuration.id } }
        statusChanges.notifyChanged()
        val outcomes = LinkedHashMap<FeedId, FeedRefreshOutcome>()
        for (request in plan.requests) {
            val id = request.configuration.id
            val outcome = refreshOne(request)
            outcomes[id] = outcome
            synchronized(lock) { inFlight -= id }
            if (outcome is FeedRefreshOutcome.Updated) {
                cacheChanges.notifyChanged()
            }
            statusChanges.notifyChanged()
        }
        persistStates(outcomes.keys)
        return FeedRefreshReport(outcomes, plan.skipped)
    }

    private fun persistStates(attempted: Set<FeedId>) {
        if (attempted.isEmpty()) return
        val snapshot = synchronized(lock) { states.filterKeys { it in attempted } }
        cache.saveRefreshStates(snapshot)
    }

    /** Validators are only honoured while the cache still holds the content they describe. */
    private fun usableStates(feeds: List<FeedConfiguration>): Map<FeedId, FeedRefreshState> =
        synchronized(lock) { HashMap(states) }.let { snapshot ->
            feeds.associate { feed ->
                val state = snapshot[feed.id] ?: FeedRefreshState()
                feed.id to
                    if (cache.loadFeed(feed.id) is FeedCacheResult.Empty) state.copy(validators = null) else state
            }
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun refreshOne(request: FeedFetchRequest): FeedRefreshOutcome {
        var validators: FeedValidators? = null
        val outcome =
            try {
                when (val result = transport.fetch(request)) {
                    is FeedTransportResult.Content -> {
                        validators = result.validators
                        store(request.configuration, result.body)
                    }
                    is FeedTransportResult.NotModified -> {
                        validators = result.validators
                        FeedRefreshOutcome.NotModified
                    }
                    is FeedTransportResult.Failure -> FeedRefreshOutcome.Failed(result.error.toRefreshFailure())
                }
            } catch (ignored: RuntimeException) {
                // The exception is deliberately dropped: its message could echo a URL.
                FeedRefreshOutcome.Failed(FeedRefreshFailure.NETWORK)
            }
        val failed = outcome is FeedRefreshOutcome.Failed
        synchronized(lock) {
            val id = request.configuration.id
            states[id] = (states[id] ?: FeedRefreshState()).after(outcome, clock(), validators.takeUnless { failed })
        }
        return outcome
    }

    private fun store(
        feed: FeedConfiguration,
        body: String,
    ): FeedRefreshOutcome {
        val parsed = parser.parse(body).getOrNull() ?: return FeedRefreshOutcome.Failed(FeedRefreshFailure.MALFORMED)
        val articles = parsed.items.map { item -> item.toCached(feed) }
        val previous = previousDigests(feed.id)
        // An empty parse of a feed that had content is treated as malformed rather than wiping good cache.
        return if (articles.isEmpty() && previous.isNotEmpty()) {
            FeedRefreshOutcome.Failed(FeedRefreshFailure.MALFORMED)
        } else {
            cache.replaceFeed(feed.id, articles)
            FeedRefreshOutcome.Updated(
                newArticles = articles.count { it.digest !in previous },
                totalArticles = articles.size.coerceAtMost(MAX_CACHED_ARTICLES_PER_FEED),
            )
        }
    }

    private fun previousDigests(feedId: FeedId): Set<String> =
        (cache.loadFeed(feedId, Long.MAX_VALUE) as? FeedCacheResult.Available)
            ?.snapshot?.feed?.articles?.map(CachedFeedArticle::digest)?.toSet().orEmpty()
}

private fun FeedItem.toCached(feed: FeedConfiguration) =
    CachedFeedArticle(
        digest = FeedItemIntentDigest.forItem(feed, this).value,
        title = title,
        author = author,
        publishedAtEpochMillis = publishedAt?.toEpochMilli(),
        summary = summary,
        canonicalUrl = canonicalUrl,
        imageUrl = imageUrl,
        sourceOrder = sourceOrder,
    )
