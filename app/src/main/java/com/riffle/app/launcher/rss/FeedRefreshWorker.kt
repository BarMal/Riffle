package com.riffle.app.launcher.rss

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.riffle.app.launcher.DataStoreLauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.FeedBackgroundConditions
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunResult

/**
 * Periodic background feed refresh (issue #1393). A thin WorkManager adapter: it builds the same transport,
 * parser, cache and [FeedRefreshCoordinator] the app uses and delegates every decision to
 * [FeedBackgroundRefreshRunner]. It runs on WorkManager's executor thread, so blocking IO is fine, and it
 * never logs or reports URLs, bodies or exception messages.
 *
 * The class name is stored in WorkManager's database, so it must stay stable across releases (see the keep
 * rule in `proguard-rules.pro`).
 */
class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun doWork(): Result =
        try {
            when (androidFeedBackgroundRunner(applicationContext).run(runAttemptCount)) {
                FeedBackgroundRunResult.SUCCESS -> Result.success()
                FeedBackgroundRunResult.RETRY -> Result.retry()
            }
        } catch (ignored: RuntimeException) {
            // Deliberately dropped: a message could echo a feed URL. The next period tries again.
            Result.success()
        }
}

internal fun androidFeedBackgroundRunner(context: Context): FeedBackgroundRefreshRunner {
    val settingsRepository = DataStoreLauncherSettingsRepository(context)
    val cache = DataStoreFeedArticleCacheRepository(context)
    val coordinator =
        FeedRefreshCoordinator(
            transport = AndroidFeedTransport(),
            parser = AndroidFeedParser(),
            cache = cache,
            configuredFeeds = { settingsRepository.loadLauncherSettings()?.rss?.feeds.orEmpty() },
            // Never used: the worker calls the blocking entry point on its own thread.
            executor = { task -> task.run() },
        )
    return FeedBackgroundRefreshRunner(
        settings = { settingsRepository.loadLauncherSettings()?.rss },
        conditions = { androidBackgroundConditions(context) },
        refreshScheduled = coordinator::refreshScheduledBlocking,
        recordRun = cache::saveBackgroundRun,
    )
}

private fun androidBackgroundConditions(context: Context): FeedBackgroundConditions {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val power = context.getSystemService(PowerManager::class.java)
    return FeedBackgroundConditions(
        // Unknown counts as metered: a Wi-Fi-only user must never be fetched on a doubtful network.
        meteredNetwork = connectivity?.isActiveNetworkMetered ?: true,
        batterySaver = power?.isPowerSaveMode ?: false,
    )
}
