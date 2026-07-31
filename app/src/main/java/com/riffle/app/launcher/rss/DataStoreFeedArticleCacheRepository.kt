package com.riffle.app.launcher.rss

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riffle.app.launcher.EpochMillisProvider
import com.riffle.app.launcher.SystemEpochMillisProvider
import com.riffle.core.domain.launcher.rss.FeedId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * DataStore file backing the offline RSS article cache. This file must never be part of Android
 * Auto Backup / Transfer -- see `res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml`,
 * which explicitly exclude it, and the `android:dataExtractionRules` / `android:fullBackupContent`
 * wiring in AndroidManifest.xml.
 */
internal const val RSS_ARTICLE_CACHE_DATASTORE_NAME = "riffle_rss_article_cache"

private val Context.rssArticleCacheDataStore by preferencesDataStore(name = RSS_ARTICLE_CACHE_DATASTORE_NAME)

class DataStoreFeedArticleCacheRepository(
    context: Context,
    private val epochMillisProvider: EpochMillisProvider = SystemEpochMillisProvider,
) : FeedArticleCacheRepository {
    private val dataStore = context.rssArticleCacheDataStore

    override fun loadFeed(
        feedId: FeedId,
        staleAfterMillis: Long,
    ): FeedCacheResult {
        val feed =
            readCacheDocument(dataStore).feeds.firstOrNull { it.feedId == feedId }
                ?: return FeedCacheResult.Empty
        val freshness =
            freshnessOf(
                fetchedAtEpochMillis = feed.fetchedAtEpochMillis,
                nowEpochMillis = epochMillisProvider.nowEpochMillis(),
                staleAfterMillis = staleAfterMillis,
            )
        return FeedCacheResult.Available(CachedFeedSnapshot(feed, freshness))
    }

    override fun replaceFeed(
        feedId: FeedId,
        articles: List<CachedFeedArticle>,
    ) {
        mutateCacheDocument(dataStore) { document ->
            val updatedFeed =
                CachedFeed(
                    feedId = feedId,
                    articles = boundFeedArticles(articles),
                    fetchedAtEpochMillis = epochMillisProvider.nowEpochMillis(),
                )
            val feeds = evictGlobalArticles(document.feeds.filterNot { it.feedId == feedId } + updatedFeed)
            document.copy(feeds = feeds, images = evictImages(document.images, feeds.referencedDigests()))
        }
    }

    override fun clearFeed(feedId: FeedId) {
        mutateCacheDocument(dataStore) { document ->
            val feeds = document.feeds.filterNot { it.feedId == feedId }
            document.copy(feeds = feeds, images = evictImages(document.images, feeds.referencedDigests()))
        }
    }

    override fun isRead(digest: String): Boolean = digest in readCacheDocument(dataStore).readDigests

    override fun markRead(digest: String) {
        mutateCacheDocument(dataStore) { document -> document.copy(readDigests = document.readDigests + digest) }
    }

    override fun isDismissed(digest: String): Boolean = digest in readCacheDocument(dataStore).dismissedDigests

    override fun markDismissed(digest: String) {
        mutateCacheDocument(dataStore) { document ->
            document.copy(dismissedDigests = document.dismissedDigests + digest)
        }
    }

    override fun cachedImage(digest: String): ByteArray? {
        return readCacheDocument(dataStore).images.firstOrNull { it.digest == digest }?.bytes
    }

    override fun cacheImage(
        digest: String,
        bytes: ByteArray,
    ) {
        if (bytes.size > MAX_CACHED_IMAGE_BYTES) return
        mutateCacheDocument(dataStore) { document ->
            val referencedDigests = document.feeds.referencedDigests()
            if (digest !in referencedDigests) {
                document
            } else {
                val image = CachedImage(digest, bytes, epochMillisProvider.nowEpochMillis())
                val images = document.images.filterNot { it.digest == digest } + image
                document.copy(images = evictImages(images, referencedDigests))
            }
        }
    }

    override fun clear() {
        writeCacheDocument(dataStore, FeedArticleCacheDocument.empty())
    }
}

private fun List<CachedFeed>.referencedDigests(): Set<String> {
    return flatMap { feed -> feed.articles.map { article -> article.digest } }.toSet()
}

private fun readCacheDocument(dataStore: DataStore<Preferences>): FeedArticleCacheDocument =
    runBlocking { dataStore.data.first()[FeedArticleCacheKeys.cache] }
        ?.let { value -> decodeFeedArticleCacheDocument(value) }
        ?: FeedArticleCacheDocument.empty()

private fun mutateCacheDocument(
    dataStore: DataStore<Preferences>,
    transform: (FeedArticleCacheDocument) -> FeedArticleCacheDocument,
) {
    writeCacheDocument(dataStore, transform(readCacheDocument(dataStore)))
}

private fun writeCacheDocument(
    dataStore: DataStore<Preferences>,
    document: FeedArticleCacheDocument,
) {
    runBlocking {
        dataStore.edit { preferences ->
            preferences[FeedArticleCacheKeys.cache] = encodeFeedArticleCacheDocument(document)
            preferences[FeedArticleCacheKeys.snapshotRevision] =
                nextFeedArticleCacheSnapshotRevision(preferences[FeedArticleCacheKeys.snapshotRevision])
        }
    }
}

internal fun nextFeedArticleCacheSnapshotRevision(currentRevision: Long?): Long =
    when (currentRevision) {
        null, Long.MAX_VALUE -> 0
        else -> currentRevision + 1
    }

private object FeedArticleCacheKeys {
    val cache = stringPreferencesKey("rss_article_cache")
    val snapshotRevision = longPreferencesKey("rss_article_cache_snapshot_revision")
}
