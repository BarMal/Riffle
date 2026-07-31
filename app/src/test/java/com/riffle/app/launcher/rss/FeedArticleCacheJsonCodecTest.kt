package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedArticleCacheJsonCodecTest {
    @Test
    fun roundTripsAFullDocument() {
        val digestA = "a".repeat(64)
        val digestB = "b".repeat(64)
        val document =
            FeedArticleCacheDocument(
                feeds =
                    listOf(
                        CachedFeed(
                            feedId = FeedId("news"),
                            fetchedAtEpochMillis = 123L,
                            articles =
                                listOf(
                                    CachedFeedArticle(
                                        digest = digestA,
                                        title = "Title",
                                        author = "Author",
                                        publishedAtEpochMillis = 456L,
                                        summary = "Summary",
                                        canonicalUrl = "https://example.com/article",
                                        imageUrl = "https://example.com/article.png",
                                        sourceOrder = 0,
                                    ),
                                ),
                        ),
                    ),
                images =
                    listOf(CachedImage(digest = digestA, bytes = byteArrayOf(1, 2, 3), cachedAtEpochMillis = 789L)),
                readDigests = setOf(digestA),
                dismissedDigests = setOf(digestB),
            )

        val decoded = decodeFeedArticleCacheDocument(encodeFeedArticleCacheDocument(document))

        assertEquals(document, decoded)
    }

    @Test
    fun encodesTheCurrentVersion() {
        val json = JSONObject(encodeFeedArticleCacheDocument(FeedArticleCacheDocument.empty()))

        assertEquals(CURRENT_FEED_ARTICLE_CACHE_VERSION, json.getInt("version"))
    }

    @Test
    fun discardsAMismatchedVersionInsteadOfCrashing() {
        val obsolete =
            JSONObject(encodeFeedArticleCacheDocument(FeedArticleCacheDocument.empty()))
                .put("version", CURRENT_FEED_ARTICLE_CACHE_VERSION + 1)
                .toString()

        assertNull(decodeFeedArticleCacheDocument(obsolete))
    }

    @Test
    fun discardsMalformedJsonInsteadOfCrashing() {
        assertNull(decodeFeedArticleCacheDocument("not json"))
    }

    @Test
    fun discardsAnArticleWithAMalformedFeedIdInsteadOfCrashingTheWholeDocument() {
        val json =
            JSONObject(encodeFeedArticleCacheDocument(FeedArticleCacheDocument.empty()))
                .put(
                    "feeds",
                    org.json.JSONArray().put(JSONObject().put("feedId", "").put("fetchedAtEpochMillis", 1L)),
                )
                .toString()

        val decoded = decodeFeedArticleCacheDocument(json)

        assertTrue(decoded != null && decoded.feeds.isEmpty())
    }
}
