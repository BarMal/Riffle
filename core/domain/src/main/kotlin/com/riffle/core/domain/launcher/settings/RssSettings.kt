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
    /** Background refresh runs only on an unmetered network (Wi-Fi). On by default. */
    val backgroundWifiOnly: Boolean = true,
    /** Background refresh runs only while the device is charging. Off by default. */
    val backgroundChargingOnly: Boolean = false,
)

/**
 * Opt-in background refresh interval (issue #1393). [OFF] is the default: Riffle makes no automatic network
 * requests unless the user picks a nonzero interval. Earlier builds stored a never-acted-on interval
 * (`MINUTES_30` .. `MINUTES_360`); those names no longer decode and fall back to [OFF], so nobody is opted
 * in by a setting they never saw take effect.
 */
enum class FeedRefreshIntervalOption(
    val minutes: Int,
) {
    OFF(0),
    HOURS_1(60),
    HOURS_3(180),
    HOURS_6(360),
    HOURS_12(720),
    HOURS_24(1440),
    ;

    /** True when the user opted in to background refresh. */
    val isEnabled: Boolean get() = this != OFF

    fun next(): FeedRefreshIntervalOption = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Privacy first: nothing runs in the background until the user asks for it. */
        val DEFAULT = OFF
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

fun RssSettings.withBackgroundWifiOnly(enabled: Boolean): RssSettings = copy(backgroundWifiOnly = enabled)

fun RssSettings.withBackgroundChargingOnly(enabled: Boolean): RssSettings = copy(backgroundChargingOnly = enabled)
