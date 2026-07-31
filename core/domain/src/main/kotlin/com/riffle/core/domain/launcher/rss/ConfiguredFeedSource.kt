package com.riffle.core.domain.launcher.rss

/**
 * Provides the currently configured feeds, ordered as the user (eventually) arranged them.
 *
 * This is a minimal placeholder seam for issue #1013 ("Settings and validation"), which owns real
 * feed management, settings-backed persistence, and validation. Until that lands, callers should
 * bind an in-memory or always-empty implementation. [FeedStagePlanner] depends only on this
 * interface so stage projection does not need to wait for or duplicate #1013's persistence work.
 */
fun interface ConfiguredFeedSource {
    fun configuredFeeds(): List<FeedConfiguration>
}

/** Placeholder default until #1013 supplies a settings-backed [ConfiguredFeedSource]. */
object NoConfiguredFeedSource : ConfiguredFeedSource {
    override fun configuredFeeds(): List<FeedConfiguration> = emptyList()
}
