package com.riffle.app.launcher

import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.RssSettings
import com.riffle.core.domain.launcher.settings.withAddedFeed
import com.riffle.core.domain.launcher.settings.withFeedEnabled
import com.riffle.core.domain.launcher.settings.withRefreshInterval
import com.riffle.core.domain.launcher.settings.withoutFeed

/** Pure settings transform for feed management actions; cache-clearing side effects live in [withRssSettingsAction]. */
private fun RssSettings.withRssSettingsAction(action: LauncherShellAction): RssSettings =
    when (action) {
        is LauncherShellAction.AddRssFeed -> withAddedFeed(url = action.url, profile = action.profile)
        is LauncherShellAction.RemoveRssFeed -> withoutFeed(action.feedId)
        is LauncherShellAction.SetRssFeedEnabled -> withFeedEnabled(feedId = action.feedId, enabled = action.enabled)
        is LauncherShellAction.SelectRssRefreshInterval -> withRefreshInterval(action.option)
        else -> this
    }

/**
 * Removing or disabling a feed also clears its offline article cache so cache content never
 * outlives the configuration that produced it (see FeedArticleCacheRepository.clearFeed).
 */
internal fun LauncherShellState.withRssSettingsAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
    feedArticleCacheRepository: FeedArticleCacheRepository,
): LauncherShellState {
    when (action) {
        is LauncherShellAction.RemoveRssFeed -> feedArticleCacheRepository.clearFeed(action.feedId)
        is LauncherShellAction.SetRssFeedEnabled ->
            if (!action.enabled) feedArticleCacheRepository.clearFeed(action.feedId)

        else -> Unit
    }
    return withLauncherSettings(
        settings = launcherSettings.copy(rss = launcherSettings.rss.withRssSettingsAction(action)),
        launcherSettingsRepository = launcherSettingsRepository,
    )
}
