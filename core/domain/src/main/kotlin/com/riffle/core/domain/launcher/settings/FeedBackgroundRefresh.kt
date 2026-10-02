package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.rss.FeedRefreshFailure
import com.riffle.core.domain.launcher.rss.FeedRefreshOutcome

/** The periodic work the platform scheduler should hold for RSS feeds (issue #1393). */
data class FeedBackgroundSchedule(
    val intervalMinutes: Long,
    /** Unmetered network (Wi-Fi) rather than any connected network. */
    val requiresUnmeteredNetwork: Boolean,
    val requiresCharging: Boolean,
) {
    /**
     * The least time that must pass since a feed's last attempt before background work fetches it again.
     * Half the interval, so a late or doubled-up run can never fetch a feed much more often than the user
     * asked for, while a run that lands a little early still does useful work.
     */
    val minFeedGapMillis: Long get() = intervalMinutes * MILLIS_PER_MINUTE / 2

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

/** Why no periodic work should exist. */
enum class FeedBackgroundCancelReason {
    INTERVAL_OFF,
    NO_FEEDS,
    NO_ENABLED_FEEDS,
}

/** What to do with the unique periodic work given the current settings. */
sealed interface FeedBackgroundDecision {
    /** Enqueue, or replace in place, the unique periodic work. */
    data class Schedule(val schedule: FeedBackgroundSchedule) : FeedBackgroundDecision

    /** Cancel the unique periodic work if it exists. */
    data class Cancel(val reason: FeedBackgroundCancelReason) : FeedBackgroundDecision
}

/** Pure mapping from settings to the scheduling decision. No platform types, so it is JVM-testable. */
object FeedBackgroundScheduler {
    fun decide(settings: RssSettings): FeedBackgroundDecision =
        when {
            !settings.refreshInterval.isEnabled ->
                FeedBackgroundDecision.Cancel(FeedBackgroundCancelReason.INTERVAL_OFF)
            settings.feeds.isEmpty() -> FeedBackgroundDecision.Cancel(FeedBackgroundCancelReason.NO_FEEDS)
            settings.feeds.none { feed -> feed.enabled } ->
                FeedBackgroundDecision.Cancel(FeedBackgroundCancelReason.NO_ENABLED_FEEDS)
            else ->
                FeedBackgroundDecision.Schedule(
                    FeedBackgroundSchedule(
                        intervalMinutes = settings.refreshInterval.minutes.toLong(),
                        requiresUnmeteredNetwork = settings.backgroundWifiOnly,
                        requiresCharging = settings.backgroundChargingOnly,
                    ),
                )
        }
}

/** What the device looks like when a scheduled run actually starts; the platform constraints are only a hint. */
data class FeedBackgroundConditions(
    val meteredNetwork: Boolean,
    val batterySaver: Boolean,
)

enum class FeedBackgroundSkip {
    /** The setting changed (or was restored) after the work was queued. */
    NOT_ENABLED,
    METERED_NETWORK,
    BATTERY_SAVER,
}

/** Re-checks the opt-in and the user's constraints at run time, since queued work can outlive a setting. */
object FeedBackgroundRunGate {
    fun check(
        settings: RssSettings,
        conditions: FeedBackgroundConditions,
    ): FeedBackgroundSkip? =
        when {
            FeedBackgroundScheduler.decide(settings) !is FeedBackgroundDecision.Schedule ->
                FeedBackgroundSkip.NOT_ENABLED
            settings.backgroundWifiOnly && conditions.meteredNetwork -> FeedBackgroundSkip.METERED_NETWORK
            conditions.batterySaver -> FeedBackgroundSkip.BATTERY_SAVER
            else -> null
        }
}

/** What a finished background run reports to the platform scheduler. */
enum class FeedBackgroundRunResult {
    /** Done (including "nothing to do"); wait for the next period. */
    SUCCESS,

    /** Every attempted feed failed for a transient reason; ask for a backed-off retry. */
    RETRY,
}

/** Coarse record of the last background run for the settings status line. Carries no URLs or content. */
data class FeedBackgroundRunRecord(
    val atEpochMillis: Long,
    val kind: Kind,
) {
    enum class Kind {
        /** At least one feed stored new content. */
        UPDATED,

        /** Nothing new, or nothing was due. */
        UNCHANGED,

        /** Every attempted feed failed. */
        FAILED,

        /** Skipped by the run gate (setting off, metered network, battery saver). */
        SKIPPED,
    }
}

/** Pure decisions about a finished run's outcomes. */
object FeedBackgroundOutcomes {
    /** Retries stop after this many attempts in one period; the next period tries again. */
    const val MAX_RETRY_ATTEMPTS = 3

    private val TRANSIENT_FAILURES = setOf(FeedRefreshFailure.NETWORK, FeedRefreshFailure.TIMEOUT)

    fun kindOf(outcomes: Collection<FeedRefreshOutcome>): FeedBackgroundRunRecord.Kind =
        when {
            outcomes.isEmpty() -> FeedBackgroundRunRecord.Kind.UNCHANGED
            outcomes.any { it is FeedRefreshOutcome.Updated } -> FeedBackgroundRunRecord.Kind.UPDATED
            outcomes.all { it is FeedRefreshOutcome.Failed } -> FeedBackgroundRunRecord.Kind.FAILED
            else -> FeedBackgroundRunRecord.Kind.UNCHANGED
        }

    /**
     * Retry only when every attempted feed failed for a reason that a later attempt could fix (network or
     * timeout) and attempts remain. A malformed or oversize feed will not improve by retrying.
     */
    fun resultOf(
        outcomes: Collection<FeedRefreshOutcome>,
        attemptCount: Int,
    ): FeedBackgroundRunResult {
        val failures = outcomes.filterIsInstance<FeedRefreshOutcome.Failed>()
        val transient =
            outcomes.isNotEmpty() &&
                failures.size == outcomes.size &&
                failures.all { it.reason in TRANSIENT_FAILURES }
        return if (transient && attemptCount < MAX_RETRY_ATTEMPTS) {
            FeedBackgroundRunResult.RETRY
        } else {
            FeedBackgroundRunResult.SUCCESS
        }
    }
}
