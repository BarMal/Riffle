package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.settings.FeedBackgroundConditions
import com.riffle.core.domain.launcher.settings.FeedBackgroundDecision
import com.riffle.core.domain.launcher.settings.FeedBackgroundOutcomes
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunGate
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunRecord
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunResult
import com.riffle.core.domain.launcher.settings.FeedBackgroundSchedule
import com.riffle.core.domain.launcher.settings.FeedBackgroundScheduler
import com.riffle.core.domain.launcher.settings.RssSettings

/**
 * The Compose-free, WorkManager-free body of one background refresh run (issue #1393). The worker is a thin
 * adapter over this class so every decision is unit-testable with fakes:
 *
 * 1. Re-read the settings; the run does nothing unless the user is still opted in with enabled feeds.
 * 2. Re-check the device conditions the user asked for (Wi-Fi only) plus battery saver.
 * 3. Hand over to the shared refresh coordinator, which owns host safety, minimum gaps, backoff, caps and all
 *    HTTP. This class never touches the network itself.
 * 4. Record a coarse result for the settings status line and tell the scheduler whether to retry.
 *
 * Nothing here logs, and nothing carries URLs or article content.
 */
class FeedBackgroundRefreshRunner(
    private val settings: () -> RssSettings?,
    private val conditions: () -> FeedBackgroundConditions,
    private val refreshScheduled: (minFeedGapMillis: Long) -> FeedRefreshReport?,
    private val recordRun: (FeedBackgroundRunRecord) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun run(attemptCount: Int): FeedBackgroundRunResult {
        val current = settings()
        // Stale work after the setting went off (or unreadable settings) is not worth a status line.
        val schedule = current?.let(::scheduleOf)
        return when {
            current == null || schedule == null -> FeedBackgroundRunResult.SUCCESS
            FeedBackgroundRunGate.check(current, conditions()) != null -> {
                record(FeedBackgroundRunRecord.Kind.SKIPPED)
                FeedBackgroundRunResult.SUCCESS
            }
            else -> refreshAndRecord(schedule, attemptCount)
        }
    }

    private fun refreshAndRecord(
        schedule: FeedBackgroundSchedule,
        attemptCount: Int,
    ): FeedBackgroundRunResult {
        // Null means a user-triggered refresh is already running; it covers this period.
        val report = refreshScheduled(schedule.minFeedGapMillis) ?: return FeedBackgroundRunResult.SUCCESS
        val outcomes = report.outcomes.values
        record(FeedBackgroundOutcomes.kindOf(outcomes))
        return FeedBackgroundOutcomes.resultOf(outcomes, attemptCount)
    }

    private fun scheduleOf(settings: RssSettings): FeedBackgroundSchedule? =
        (FeedBackgroundScheduler.decide(settings) as? FeedBackgroundDecision.Schedule)?.schedule

    private fun record(kind: FeedBackgroundRunRecord.Kind) = recordRun(FeedBackgroundRunRecord(clock(), kind))
}
