package com.riffle.core.domain.launcher.rss

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FeedModelTest {
    @Test
    fun feedUrlNormalizesHttpsHostPathAndTrackingParameters() {
        val url = FeedUrl.parse(" HTTPS://News.Example.com?utm_source=mail&topic=android ").getOrThrow()

        assertEquals("https://news.example.com/?topic=android", url.value)
    }

    @Test
    fun feedUrlRejectsBlankNonHttpsUserinfoAndMalformedHosts() {
        listOf(
            " ",
            "http://example.com/feed",
            "https://user@example.com/feed",
            "https:///feed",
            "https://example.com/feed#latest",
        )
            .forEach { raw -> assertTrue(FeedUrl.parse(raw).isFailure, raw) }
    }

    @Test
    fun feedAvailabilityReflectsDisabledLockedAndRemovedProfiles() {
        val feed = configuration()
        val profileId = feed.profile.id

        assertEquals(FeedAvailability.ENABLED, feed.availability(emptyMap()))
        assertEquals(FeedAvailability.DISABLED, feed.copy(enabled = false).availability(emptyMap()))
        assertEquals(
            FeedAvailability.PROFILE_LOCKED,
            feed.availability(mapOf(profileId to FeedProfileStatus.LOCKED)),
        )
        assertEquals(
            FeedAvailability.PROFILE_REMOVED,
            feed.availability(mapOf(profileId to FeedProfileStatus.REMOVED)),
        )
    }

    @Test
    fun normalizerDropsMalformedItemsDeduplicatesAndBoundsItems() {
        val result =
            FeedItemNormalizer.normalize(
                format = FeedFormat.RSS_2,
                feedTitle = "  Android   News ",
                maxItems = 2,
                items =
                    listOf(
                        FeedItemInput(sourceId = "one", title = " First ", publishedAt = "2026-07-29T10:00:00Z"),
                        FeedItemInput(sourceId = "one", title = " Duplicate ", publishedAt = "2026-07-29T09:00:00Z"),
                        FeedItemInput(sourceId = "two", title = "Second", publishedAt = "not-a-date"),
                        FeedItemInput(sourceId = "three", title = "Third"),
                        FeedItemInput(sourceId = "bad", title = "   "),
                    ),
            )

        assertEquals("Android News", result.title)
        assertEquals(listOf("First", "Second"), result.items.map { item -> item.title })
        assertEquals(Instant.parse("2026-07-29T10:00:00Z"), result.items.first().publishedAt)
        assertEquals(null, result.items[1].publishedAt)
    }

    @Test
    fun normalizerCapsInputBeforeProcessingAndCapsRequestedOutput() {
        val lateItem =
            FeedItemInput(
                sourceId = "late-item",
                title = "Late item must not be processed",
                publishedAt = "2026-07-30T00:00:00Z",
            )
        val items =
            List(MAX_FEED_INPUT_ITEMS) { index ->
                FeedItemInput(sourceId = "item-$index", title = "Item $index")
            } + lateItem

        val result =
            FeedItemNormalizer.normalize(
                format = FeedFormat.RSS_2,
                feedTitle = "Feed",
                items = items,
                maxItems = Int.MAX_VALUE,
            )

        assertEquals(MAX_FEED_ITEM_LIMIT, result.items.size)
        assertTrue(result.items.none { item -> item.identity == lateItem.sourceId })
    }

    @Test
    fun normalizerRejectsOversizedTitleAndBoundsOtherUntrustedFields() {
        val oversizedUrl = "https://example.com/" + "x".repeat(MAX_FEED_URL_LENGTH)
        val oversizedRawDate = " ".repeat(MAX_FEED_PUBLISHED_AT_LENGTH + 1) + "2026-07-29T10:00:00Z"
        val result =
            FeedItemNormalizer.normalize(
                format = FeedFormat.ATOM,
                feedTitle = "F".repeat(MAX_FEED_TITLE_LENGTH + 1),
                items =
                    listOf(
                        FeedItemInput(
                            sourceId = "S".repeat(MAX_FEED_SOURCE_ID_LENGTH + 1),
                            canonicalUrl = oversizedUrl,
                            title = "Valid title",
                            author = "A".repeat(MAX_FEED_AUTHOR_LENGTH + 1),
                            publishedAt = oversizedRawDate,
                            summary = "M".repeat(MAX_FEED_SUMMARY_LENGTH + 1),
                            imageUrl = oversizedUrl,
                        ),
                        FeedItemInput(title = " ".repeat(MAX_FEED_TITLE_LENGTH + 1) + "Valid after whitespace"),
                    ),
            )

        val item = result.items.single()
        assertEquals("", result.title)
        assertEquals(null, item.canonicalUrl)
        assertEquals(null, item.author)
        assertEquals(null, item.publishedAt)
        assertEquals(null, item.summary)
        assertEquals(null, item.imageUrl)
        assertTrue(item.identity.length <= MAX_FEED_SOURCE_ID_LENGTH)
    }

    @Test
    fun datedItemsSortNewestFirstAndUndatedItemsKeepSourceOrder() {
        val result =
            FeedItemNormalizer.normalize(
                format = FeedFormat.ATOM,
                feedTitle = "Feed",
                items =
                    listOf(
                        FeedItemInput(sourceId = "undated-a", title = "A"),
                        FeedItemInput(sourceId = "dated-old", title = "Old", publishedAt = "2026-07-28T00:00:00Z"),
                        FeedItemInput(sourceId = "undated-b", title = "B"),
                        FeedItemInput(sourceId = "dated-new", title = "New", publishedAt = "2026-07-29T00:00:00Z"),
                    ),
            )

        assertEquals(listOf("New", "Old", "A", "B"), result.items.map { item -> item.title })
    }

    @Test
    fun intentDigestIsStableOpaqueAndChangesWithFeedOrItemIdentity() {
        val feed = configuration()
        val item =
            FeedItemNormalizer.normalize(
                FeedFormat.RSS_2,
                "Feed",
                listOf(FeedItemInput(sourceId = "article-1", title = "Title")),
            ).items.single()

        val digest = FeedItemIntentDigest.forItem(feed, item)
        assertEquals(64, digest.value.length)
        assertTrue(digest.value.all { character -> character in '0'..'9' || character in 'a'..'f' })
        assertTrue("example.com" !in digest.value)
        assertEquals(digest, FeedItemIntentDigest.forItem(feed, item.copy(title = "Changed")))
        assertNotEquals(digest, FeedItemIntentDigest.forItem(feed, item.copy(identity = "article-2")))
        val workFeed =
            feed.copy(
                profile = AppProfile(AppProfileId("work"), feed.profile.type),
            )
        assertNotEquals(digest, FeedItemIntentDigest.forItem(workFeed, item))
    }

    @Test
    fun blankFeedIdCannotBeConstructed() {
        assertFailsWith<IllegalArgumentException> { FeedId("") }
    }

    private fun configuration(): FeedConfiguration =
        FeedConfiguration(
            id = FeedId("news"),
            url = FeedUrl.parse("https://example.com/feed?utm_source=test").getOrThrow(),
        )
}
