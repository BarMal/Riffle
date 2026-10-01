package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.rss.FeedCacheFreshness
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedProfileStatus
import com.riffle.core.domain.launcher.rss.FeedStageCacheProjection
import com.riffle.core.domain.launcher.rss.FeedStageItem
import com.riffle.core.domain.launcher.rss.FeedStagePlanner
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedItemMapperTest {
    private val mapper = FeedItemMapper()
    private val planner = FeedStagePlanner()

    private fun feed(
        name: String,
        host: String = "www.$name.example",
        profile: AppProfile = AppProfile.personal(),
    ) = FeedConfiguration(
        id = FeedId(name),
        url = FeedUrl.parse("https://$host/feed.xml").getOrThrow(),
        profile = profile,
    )

    private fun stageItem(
        digest: String,
        time: Long?,
        order: Int,
    ) = FeedStageItem(digest, time, order)

    private fun content(
        digest: String,
        title: String = "Title $digest",
        summary: String? = null,
        url: String? = "https://news.example/$digest",
        image: Boolean = false,
    ) = FeedArticleContent(digest, title, summary = summary, canonicalUrl = url, hasImage = image)

    private fun cached(
        vararg items: FeedStageItem,
        freshness: FeedCacheFreshness = FeedCacheFreshness.FRESH,
    ) = FeedStageCacheProjection.Cached(freshness, items.toList())

    @Test
    fun `maps an article to an item with feed grouping and a deep link`() {
        val news = feed("news")
        val projection = cached(stageItem("a", 5_000, 0))
        val snapshot = planner.reconcile(listOf(news), cacheProjections = mapOf(news.id to projection))

        val items =
            mapper.feedItems(
                snapshot,
                listOf(news),
                mapOf(news.id to listOf(content("a", summary = "Short body", image = true).copy(author = "Ann"))),
            )

        val item = items.single()
        assertEquals("rss:news:a", item.id.value)
        assertEquals(SourceIds.RSS, item.sourceId)
        assertEquals(ItemTarget.DeepLink("https://news.example/a"), item.target)
        assertEquals("Title a", item.title)
        assertEquals("news.example", item.subtitle)
        assertEquals("Short body", item.body)
        assertEquals(ItemImageKeys.feedArtwork("a"), item.image)
        assertEquals(5_000L, item.timeEpochMillis)
        assertEquals("news", item.groupKey)
        assertEquals("news.example", item.groupLabel)
        assertEquals(ItemPrivacy.VISIBLE, item.privacy)
        assertEquals(ItemExtValue.Text("Ann"), item.ext[FeedItemMapper.AUTHOR_KEY])
        assertEquals(ItemExtValue.Flag(false), item.ext[FeedItemMapper.STALE_KEY])
    }

    @Test
    fun `keeps the planner order for feeds and articles and drops articles without content`() {
        val a = feed("a")
        val b = feed("b")
        val projections =
            mapOf(
                a.id to cached(stageItem("old", 1, 0), stageItem("new", 9, 1), stageItem("gone", 5, 2)),
                b.id to cached(stageItem("b1", 3, 0)),
            )
        val snapshot = planner.reconcile(listOf(a, b), cacheProjections = projections)

        val items =
            mapper.feedItems(
                snapshot,
                listOf(a, b),
                mapOf(a.id to listOf(content("old"), content("new")), b.id to listOf(content("b1"))),
            )

        assertEquals(listOf("rss:a:new", "rss:a:old", "rss:b:b1"), items.map { it.id.value })
    }

    @Test
    fun `stale feeds keep their articles and flag them`() {
        val news = feed("news")
        val snapshot =
            planner.reconcile(
                listOf(news),
                cacheProjections = mapOf(news.id to cached(stageItem("a", 1, 0), freshness = FeedCacheFreshness.STALE)),
            )

        val item = mapper.feedItems(snapshot, listOf(news), mapOf(news.id to listOf(content("a")))).single()

        assertEquals(ItemExtValue.Flag(true), item.ext[FeedItemMapper.STALE_KEY])
    }

    @Test
    fun `locked profiles expose nothing and removed ones are absent`() {
        val work = feed("work", profile = AppProfile.work())
        val gone = feed("gone", profile = AppProfile.private())
        val snapshot =
            planner.reconcile(
                listOf(work, gone),
                profileStatuses =
                    mapOf(
                        work.profile.id to FeedProfileStatus.LOCKED,
                        gone.profile.id to FeedProfileStatus.REMOVED,
                    ),
                cacheProjections =
                    mapOf(work.id to cached(stageItem("w", 1, 0)), gone.id to cached(stageItem("g", 1, 0))),
            )

        val items =
            mapper.feedItems(
                snapshot,
                listOf(work, gone),
                mapOf(work.id to listOf(content("w")), gone.id to listOf(content("g"))),
            )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `no feeds, never fetched, and failed feeds all yield no items`() {
        val news = feed("news")
        assertTrue(mapper.feedItems(planner.reconcile(emptyList()), emptyList(), emptyMap()).isEmpty())

        val loading = planner.reconcile(listOf(news))
        assertTrue(mapper.feedItems(loading, listOf(news), emptyMap()).isEmpty())

        val projection = FeedStageCacheProjection.FetchFailed
        val failed = planner.reconcile(listOf(news), cacheProjections = mapOf(news.id to projection))
        assertTrue(mapper.feedItems(failed, listOf(news), emptyMap()).isEmpty())
    }

    @Test
    fun `articles without a url have no target and long summaries become snippets`() {
        val news = feed("news")
        val projection = cached(stageItem("a", null, 0))
        val snapshot = planner.reconcile(listOf(news), cacheProjections = mapOf(news.id to projection))
        val long = "word ".repeat(100)

        val articles = mapOf(news.id to listOf(content("a", summary = long, url = null)))
        val item = mapper.feedItems(snapshot, listOf(news), articles).single()

        assertEquals(ItemTarget.None, item.target)
        assertNull(item.timeEpochMillis)
        assertNull(item.image)
        assertEquals(FeedItemMapper.MAX_SNIPPET_LENGTH, item.body!!.length)
        assertTrue(item.body!!.endsWith("…"))
    }

    @Test
    fun `source label is the host without www`() {
        assertEquals("blog.example", feed("x", host = "www.blog.example").sourceLabel())
        assertEquals("blog.example", feed("x", host = "BLOG.example").sourceLabel())
    }
}
