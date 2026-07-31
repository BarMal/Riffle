package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedId
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The full persisted offline RSS cache: normalized article content per feed, optional cached
 * article images, and the opaque read/dismiss intent digests. This document is device-local
 * cache state -- it is never included in the in-app JSON launcher backup (see
 * [com.riffle.app.launcher.LauncherBackupDocument]) and is excluded from OS-level Auto Backup.
 */
internal data class FeedArticleCacheDocument(
    val feeds: List<CachedFeed> = emptyList(),
    val images: List<CachedImage> = emptyList(),
    val readDigests: Set<String> = emptySet(),
    val dismissedDigests: Set<String> = emptySet(),
) {
    companion object {
        fun empty(): FeedArticleCacheDocument = FeedArticleCacheDocument()
    }
}

internal const val CURRENT_FEED_ARTICLE_CACHE_VERSION = 1

internal fun encodeFeedArticleCacheDocument(document: FeedArticleCacheDocument): String =
    JSONObject()
        .put("version", CURRENT_FEED_ARTICLE_CACHE_VERSION)
        .put("feeds", JSONArray(document.feeds.map(::encodeCachedFeed)))
        .put("images", JSONArray(document.images.map(::encodeCachedImage)))
        .put("readDigests", JSONArray(document.readDigests.toList()))
        .put("dismissedDigests", JSONArray(document.dismissedDigests.toList()))
        .toString()

/**
 * Decodes a persisted cache document. Follows the repository's shallow-migration convention: a
 * version mismatch or any structural corruption is treated as an unrecoverable cache (never a
 * crash) and discarded in favor of an empty document -- callers simply refetch, so there is
 * nothing to gain from a deeper field-by-field migration for this payload.
 */
internal fun decodeFeedArticleCacheDocument(value: String): FeedArticleCacheDocument? =
    runCatching {
        val json = JSONObject(JSONTokener(value))
        require(json.optInt("version", -1) == CURRENT_FEED_ARTICLE_CACHE_VERSION) {
            "Unsupported feed article cache version"
        }
        FeedArticleCacheDocument(
            feeds = json.optJSONArray("feeds")?.decodeCachedFeeds() ?: emptyList(),
            images = json.optJSONArray("images")?.decodeCachedImages() ?: emptyList(),
            readDigests = json.optJSONArray("readDigests")?.decodeDigestSet() ?: emptySet(),
            dismissedDigests = json.optJSONArray("dismissedDigests")?.decodeDigestSet() ?: emptySet(),
        )
    }.getOrNull()

private fun encodeCachedFeed(feed: CachedFeed): JSONObject =
    JSONObject()
        .put("feedId", feed.feedId.value)
        .put("fetchedAtEpochMillis", feed.fetchedAtEpochMillis)
        .put("articles", JSONArray(feed.articles.map(::encodeCachedFeedArticle)))

private fun JSONArray.decodeCachedFeeds(): List<CachedFeed> =
    (0 until length()).mapNotNull { index -> runCatching { decodeCachedFeed(getJSONObject(index)) }.getOrNull() }

private fun decodeCachedFeed(json: JSONObject): CachedFeed =
    CachedFeed(
        feedId = FeedId(json.getString("feedId")),
        fetchedAtEpochMillis = json.getLong("fetchedAtEpochMillis"),
        articles =
            json.optJSONArray("articles")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    runCatching { decodeCachedFeedArticle(array.getJSONObject(index)) }.getOrNull()
                }
            }.orEmpty(),
    )

private fun JSONArray.decodeDigestSet(): Set<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf(String::isNotBlank) }.toSet()
