package com.riffle.app.launcher.ics

import com.riffle.app.launcher.rss.FeedChangeSignal
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedFetchRequest
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome
import com.riffle.core.domain.launcher.rss.FeedRefreshPlanner
import com.riffle.core.domain.launcher.rss.FeedRefreshScope
import com.riffle.core.domain.launcher.rss.FeedRefreshSkip
import com.riffle.core.domain.launcher.rss.FeedRefreshState
import com.riffle.core.domain.launcher.rss.FeedTransport
import com.riffle.core.domain.launcher.rss.FeedTransportResult
import com.riffle.core.domain.launcher.rss.FeedValidators
import com.riffle.core.domain.launcher.rss.after
import com.riffle.core.domain.launcher.rss.toRefreshFailure
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEngine
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEventPruner
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsParseFailure
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsParseResult
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** What a finished refresh did, per feed. Carries no URLs and no calendar content. */
internal data class IcsRefreshReport(
    val outcomes: Map<IcsFeedId, FeedRefreshOutcome>,
    val skipped: Map<IcsFeedId, FeedRefreshSkip>,
) {
    companion object {
        val EMPTY = IcsRefreshReport(emptyMap(), emptyMap())
    }
}

/** What a settings row shows for one feed. */
internal data class IcsRefreshStatus(
    val refreshing: Boolean,
    val lastUpdatedAtEpochMillis: Long?,
    val lastFailure: FeedRefreshFailure?,
)

/**
 * User-triggered ICS feed refresh, on the same rules as the RSS one (`FeedRefreshPlanner`: https and public
 * host only, a minimum interval, backoff, conditional requests): fetch on [executor], parse with [engine],
 * keep only the pruned parsed events in [repository], and drop the body. Nothing here runs on its own: no
 * timers and no observers; the network is touched only inside [refresh] or [refreshBlocking]. One refresh
 * runs at a time.
 *
 * Validators, backoff counters and last errors are in memory only. Failures are typed reasons, never URLs,
 * response bodies or exception messages.
 */
internal class IcsRefreshCoordinator(
    private val transport: FeedTransport,
    private val engine: IcsEngine,
    private val repository: CachedIcsFeedRepository,
    private val executor: Executor,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val planner = FeedRefreshPlanner()
    private val running = AtomicBoolean(false)
    private val lock = Any()
    private val states = HashMap<IcsFeedId, FeedRefreshState>()
    private val inFlight = HashSet<IcsFeedId>()

    /** Fires when progress or a per-feed status changed. */
    val statusChanges = FeedChangeSignal()

    val isRefreshing: Boolean get() = running.get()

    /** Starts a refresh on [executor] and returns true, or false when one is already running. */
    fun refresh(
        scope: IcsRefreshScope = IcsRefreshScope.All,
        onComplete: (IcsRefreshReport) -> Unit = {},
    ): Boolean {
        if (!running.compareAndSet(false, true)) return false
        statusChanges.notifyChanged()
        executor.execute { onComplete(runAndRelease(scope)) }
        return true
    }

    /** Runs a refresh on the calling thread (never the main thread); null when one is running. */
    fun refreshBlocking(scope: IcsRefreshScope = IcsRefreshScope.All): IcsRefreshReport? =
        if (running.compareAndSet(false, true)) runAndRelease(scope) else null

    fun statusOf(feedId: IcsFeedId): IcsRefreshStatus =
        synchronized(lock) {
            val state = states[feedId]
            IcsRefreshStatus(
                refreshing = feedId in inFlight,
                lastUpdatedAtEpochMillis = repository.cached(feedId)?.fetchedAtEpochMillis,
                lastFailure = state?.lastFailure,
            )
        }

    private fun runAndRelease(scope: IcsRefreshScope): IcsRefreshReport =
        try {
            run(scope)
        } finally {
            running.set(false)
            statusChanges.notifyChanged()
        }

    private fun run(scope: IcsRefreshScope): IcsRefreshReport {
        val feeds = repository.settings().feeds
        val configurations = feeds.associate { feed -> FeedId(feed.id.value) to feed }
        val plan =
            planner.plan(
                feeds = feeds.map { it.toConfiguration() },
                scope = scope.toFeedScope(),
                states = usableStates(feeds),
                profileStatuses = emptyMap(),
                nowEpochMillis = clock(),
            )
        synchronized(lock) { inFlight += plan.requests.map { IcsFeedId(it.configuration.id.value) } }
        statusChanges.notifyChanged()
        val outcomes = LinkedHashMap<IcsFeedId, FeedRefreshOutcome>()
        for (request in plan.requests) {
            val id = IcsFeedId(request.configuration.id.value)
            outcomes[id] = refreshOne(request, configurations.getValue(request.configuration.id))
            synchronized(lock) { inFlight -= id }
            statusChanges.notifyChanged()
        }
        return IcsRefreshReport(outcomes, plan.skipped.mapKeys { IcsFeedId(it.key.value) })
    }

    /** Validators are only honoured while the cache still holds the content they describe. */
    private fun usableStates(feeds: List<IcsFeed>): Map<FeedId, FeedRefreshState> =
        synchronized(lock) { HashMap(states) }.let { snapshot ->
            feeds.associate { feed ->
                val state = snapshot[feed.id] ?: FeedRefreshState()
                val usable = if (repository.cached(feed.id) == null) state.copy(validators = null) else state
                FeedId(feed.id.value) to usable
            }
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun refreshOne(
        request: FeedFetchRequest,
        feed: IcsFeed,
    ): FeedRefreshOutcome {
        var validators: FeedValidators? = null
        val outcome =
            try {
                when (val result = transport.fetch(request)) {
                    is FeedTransportResult.Content -> {
                        validators = result.validators
                        store(feed.id, result.body)
                    }
                    is FeedTransportResult.NotModified -> {
                        validators = result.validators
                        FeedRefreshOutcome.NotModified
                    }
                    is FeedTransportResult.Failure -> FeedRefreshOutcome.Failed(result.error.toRefreshFailure())
                }
            } catch (ignored: RuntimeException) {
                // Deliberately dropped: the message could echo the secret URL.
                FeedRefreshOutcome.Failed(FeedRefreshFailure.NETWORK)
            }
        val failed = outcome is FeedRefreshOutcome.Failed
        synchronized(lock) {
            val key = feed.id
            states[key] = (states[key] ?: FeedRefreshState()).after(outcome, clock(), validators.takeUnless { failed })
        }
        return outcome
    }

    private fun store(
        id: IcsFeedId,
        body: String,
    ): FeedRefreshOutcome {
        val deviceZone = zone()
        val now = clock()
        val parsed =
            when (val result = engine.parse(body, deviceZone)) {
                is IcsParseResult.Failed -> return FeedRefreshOutcome.Failed(result.reason.toFailure())
                is IcsParseResult.Ok -> result
            }
        val events = IcsEventPruner.prune(parsed.events, Instant.ofEpochMilli(now), deviceZone)
        val previous = repository.cached(id)?.events?.size ?: 0
        // A feed that parses to nothing does not wipe content we already hold.
        return if (events.isEmpty() && previous > 0) {
            FeedRefreshOutcome.Failed(FeedRefreshFailure.MALFORMED)
        } else {
            repository.replaceCache(id, CachedIcsFeed(events, now))
            FeedRefreshOutcome.Updated(
                newArticles = (events.size - previous).coerceAtLeast(0),
                totalArticles = events.size,
            )
        }
    }

    private fun IcsParseFailure.toFailure(): FeedRefreshFailure =
        when (this) {
            IcsParseFailure.TOO_LARGE -> FeedRefreshFailure.OVERSIZE
            IcsParseFailure.NOT_CALENDAR -> FeedRefreshFailure.MALFORMED
        }
}

/** What the user asked to refresh. */
internal sealed interface IcsRefreshScope {
    data object All : IcsRefreshScope

    data class One(val feedId: IcsFeedId) : IcsRefreshScope
}

private fun IcsRefreshScope.toFeedScope(): FeedRefreshScope =
    when (this) {
        IcsRefreshScope.All -> FeedRefreshScope.All
        is IcsRefreshScope.One -> FeedRefreshScope.One(FeedId(feedId.value))
    }

/** The planner's view of a feed: only the id, URL and enabled flag matter. */
private fun IcsFeed.toConfiguration(): FeedConfiguration =
    FeedConfiguration(id = FeedId(id.value), url = url, enabled = enabled)
