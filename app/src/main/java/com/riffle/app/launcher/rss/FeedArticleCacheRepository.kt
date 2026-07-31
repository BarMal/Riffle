package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedId

/**
 * Bounded offline persistence for normalized RSS article content and read/dismiss intent state.
 *
 * Read/dismiss state is keyed only by the opaque [com.riffle.core.domain.launcher.rss.FeedItemIntentDigest]
 * hex value -- never by URL, title, or any URL-derived string. Cached content is device-local: it
 * is never part of the in-app JSON launcher backup and is excluded from OS-level Auto Backup.
 */
interface FeedArticleCacheRepository {
    /** Loads the last cached snapshot for [feedId], if any, marking it stale when past [staleAfterMillis]. */
    fun loadFeed(
        feedId: FeedId,
        staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
    ): FeedCacheResult

    /** Replaces the cached articles for [feedId], applying per-feed and global bounds and eviction. */
    fun replaceFeed(
        feedId: FeedId,
        articles: List<CachedFeedArticle>,
    )

    /** Removes all cached articles (and now-orphaned images) for [feedId]. */
    fun clearFeed(feedId: FeedId)

    fun isRead(digest: String): Boolean

    fun markRead(digest: String)

    fun isDismissed(digest: String): Boolean

    fun markDismissed(digest: String)

    /** Returns previously cached image bytes for [digest], if any were stored. */
    fun cachedImage(digest: String): ByteArray?

    /**
     * Caches [bytes] for [digest]. Silently ignored when [bytes] exceeds [MAX_CACHED_IMAGE_BYTES]
     * or when [digest] does not correspond to a currently cached article.
     */
    fun cacheImage(
        digest: String,
        bytes: ByteArray,
    )

    /** Fully clears all cached articles, images, and read/dismiss state. */
    fun clear()
}
