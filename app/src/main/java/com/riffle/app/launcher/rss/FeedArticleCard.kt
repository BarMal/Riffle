package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedStage

/**
 * Renderer-local join of a thin, ordering-only [com.riffle.core.domain.launcher.rss.FeedStageItem]
 * against the rich [CachedFeedArticle] display fields sourced from [FeedArticleCacheRepository],
 * keyed by their shared opaque digest. This mirrors how `AdaptiveStageNotificationStack` joins thin
 * `AppStageContent` identifiers against full `AppStageNotificationCard` data by id
 * (`AdaptiveStageAppStageSurface.kt`).
 *
 * [title], [author], and [summary] are always the sanitized, tag-stripped display forms -- never
 * the raw cached text -- so every renderer that consumes this model can pass them straight to a
 * plain Compose `Text()` without re-sanitizing.
 */
data class FeedArticleCard(
    val digest: String,
    val title: String,
    val author: String?,
    val publishedAtEpochMillis: Long?,
    val summary: String?,
    val canonicalUrl: String?,
    val sourceOrder: Int,
)

/**
 * Joins this stage's ordered item digests against [articles], dropping any item whose cached
 * article is missing (e.g. evicted or cleared) rather than inventing display data for it. The
 * domain-owned [FeedStage.items] order (publication time, then stable source order) is preserved.
 */
fun FeedStage.joinArticleCards(articles: List<CachedFeedArticle>): List<FeedArticleCard> {
    val articlesByDigest = articles.associateBy(CachedFeedArticle::digest)
    return items.mapNotNull { item -> articlesByDigest[item.digest]?.toFeedArticleCard(item.sourceOrder) }
}

private fun CachedFeedArticle.toFeedArticleCard(sourceOrder: Int): FeedArticleCard =
    FeedArticleCard(
        digest = digest,
        title = stripHtmlMarkup(title),
        author = author?.let(::stripHtmlMarkup)?.takeIf(String::isNotBlank),
        publishedAtEpochMillis = publishedAtEpochMillis,
        summary = summary?.let(::stripHtmlMarkup)?.takeIf(String::isNotBlank),
        canonicalUrl = canonicalUrl,
        sourceOrder = sourceOrder,
    )
