package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedStage
import com.riffle.core.domain.launcher.rss.FeedStageItem
import com.riffle.core.domain.launcher.rss.FeedStageLifecycle
import com.riffle.core.domain.launcher.rss.FeedStageSnapshot
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import java.net.URI

/**
 * Display content of one cached article, already sanitized to plain text by the platform layer (the
 * feed cache can hold markup, see `stripHtmlMarkup`). Transient: never stored by the launcher.
 */
data class FeedArticleContent(
    /** Opaque item digest shared with [FeedStageItem.digest]; the join key. */
    val digest: String,
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val canonicalUrl: String? = null,
    /** An image URL was cached for the article; resolving the pixels stays a lazy platform concern. */
    val hasImage: Boolean = false,
)

/**
 * Maps the existing feed projection to [Item]s without re-deriving any feed rule: which feeds appear, in
 * which order, with which lifecycle and which articles in which order all come from [FeedStageSnapshot]
 * (`FeedStagePlanner`). Profile-locked feeds therefore never expose content, and a stale feed keeps its
 * cached articles, flagged through `rss.stale`.
 *
 * Feed content is public, so items are [com.riffle.core.domain.launcher.workspace.ItemPrivacy.VISIBLE].
 */
class FeedItemMapper {
    /**
     * One item per planned article that has content in [articles] (an evicted or dismissed article simply has
     * none and is skipped). Feeds keep the planner's order, then each feed's own article order.
     */
    fun feedItems(
        snapshot: FeedStageSnapshot,
        feeds: List<FeedConfiguration>,
        articles: Map<FeedId, List<FeedArticleContent>>,
    ): List<Item> {
        val labels = feeds.associate { feed -> feed.id to feed.sourceLabel() }
        return snapshot.stages.flatMap { stage ->
            val feedId = stage.id.feedId
            val byDigest = articles[feedId].orEmpty().associateBy(FeedArticleContent::digest)
            val label = labels[feedId] ?: DEFAULT_LABEL
            stage.exposedItems().mapNotNull { item ->
                byDigest[item.digest]?.toItem(feedId, label, item, stale = stage.lifecycle == FeedStageLifecycle.STALE)
            }
        }
    }

    private fun FeedStage.exposedItems(): List<FeedStageItem> =
        if (lifecycle == FeedStageLifecycle.PROFILE_LOCKED) emptyList() else items

    private fun FeedArticleContent.toItem(
        feedId: FeedId,
        label: String,
        stageItem: FeedStageItem,
        stale: Boolean,
    ): Item =
        Item(
            id = ItemId("${SourceIds.RSS.value}:${feedId.value}:$digest"),
            sourceId = SourceIds.RSS,
            target = canonicalUrl?.let { url -> ItemTarget.DeepLink(url) } ?: ItemTarget.None,
            title = title.takeIf(String::isNotBlank),
            subtitle = label,
            body = summary?.let(::snippet),
            image = if (hasImage) ItemImageKeys.feedArtwork(digest) else null,
            timeEpochMillis = stageItem.publishedAtEpochMillis?.takeIf { time -> time >= 0L },
            groupKey = feedId.value,
            groupLabel = label,
            actions = listOf(ItemAction.Open()),
            ext = extras(stale),
        )

    private fun FeedArticleContent.extras(stale: Boolean): Map<ItemExtKey, ItemExtValue> =
        buildMap {
            author?.takeIf(String::isNotBlank)?.let { name -> put(AUTHOR_KEY, ItemExtValue.Text(name)) }
            put(STALE_KEY, ItemExtValue.Flag(stale))
        }

    private fun snippet(summary: String): String? {
        val text = summary.trim()
        return when {
            text.isEmpty() -> null
            text.length <= MAX_SNIPPET_LENGTH -> text
            else -> text.take(MAX_SNIPPET_LENGTH - 1).trimEnd() + "…"
        }
    }

    companion object {
        /** Body snippets are cut here; the full summary stays in the cache. */
        const val MAX_SNIPPET_LENGTH = 280

        private const val DEFAULT_LABEL = "Feed"
        val AUTHOR_KEY = ItemExtKey("rss.author")
        val STALE_KEY = ItemExtKey("rss.stale")
    }
}

/** The feed's host without a leading `www.`, the only human name a configured feed has. */
fun FeedConfiguration.sourceLabel(): String =
    runCatching { URI(url.value).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?.takeIf(String::isNotBlank)
        ?: "Feed"
