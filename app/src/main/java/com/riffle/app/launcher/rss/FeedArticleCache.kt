package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.MAX_FEED_AUTHOR_LENGTH
import com.riffle.core.domain.launcher.rss.MAX_FEED_ITEM_LIMIT
import com.riffle.core.domain.launcher.rss.MAX_FEED_SUMMARY_LENGTH
import com.riffle.core.domain.launcher.rss.MAX_FEED_TITLE_LENGTH
import com.riffle.core.domain.launcher.rss.MAX_FEED_URL_LENGTH

private const val SHA_256_HEX_LENGTH = 64

/** Per-feed cap on cached articles; mirrors [MAX_FEED_ITEM_LIMIT], the existing normalizer output cap. */
const val MAX_CACHED_ARTICLES_PER_FEED = MAX_FEED_ITEM_LIMIT

/** Global cap on cached articles across all feeds. */
const val MAX_CACHED_ARTICLES_GLOBAL = MAX_CACHED_ARTICLES_PER_FEED * 10

/** Global cap on the number of cached article images. */
const val MAX_CACHED_IMAGES_GLOBAL = 200

/** Per-image byte cap; oversized images are simply never cached. */
const val MAX_CACHED_IMAGE_BYTES = 512 * 1024

/** Global byte cap across all cached images. */
const val MAX_CACHED_IMAGE_BYTES_GLOBAL = 20L * 1024 * 1024

/** Default staleness window before cached content is reported as [CacheFreshness.STALE]. */
const val DEFAULT_STALE_AFTER_MILLIS = 6L * 60 * 60 * 1000

/**
 * A single normalized article persisted to the offline cache. Fields mirror
 * [com.riffle.core.domain.launcher.rss.FeedItem] and are bounded by the same limits enforced
 * upstream by `FeedItemNormalizer`. No raw HTML, response headers, or credentials are stored
 * here -- only normalized display fields and the opaque [digest] used for read/dismiss state.
 */
data class CachedFeedArticle(
    val digest: String,
    val title: String,
    val author: String? = null,
    val publishedAtEpochMillis: Long? = null,
    val summary: String? = null,
    val canonicalUrl: String? = null,
    val imageUrl: String? = null,
    val sourceOrder: Int,
) {
    init {
        require(isValidFeedItemDigest(digest)) { "Cached article digest must be lowercase SHA-256 hex." }
        require(title.length <= MAX_FEED_TITLE_LENGTH) { "Cached article title too long." }
        require((author?.length ?: 0) <= MAX_FEED_AUTHOR_LENGTH) { "Cached article author too long." }
        require((summary?.length ?: 0) <= MAX_FEED_SUMMARY_LENGTH) { "Cached article summary too long." }
        require((canonicalUrl?.length ?: 0) <= MAX_FEED_URL_LENGTH) { "Cached article URL too long." }
        require((imageUrl?.length ?: 0) <= MAX_FEED_URL_LENGTH) { "Cached article image URL too long." }
    }
}

/** Returns true when [value] is a well-formed opaque item digest (lowercase SHA-256 hex). */
fun isValidFeedItemDigest(value: String): Boolean =
    value.length == SHA_256_HEX_LENGTH &&
        value.all { character -> character in '0'..'9' || character in 'a'..'f' }

data class CachedFeed(
    val feedId: FeedId,
    val articles: List<CachedFeedArticle>,
    val fetchedAtEpochMillis: Long,
)

data class CachedImage(
    val digest: String,
    val bytes: ByteArray,
    val cachedAtEpochMillis: Long,
) {
    init {
        require(isValidFeedItemDigest(digest)) { "Cached image digest must be lowercase SHA-256 hex." }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is CachedImage &&
                    digest == other.digest &&
                    bytes.contentEquals(other.bytes) &&
                    cachedAtEpochMillis == other.cachedAtEpochMillis
            )

    override fun hashCode(): Int = digest.hashCode()
}

enum class CacheFreshness { FRESH, STALE }

data class CachedFeedSnapshot(
    val feed: CachedFeed,
    val freshness: CacheFreshness,
)

/** Result of a cache lookup -- callers must fall back to stale content rather than nothing. */
sealed interface FeedCacheResult {
    data class Available(val snapshot: CachedFeedSnapshot) : FeedCacheResult

    data object Empty : FeedCacheResult
}

/**
 * Deterministic per-feed retention order: dated articles newest-first (matching
 * `FeedItemNormalizer`'s display order), then undated articles in their original source order.
 */
internal val articleRetentionOrder =
    Comparator<CachedFeedArticle> { left, right ->
        when {
            left.publishedAtEpochMillis != null && right.publishedAtEpochMillis != null ->
                right.publishedAtEpochMillis.compareTo(left.publishedAtEpochMillis)
            left.publishedAtEpochMillis != null -> -1
            right.publishedAtEpochMillis != null -> 1
            else -> left.sourceOrder.compareTo(right.sourceOrder)
        }
    }

/** Bounds a single feed's articles to [MAX_CACHED_ARTICLES_PER_FEED], evicting the oldest first. */
internal fun boundFeedArticles(articles: List<CachedFeedArticle>): List<CachedFeedArticle> =
    articles.sortedWith(articleRetentionOrder).take(MAX_CACHED_ARTICLES_PER_FEED)

/**
 * Deterministically evicts articles across every cached feed down to [MAX_CACHED_ARTICLES_GLOBAL],
 * keeping the newest articles by publish time (falling back to the owning feed's fetch time for
 * undated articles), with feed id and source order as stable tie-breakers. Relative per-feed
 * ordering is preserved for the articles that survive.
 */
internal fun evictGlobalArticles(feeds: List<CachedFeed>): List<CachedFeed> {
    data class Ranked(
        val feed: CachedFeed,
        val article: CachedFeedArticle,
    )

    val ranked = feeds.flatMap { feed -> feed.articles.map { article -> Ranked(feed, article) } }
    val retentionOrder =
        compareByDescending<Ranked> { it.article.publishedAtEpochMillis ?: it.feed.fetchedAtEpochMillis }
            .thenByDescending { it.feed.fetchedAtEpochMillis }
            .thenBy { it.feed.feedId.value }
            .thenBy { it.article.sourceOrder }
    val retainedDigests =
        ranked
            .sortedWith(retentionOrder)
            .take(MAX_CACHED_ARTICLES_GLOBAL)
            .map { it.feed.feedId to it.article.digest }
            .toSet()

    return feeds.map { feed ->
        feed.copy(articles = feed.articles.filter { article -> (feed.feedId to article.digest) in retainedDigests })
    }
}

/**
 * Deterministically evicts cached images by recency (newest [CachedImage.cachedAtEpochMillis]
 * first) down to both [MAX_CACHED_IMAGES_GLOBAL] and [MAX_CACHED_IMAGE_BYTES_GLOBAL], and drops
 * any image whose digest is no longer referenced by a retained article.
 */
internal fun evictImages(
    images: List<CachedImage>,
    referencedDigests: Set<String>,
): List<CachedImage> {
    val referenced = images.filter { it.digest in referencedDigests }
    val ordered =
        referenced.sortedWith(
            compareByDescending<CachedImage> { it.cachedAtEpochMillis }.thenBy { it.digest },
        )
    val retained = mutableListOf<CachedImage>()
    var totalBytes = 0L
    for (image in ordered) {
        val nextTotal = totalBytes + image.bytes.size
        val fitsWithinCaps = retained.size < MAX_CACHED_IMAGES_GLOBAL && nextTotal <= MAX_CACHED_IMAGE_BYTES_GLOBAL
        if (!fitsWithinCaps) continue
        retained += image
        totalBytes = nextTotal
    }
    return retained
}

internal fun freshnessOf(
    fetchedAtEpochMillis: Long,
    nowEpochMillis: Long,
    staleAfterMillis: Long,
): CacheFreshness {
    return if (nowEpochMillis - fetchedAtEpochMillis > staleAfterMillis) CacheFreshness.STALE else CacheFreshness.FRESH
}
