package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.ConfiguredFeedSource
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.settings.LauncherSettings

/**
 * Settings-backed [ConfiguredFeedSource] (issue #1013), replacing the `NoConfiguredFeedSource`
 * placeholder from #1011. Reads feed configuration directly from [LauncherSettings.rss.feeds],
 * so [com.riffle.core.domain.launcher.rss.FeedStagePlanner] projects the feeds a user has actually
 * configured, added, removed, or enabled/disabled through settings.
 */
class SettingsBackedConfiguredFeedSource(
    private val settings: () -> LauncherSettings,
) : ConfiguredFeedSource {
    constructor(settings: LauncherSettings) : this({ settings })

    override fun configuredFeeds(): List<FeedConfiguration> = settings().rss.feeds
}
