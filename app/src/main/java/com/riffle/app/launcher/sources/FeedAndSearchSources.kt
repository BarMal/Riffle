package com.riffle.app.launcher.sources

import com.riffle.app.launcher.rss.CacheFreshness
import com.riffle.app.launcher.rss.CachedFeedArticle
import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.app.launcher.rss.FeedCacheResult
import com.riffle.app.launcher.rss.NoopFeedArticleCacheRepository
import com.riffle.app.launcher.rss.stripHtmlMarkup
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.rss.ConfiguredFeedSource
import com.riffle.core.domain.launcher.rss.FeedAvailability
import com.riffle.core.domain.launcher.rss.FeedCacheFreshness
import com.riffle.core.domain.launcher.rss.FeedProfileStatus
import com.riffle.core.domain.launcher.rss.FeedStageCacheProjection
import com.riffle.core.domain.launcher.rss.FeedStageItem
import com.riffle.core.domain.launcher.rss.FeedStagePlanner
import com.riffle.core.domain.launcher.rss.NoConfiguredFeedSource
import com.riffle.core.domain.launcher.search.LauncherSearchProvider
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.sources.FeedArticleContent
import com.riffle.core.domain.launcher.workspace.sources.FeedItemMapper
import com.riffle.core.domain.launcher.workspace.sources.SearchItemMapper
import com.riffle.core.domain.launcher.workspace.sources.SearchQueryHolder
import com.riffle.core.domain.launcher.workspace.sources.SharedSourceStream

/**
 * What the RSS source reads: the user's configured feeds and the offline article cache. There is no network
 * here by design (see docs/product/workspaces-sources-rss-search.md); the user-triggered refresh (#1374)
 * fills the cache and reports through [changes], which is what makes the source `LIVE`.
 */
internal class FeedSourceDependencies(
    val configuredFeeds: ConfiguredFeedSource,
    val cache: FeedArticleCacheRepository,
    /** Locked or removed profiles; unknown profiles count as available. */
    val profileStatuses: () -> Map<AppProfileId, FeedProfileStatus> = { emptyMap() },
    val changes: SourceChangeSource = SourceChangeSource.NONE,
) {
    companion object {
        /** No feeds and an empty cache: the source reports an empty list. */
        val NONE = FeedSourceDependencies(NoConfiguredFeedSource, NoopFeedArticleCacheRepository)
    }
}

/** What the search source reads besides the apps snapshot: the shared query and the settings entries. */
internal class SearchSourceDependencies(
    /** Written by a search-box UI or container; the source only observes it. */
    val query: SearchQueryHolder = SearchQueryHolder(),
    val settingsEntries: () -> List<LauncherSearchSettingsEntry> = { emptyList() },
)

/** Feed and search inputs for [androidItemSources]; both default to "nothing configured / nothing typed". */
internal class ContentSourceDependencies(
    val feeds: FeedSourceDependencies = FeedSourceDependencies.NONE,
    val search: SearchSourceDependencies = SearchSourceDependencies(),
)

/**
 * Groupable, and `LIVE` only when [FeedSourceDependencies.changes] can report cache changes (the user-triggered
 * refresh does). It reads the cache and never touches the network itself.
 */
internal fun feedSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    stream(deps, SourceIds.RSS, setOf(SourceCapability.GROUPABLE), deps.feeds.changes) {
        SourceState.Ready(feedItems(deps.feeds))
    }

/** Searchable and live on the query holder: it re-queries when the text changes and never otherwise. */
internal fun searchSource(deps: BuiltInSourceDependencies): SharedSourceStream =
    stream(
        deps,
        SourceIds.SEARCH,
        setOf(SourceCapability.SEARCHABLE),
        SourceChangeSource { onChanged -> deps.search.query.observe(onChanged) },
    ) {
        val query = deps.search.query.current()
        if (query.isEmpty()) {
            SourceState.Ready(emptyList())
        } else {
            deps.apps.installedApps.snapshot()
                ?.let { snapshot -> SourceState.Ready(searchItems(deps, query, snapshot)) }
                ?: SourceState.Unavailable
        }
    }

private fun searchItems(
    deps: BuiltInSourceDependencies,
    query: String,
    snapshot: InstalledAppSnapshot,
) = SearchItemMapper().items(
    LauncherSearchProvider().search(
        query = query,
        apps = snapshot.apps,
        settingsEntries = deps.search.settingsEntries(),
        shortcutsByApp = deps.apps.shortcuts.shortcutsFor(InstalledAppCatalog().visibleApps(snapshot.apps)),
    ),
)

private fun feedItems(deps: FeedSourceDependencies): List<Item> {
    val feeds = deps.configuredFeeds.configuredFeeds()
    val statuses = deps.profileStatuses()
    val readable = feeds.filter { feed -> feed.availability(statuses) == FeedAvailability.ENABLED }
    val cached = readable.associate { feed -> feed.id to deps.cache.loadFeed(feed.id) }
    val snapshot =
        FeedStagePlanner().reconcile(
            configuredFeeds = feeds,
            profileStatuses = statuses,
            cacheProjections = cached.mapValues { (_, result) -> result.toProjection() },
        )
    return FeedItemMapper().feedItems(snapshot, feeds, cached.mapValues { (_, result) -> result.toContent() })
}

private fun FeedCacheResult.toProjection(): FeedStageCacheProjection =
    when (this) {
        FeedCacheResult.Empty -> FeedStageCacheProjection.NeverAttempted
        is FeedCacheResult.Available ->
            FeedStageCacheProjection.Cached(
                freshness = snapshot.freshness.toDomain(),
                items = snapshot.feed.articles.map(::stageItem),
            )
    }

private fun CacheFreshness.toDomain(): FeedCacheFreshness =
    if (this == CacheFreshness.STALE) FeedCacheFreshness.STALE else FeedCacheFreshness.FRESH

private fun stageItem(article: CachedFeedArticle) =
    FeedStageItem(article.digest, article.publishedAtEpochMillis, article.sourceOrder)

private fun FeedCacheResult.toContent(): List<FeedArticleContent> =
    when (this) {
        FeedCacheResult.Empty -> emptyList()
        is FeedCacheResult.Available -> snapshot.feed.articles.map(::articleContent)
    }

/** Cached text can hold markup, so it is reduced to plain text like every other feed renderer does. */
private fun articleContent(article: CachedFeedArticle) =
    FeedArticleContent(
        digest = article.digest,
        title = stripHtmlMarkup(article.title),
        author = article.author?.let(::stripHtmlMarkup)?.takeIf(String::isNotBlank),
        summary = article.summary?.let(::stripHtmlMarkup)?.takeIf(String::isNotBlank),
        canonicalUrl = article.canonicalUrl,
        hasImage = article.imageUrl != null,
    )
