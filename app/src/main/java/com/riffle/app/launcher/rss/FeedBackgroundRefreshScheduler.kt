package com.riffle.app.launcher.rss

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.riffle.core.domain.launcher.settings.FeedBackgroundDecision
import com.riffle.core.domain.launcher.settings.FeedBackgroundSchedule
import java.util.concurrent.TimeUnit

/** Name of the one unique periodic work; used by `adb` validation steps in docs/product/rss-refresh.md. */
const val FEED_BACKGROUND_REFRESH_WORK_NAME = "riffle.rss.background-refresh"

private const val RETRY_BACKOFF_MINUTES = 30L

/**
 * Applies a pure [FeedBackgroundDecision] to WorkManager (issue #1393). Safe to call repeatedly with the same
 * decision. Enqueuing uses [ExistingPeriodicWorkPolicy.UPDATE], so changing the interval or a constraint
 * replaces the spec in place without cancelling a running attempt. A [FeedBackgroundDecision.Cancel] removes
 * the work entirely, so an Off setting leaves nothing scheduled.
 *
 * Call from a background thread: the first WorkManager access opens its database.
 */
class FeedBackgroundRefreshScheduler(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun apply(decision: FeedBackgroundDecision) {
        val workManager = WorkManager.getInstance(appContext)
        when (decision) {
            is FeedBackgroundDecision.Cancel -> workManager.cancelUniqueWork(FEED_BACKGROUND_REFRESH_WORK_NAME)
            is FeedBackgroundDecision.Schedule ->
                workManager.enqueueUniquePeriodicWork(
                    FEED_BACKGROUND_REFRESH_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request(decision.schedule),
                )
        }
    }

    private fun request(schedule: FeedBackgroundSchedule) =
        PeriodicWorkRequestBuilder<FeedRefreshWorker>(schedule.intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints(schedule))
            // First run one period after opting in, not immediately.
            .setInitialDelay(schedule.intervalMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

    private fun constraints(schedule: FeedBackgroundSchedule): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(networkType(schedule))
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(schedule.requiresCharging)
            .build()

    private fun networkType(schedule: FeedBackgroundSchedule): NetworkType =
        if (schedule.requiresUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED
}
