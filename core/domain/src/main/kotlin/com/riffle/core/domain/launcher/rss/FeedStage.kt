package com.riffle.core.domain.launcher.rss

/**
 * Stable identity for one AdaptiveStage feed stage. One [FeedStage] projects from exactly one
 * [FeedConfiguration], keyed by its already profile-scoped [FeedId].
 */
@JvmInline
value class FeedStageId(val feedId: FeedId)

/**
 * Honest lifecycle for a projected feed stage. Unlike [com.riffle.core.domain.launcher.cards.AppStage],
 * a feed stage is projected for every non-removed-profile [FeedConfiguration] -- it is never pruned
 * for being empty, so pin state does not gate its presence.
 *
 * - [LOADING]: the feed is enabled and available but has never been fetched into the offline cache.
 * - [ACTIVE]: fresh cached content with at least one item.
 * - [EMPTY]: fresh cached content, but the feed genuinely has zero items.
 * - [STALE]: cached content is being served past its freshness window (e.g. a refresh failed or
 *   has not run recently) but at least one item is available -- honest "serving stale content".
 * - [UNAVAILABLE]: the feed is disabled by configuration.
 * - [ERROR]: a fetch/parse attempt was made and failed, and there is no cached content to fall
 *   back on. This is distinct from [UNAVAILABLE] (a deliberate configuration choice) and from
 *   [STALE] (a failure with usable cached content) because there is nothing honest to show yet.
 * - [PROFILE_LOCKED]: the owning profile is currently locked; content must not be exposed.
 *
 * A feed whose profile is REMOVED does not get a lifecycle at all -- [FeedStagePlanner] drops the
 * stage entirely, mirroring how a removed profile drops an [com.riffle.core.domain.launcher.cards.AppStage].
 */
enum class FeedStageLifecycle {
    LOADING,
    ACTIVE,
    EMPTY,
    STALE,
    UNAVAILABLE,
    ERROR,
    PROFILE_LOCKED,
}

/** Local, domain-level mirror of the offline cache's freshness signal (see [FeedStageCacheProjection]). */
enum class FeedCacheFreshness {
    FRESH,
    STALE,
}

/**
 * A single ordered, presentation-neutral reference to a cached article. Full card content (author,
 * summary, image) is intentionally out of scope here -- rendering is owned by a later slice. This
 * carries just enough to prove and test deterministic ordering.
 */
data class FeedStageItem(
    val digest: String,
    val publishedAtEpochMillis: Long?,
    val sourceOrder: Int,
)

/**
 * The result of asking the offline cache boundary for a feed's content, expressed as a
 * domain-only projection so [FeedStagePlanner] never depends on the app-module cache repository
 * or its types directly (`core:domain` cannot depend on `:app`). Callers adapt
 * `FeedArticleCacheRepository`/`FeedCacheResult` results into this shape.
 */
sealed interface FeedStageCacheProjection {
    /** The feed has never been fetched (or its cache was cleared) and no fetch attempt failed. */
    data object NeverAttempted : FeedStageCacheProjection

    /** The most recent fetch attempt failed and there is no cached content to fall back on. */
    data object FetchFailed : FeedStageCacheProjection

    /** Cached content is available, possibly stale. */
    data class Cached(
        val freshness: FeedCacheFreshness,
        val items: List<FeedStageItem>,
    ) : FeedStageCacheProjection
}

/** One reconciled feed stage. */
data class FeedStage(
    val id: FeedStageId,
    val lifecycle: FeedStageLifecycle,
    val items: List<FeedStageItem> = emptyList(),
) {
    init {
        require(items.map(FeedStageItem::digest).distinct().size == items.size) {
            "Feed stage item digests must be unique."
        }
    }
}

/** Persistable user choices only, mirroring [com.riffle.core.domain.launcher.cards.AppStagePreferences]. */
data class FeedStagePreferences(
    val pinnedStageIds: List<FeedStageId> = emptyList(),
    val selectedStageId: FeedStageId? = null,
) {
    fun pin(stageId: FeedStageId): FeedStagePreferences =
        if (stageId in pinnedStageIds) this else copy(pinnedStageIds = pinnedStageIds + stageId)

    fun unpin(stageId: FeedStageId): FeedStagePreferences = copy(pinnedStageIds = pinnedStageIds - stageId)

    fun select(stageId: FeedStageId?): FeedStagePreferences = copy(selectedStageId = stageId)
}

/** Reconciled transient feed projection paired with the sanitized durable intent needed to recreate it. */
data class FeedStageSnapshot(
    val stages: List<FeedStage>,
    val preferences: FeedStagePreferences,
) {
    init {
        require(stages.map(FeedStage::id).distinct().size == stages.size) { "Feed stage ids must be unique." }
        require(preferences.selectedStageId == null || preferences.selectedStageId in stages.map(FeedStage::id)) {
            "Selected feed stage must be available."
        }
    }

    val selectedStage: FeedStage?
        get() = stages.firstOrNull { it.id == preferences.selectedStageId }

    fun isPinned(id: FeedStageId): Boolean = id in preferences.pinnedStageIds
}
