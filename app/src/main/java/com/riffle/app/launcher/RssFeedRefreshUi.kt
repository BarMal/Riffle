package com.riffle.app.launcher

import android.text.format.DateUtils
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.rss.FeedRefreshCoordinator
import com.riffle.app.launcher.rss.label
import com.riffle.app.launcher.rss.summary
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedRefreshScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The user-triggered feed refresh, provided by the activity; null (no row shown) where none is wired. */
internal val LocalFeedRefreshCoordinator = staticCompositionLocalOf<FeedRefreshCoordinator?> { null }

/** Re-reads [coordinator] status whenever it reports progress; the returned value only serves as a recompose key. */
@Composable
private fun rememberRefreshTick(coordinator: FeedRefreshCoordinator): Int {
    var tick by remember(coordinator) { mutableIntStateOf(0) }
    DisposableEffect(coordinator) {
        val stop = coordinator.statusChanges.observe { tick++ }
        onDispose { stop() }
    }
    return tick
}

/** "Refresh feeds" row. Network work only starts from the button and runs on the coordinator's executor. */
@Composable
internal fun RssRefreshAllSetting(
    coordinator: FeedRefreshCoordinator,
    feeds: List<FeedConfiguration>,
) {
    val tick = rememberRefreshTick(coordinator)
    var message by remember { mutableStateOf<String?>(null) }
    val refreshing = remember(tick) { coordinator.isRefreshing }
    SettingsListRow(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        title = "Refresh feeds",
        subtitle =
            when {
                refreshing -> "Refreshing…"
                else -> message ?: "Fetch the latest articles now. Feeds are only fetched when you ask."
            },
        trailingContent = {
            TextButton(
                enabled = !refreshing && feeds.any(FeedConfiguration::enabled),
                onClick = {
                    message = null
                    coordinator.refresh(FeedRefreshScope.All) { report -> message = report.summary() }
                },
            ) {
                SettingsButtonText(text = "Refresh")
            }
        },
    )
}

/** Status line for one feed row: refreshing, last error, or last update. Cache time is read off the main thread. */
@Composable
internal fun rssFeedStatusText(
    coordinator: FeedRefreshCoordinator?,
    feed: FeedConfiguration,
): String? {
    if (coordinator == null || !feed.enabled) return null
    val tick = rememberRefreshTick(coordinator)
    val cachedAt by produceState<Long?>(initialValue = null, coordinator, feed.id, tick) {
        value = withContext(Dispatchers.IO) { coordinator.lastCachedAtMillis(feed.id) }
    }
    val status = remember(tick) { coordinator.statusOf(feed.id) }
    val updatedAt = status.lastUpdatedAtEpochMillis ?: cachedAt
    return when {
        status.refreshing -> "Refreshing…"
        status.lastFailure != null -> "Last refresh failed: ${status.lastFailure.label()}"
        updatedAt != null -> "Updated ${DateUtils.getRelativeTimeSpanString(updatedAt)}"
        else -> "Not refreshed yet"
    }
}
