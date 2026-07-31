package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.RssSettings

/**
 * Feed management, refresh interval, and privacy copy for the RSS settings page (issue #1013).
 * Adding a feed validates the URL client-side via [FeedUrl.parse] before dispatching an action,
 * so an invalid or credential-bearing URL never reaches the reducer/settings model and only a
 * short, generic error is ever shown -- never the raw parse failure, which could echo back
 * sensitive URL fragments.
 */
@Composable
internal fun SettingsRssPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "RSS feeds") {
        RssFeedSettings(settings = state.settings.rss, onAction = onAction)
    }
}

@Composable
private fun RssFeedSettings(
    settings: RssSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextColumn(
            title = "Privacy",
            subtitle =
                "Riffle stores only the feed URLs you add, their enabled state, and your refresh " +
                    "interval; this is the only feed data included in launcher backups. Cached " +
                    "articles, images, and read/dismiss state stay on this device and are never " +
                    "backed up. Only public https feeds are accepted, embedded credentials are " +
                    "rejected, and tracking-only query parameters are removed before a feed URL " +
                    "is saved.",
        )
        RssRefreshIntervalSetting(selected = settings.refreshInterval, onAction = onAction)
        RssFeedListSetting(feeds = settings.feeds, onAction = onAction)
        RssAddFeedSetting(onAction = onAction)
    }
}

@Composable
private fun RssRefreshIntervalSetting(
    selected: FeedRefreshIntervalOption,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsListRow(
        title = "Refresh interval",
        subtitle = selected.refreshIntervalLabel(),
        trailingContent = {
            TextButton(onClick = { onAction(LauncherShellAction.SelectRssRefreshInterval(selected.next())) }) {
                SettingsButtonText(text = "Change")
            }
        },
    )
}

private fun FeedRefreshIntervalOption.refreshIntervalLabel(): String =
    when (this) {
        FeedRefreshIntervalOption.MINUTES_30 -> "Every 30 minutes"
        FeedRefreshIntervalOption.MINUTES_60 -> "Every hour"
        FeedRefreshIntervalOption.MINUTES_180 -> "Every 3 hours"
        FeedRefreshIntervalOption.MINUTES_360 -> "Every 6 hours"
    }

@Composable
private fun RssFeedListSetting(
    feeds: List<FeedConfiguration>,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsTextColumn(
        title = "Configured feeds",
        subtitle = feeds.feedCountLabel(),
    )
    feeds.forEach { feed ->
        RssFeedRow(feed = feed, onAction = onAction)
    }
}

private fun List<FeedConfiguration>.feedCountLabel(): String =
    when (size) {
        0 -> "No feeds configured"
        1 -> "1 feed"
        else -> "$size feeds"
    }

@Composable
private fun RssFeedRow(
    feed: FeedConfiguration,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = feed.url.value,
            subtitle = if (feed.enabled) "Enabled" else "Disabled",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { onAction(LauncherShellAction.SetRssFeedEnabled(feed.id, !feed.enabled)) },
            ) {
                SettingsButtonText(text = if (feed.enabled) "Disable" else "Enable")
            }
            TextButton(onClick = { onAction(LauncherShellAction.RemoveRssFeed(feed.id)) }) {
                SettingsButtonText(text = "Remove")
            }
        }
    }
}

private const val GENERIC_INVALID_FEED_URL_MESSAGE = "Enter a valid https feed URL"

@Composable
private fun RssAddFeedSetting(onAction: (LauncherShellAction) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = url,
            onValueChange = { value ->
                url = value
                errorText = null
            },
            label = { Text("Feed URL") },
            isError = errorText != null,
            supportingText = { errorText?.let { message -> Text(message) } },
            singleLine = true,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    FeedUrl.parse(url).fold(
                        onSuccess = { feedUrl ->
                            onAction(LauncherShellAction.AddRssFeed(feedUrl))
                            url = ""
                            errorText = null
                        },
                        onFailure = { errorText = GENERIC_INVALID_FEED_URL_MESSAGE },
                    )
                },
            ) {
                SettingsButtonText(text = "Add feed")
            }
        }
    }
}
