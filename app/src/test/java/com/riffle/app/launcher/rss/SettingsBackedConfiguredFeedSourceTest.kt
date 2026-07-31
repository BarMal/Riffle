package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.RssSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackedConfiguredFeedSourceTest {
    @Test
    fun returnsFeedsConfiguredInLauncherSettings() {
        val feed =
            FeedConfiguration(
                id = FeedId("feed-1"),
                url = FeedUrl.parse("https://example.com/feed.xml").getOrThrow(),
            )
        val settings = LauncherSettings(rss = RssSettings(feeds = listOf(feed)))

        val source = SettingsBackedConfiguredFeedSource(settings)

        assertEquals(listOf(feed), source.configuredFeeds())
    }

    @Test
    fun returnsNoFeedsWhenNoneAreConfigured() {
        val source = SettingsBackedConfiguredFeedSource(LauncherSettings())

        assertEquals(emptyList<FeedConfiguration>(), source.configuredFeeds())
    }

    @Test
    fun reflectsTheLatestSettingsWhenBackedByASupplier() {
        var settings = LauncherSettings()
        val source = SettingsBackedConfiguredFeedSource { settings }
        val feed =
            FeedConfiguration(
                id = FeedId("feed-1"),
                url = FeedUrl.parse("https://example.com/feed.xml").getOrThrow(),
            )

        assertEquals(emptyList<FeedConfiguration>(), source.configuredFeeds())

        settings = LauncherSettings(rss = RssSettings(feeds = listOf(feed)))

        assertEquals(listOf(feed), source.configuredFeeds())
    }
}
