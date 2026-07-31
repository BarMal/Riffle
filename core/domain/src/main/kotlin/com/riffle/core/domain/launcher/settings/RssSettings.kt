package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import java.util.UUID

/**
 * Durable RSS/Atom feed configuration and refresh policy (issue #1013). Only [FeedConfiguration]
 * values -- normalized public HTTPS URLs, feed identity, enabled state, and refresh intent -- are
 * stored here, matching the ADR's backup/restore contract. Cached article content, images, and
 * response metadata never live in this settings model; see `FeedArticleCacheRepository` for the
 * device-local, non-backed-up cache.
 */
data class RssSettings(
    val feeds: List<FeedConfiguration> = emptyList(),
    val refreshInterval: FeedRefreshIntervalOption = FeedRefreshIntervalOption.DEFAULT,
)

/**
 * User-configurable minimum refresh interval. The ADR calls for "a configurable minimum refresh
 * interval, with a conservative default." Refresh itself remains user-triggered until a later
 * scheduler slice ships, so this setting only bounds how soon a future scheduled/allowed refresh
 * may run; it never causes a refresh to happen on its own.
 */
enum class FeedRefreshIntervalOption(
    val minutes: Int,
) {
    MINUTES_30(30),
    MINUTES_60(60),
    MINUTES_180(180),
    MINUTES_360(360),
    ;

    fun next(): FeedRefreshIntervalOption = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Conservative default: favors battery/network conservatism over freshness. */
        val DEFAULT = MINUTES_180
    }
}

/** Bounds the number of feeds a user can configure, mirroring other bounded settings lists. */
const val MAX_CONFIGURED_FEEDS = 50

fun RssSettings.withFeeds(feeds: List<FeedConfiguration>): RssSettings = copy(feeds = feeds.take(MAX_CONFIGURED_FEEDS))

/** Adds [url] as a new enabled feed for [profile], ignoring an exact duplicate. */
fun RssSettings.withAddedFeed(
    url: FeedUrl,
    profile: AppProfile = AppProfile.personal(),
): RssSettings =
    if (feeds.any { feed -> feed.url.value == url.value && feed.profile.id == profile.id }) {
        this
    } else {
        withFeeds(
            feeds +
                FeedConfiguration(
                    id = FeedId(UUID.randomUUID().toString()),
                    url = url,
                    profile = profile,
                ),
        )
    }

fun RssSettings.withoutFeed(feedId: FeedId): RssSettings = copy(feeds = feeds.filterNot { feed -> feed.id == feedId })

fun RssSettings.withFeedEnabled(
    feedId: FeedId,
    enabled: Boolean,
): RssSettings = copy(feeds = feeds.map { feed -> if (feed.id == feedId) feed.copy(enabled = enabled) else feed })

fun RssSettings.withRefreshInterval(option: FeedRefreshIntervalOption): RssSettings = copy(refreshInterval = option)
