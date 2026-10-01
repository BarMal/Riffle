package com.riffle.core.domain.launcher.rss

import com.riffle.core.domain.launcher.apps.AppProfileId
import java.net.URI

/** The feeds to fetch now (all user-triggered) and the ones deliberately left alone, with the reason. */
data class FeedRefreshPlan(
    val requests: List<FeedFetchRequest>,
    val skipped: Map<FeedId, FeedRefreshSkip>,
)

/**
 * Decides which configured feeds a user-triggered refresh may fetch. Pure: the caller supplies the clock and
 * the per-feed [FeedRefreshState]. Disabled, locked-profile and unsafe-host feeds never produce a request.
 * A minimum interval protects every feed from repeat taps; failed feeds also back off exponentially, but an
 * explicit "refresh this feed" request skips the backoff (not the minimum interval).
 */
class FeedRefreshPlanner(
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val baseBackoffMillis: Long = DEFAULT_BASE_BACKOFF_MILLIS,
    private val maxBackoffMillis: Long = DEFAULT_MAX_BACKOFF_MILLIS,
) {
    init {
        require(minIntervalMillis >= 0 && baseBackoffMillis > 0 && maxBackoffMillis >= baseBackoffMillis) {
            "Refresh intervals must be positive and ordered."
        }
    }

    fun plan(
        feeds: List<FeedConfiguration>,
        scope: FeedRefreshScope,
        states: Map<FeedId, FeedRefreshState>,
        profileStatuses: Map<AppProfileId, FeedProfileStatus>,
        nowEpochMillis: Long,
    ): FeedRefreshPlan {
        val targets =
            when (scope) {
                FeedRefreshScope.All -> feeds
                is FeedRefreshScope.One -> feeds.filter { it.id == scope.feedId }
            }
        val requests = mutableListOf<FeedFetchRequest>()
        val skipped = linkedMapOf<FeedId, FeedRefreshSkip>()
        for (feed in targets) {
            val state = states[feed.id] ?: FeedRefreshState()
            val skip = skipReason(feed, state, scope, profileStatuses, nowEpochMillis)
            if (skip == null) {
                requests += FeedFetchRequest(feed, state.validators, FeedRefreshTrigger.USER)
            } else {
                skipped[feed.id] = skip
            }
        }
        return FeedRefreshPlan(requests, skipped)
    }

    /** Delay after [consecutiveFailures] failures before an "all" refresh retries the feed. */
    fun backoffMillis(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return 0
        val shift = (consecutiveFailures - 1).coerceAtMost(MAX_BACKOFF_SHIFT)
        return (baseBackoffMillis shl shift).coerceAtMost(maxBackoffMillis)
    }

    private fun skipReason(
        feed: FeedConfiguration,
        state: FeedRefreshState,
        scope: FeedRefreshScope,
        profileStatuses: Map<AppProfileId, FeedProfileStatus>,
        nowEpochMillis: Long,
    ): FeedRefreshSkip? {
        return availabilitySkip(feed.availability(profileStatuses))
            ?: FeedRefreshSkip.UNSAFE_URL.takeUnless { FeedHostSafety.isPublicHost(hostOf(feed.url)) }
            ?: timingSkip(state, scope, nowEpochMillis)
    }

    private fun timingSkip(
        state: FeedRefreshState,
        scope: FeedRefreshScope,
        nowEpochMillis: Long,
    ): FeedRefreshSkip? {
        // A clock that moved backwards must not freeze the feed, so a negative elapsed time never skips.
        val elapsed = state.lastAttemptAtEpochMillis?.let { nowEpochMillis - it }?.takeIf { it >= 0 }
        return when {
            elapsed == null -> null
            elapsed < minIntervalMillis -> FeedRefreshSkip.TOO_SOON
            scope == FeedRefreshScope.All && elapsed < backoffMillis(state.consecutiveFailures) ->
                FeedRefreshSkip.BACKING_OFF
            else -> null
        }
    }

    private fun availabilitySkip(availability: FeedAvailability): FeedRefreshSkip? =
        when (availability) {
            FeedAvailability.ENABLED -> null
            FeedAvailability.DISABLED -> FeedRefreshSkip.DISABLED
            FeedAvailability.PROFILE_LOCKED -> FeedRefreshSkip.PROFILE_LOCKED
            FeedAvailability.PROFILE_REMOVED -> FeedRefreshSkip.PROFILE_REMOVED
        }

    private fun hostOf(url: FeedUrl): String = runCatching { URI(url.value).host }.getOrNull().orEmpty()

    companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS = 15_000L
        const val DEFAULT_BASE_BACKOFF_MILLIS = 60_000L
        const val DEFAULT_MAX_BACKOFF_MILLIS = 6L * 60 * 60 * 1000
        private const val MAX_BACKOFF_SHIFT = 20
    }
}
