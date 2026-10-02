package com.riffle.app.launcher.rss

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.FeedBackgroundScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the platform's periodic feed work in step with the saved RSS settings (issue #1393): on start, and on
 * every change to the interval, the Wi-Fi/charging options or the feed list. Only the pure decision is
 * observed, so unrelated settings changes do nothing. With the interval Off this only ever cancels.
 */
fun ComponentActivity.startFeedBackgroundSync(
    state: StateFlow<LauncherShellState>,
    scheduler: FeedBackgroundRefreshScheduler,
) {
    lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state
                .map { shellState -> FeedBackgroundScheduler.decide(shellState.launcherSettings.rss) }
                .distinctUntilChanged()
                .collect { decision ->
                    // A scheduling failure must never take the launcher down; the next change retries.
                    withContext(Dispatchers.IO) { runCatching { scheduler.apply(decision) } }
                }
        }
    }
}
