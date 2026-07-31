package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedStage
import com.riffle.core.domain.launcher.rss.FeedStageId
import com.riffle.core.domain.launcher.rss.FeedStageItem
import com.riffle.core.domain.launcher.rss.FeedStageLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedArticleCardTest {
    @Test
    fun joinPreservesStageItemOrderAndDropsMissingArticles() {
        val stage =
            FeedStage(
                id = FeedStageId(FeedId("feed-1")),
                lifecycle = FeedStageLifecycle.ACTIVE,
                items =
                    listOf(
                        FeedStageItem(digest = digest(1), publishedAtEpochMillis = 200L, sourceOrder = 0),
                        FeedStageItem(digest = digest(2), publishedAtEpochMillis = 100L, sourceOrder = 1),
                        // digest(3) has no matching cached article -- e.g. evicted after the stage
                        // snapshot was reconciled.
                        FeedStageItem(digest = digest(3), publishedAtEpochMillis = 50L, sourceOrder = 2),
                    ),
            )
        val articles =
            listOf(
                article(digest = digest(1), title = "First"),
                article(digest = digest(2), title = "Second"),
            )

        val cards = stage.joinArticleCards(articles)

        assertEquals(listOf(digest(1), digest(2)), cards.map { card -> card.digest })
        assertEquals(listOf("First", "Second"), cards.map { card -> card.title })
    }

    @Test
    fun joinSanitizesTitleAuthorAndSummaryButLeavesCanonicalUrlAlone() {
        val stage =
            FeedStage(
                id = FeedStageId(FeedId("feed-1")),
                lifecycle = FeedStageLifecycle.ACTIVE,
                items = listOf(FeedStageItem(digest = digest(1), publishedAtEpochMillis = null, sourceOrder = 0)),
            )
        val articles =
            listOf(
                article(digest = digest(1), title = "<b>Bold</b> title").copy(
                    author = "<i>Author</i>",
                    summary = "<p>Body &amp; more</p>",
                    canonicalUrl = "https://example.com/a?x=1",
                ),
            )

        val card = stage.joinArticleCards(articles).single()

        assertEquals("Bold title", card.title)
        assertEquals("Author", card.author)
        assertEquals("Body & more", card.summary)
        assertEquals("https://example.com/a?x=1", card.canonicalUrl)
    }

    @Test
    fun joinBlankSanitizedAuthorAndSummaryBecomeNull() {
        val stage =
            FeedStage(
                id = FeedStageId(FeedId("feed-1")),
                lifecycle = FeedStageLifecycle.ACTIVE,
                items = listOf(FeedStageItem(digest = digest(1), publishedAtEpochMillis = null, sourceOrder = 0)),
            )
        val articles =
            listOf(
                article(digest = digest(1)).copy(
                    author = "<div></div>",
                    summary = "   ",
                ),
            )

        val card = stage.joinArticleCards(articles).single()

        assertNull(card.author)
        assertNull(card.summary)
    }

    @Test
    fun emptyStageProducesNoCards() {
        val stage = FeedStage(id = FeedStageId(FeedId("feed-1")), lifecycle = FeedStageLifecycle.EMPTY)

        assertTrue(stage.joinArticleCards(listOf(article(digest = digest(1)))).isEmpty())
    }

    private fun digest(seed: Int): String = seed.toString().padStart(64, '0')

    private fun article(
        digest: String,
        title: String = "Title",
    ): CachedFeedArticle =
        CachedFeedArticle(
            digest = digest,
            title = title,
            sourceOrder = 0,
        )
}
