package com.riffle.core.domain.launcher.rss

import com.riffle.core.domain.launcher.apps.AppProfileId

/**
 * Projects one [FeedStage] per configured feed, deterministically ordered, honestly reflecting
 * loading/empty/stale/unavailable/error/profile-locked states.
 *
 * Unlike [com.riffle.core.domain.launcher.cards.AppStagePlanner], every enabled, non-removed-profile
 * [FeedConfiguration] always produces a stage -- feed stages are never pruned for being empty (a
 * feed stage "can remain visible when empty" per ADR 0001), so there is no dynamic-content gating
 * to reconcile. This keeps focus retention trivial: as long as a feed's configuration entry and
 * profile remain, its stage identity survives reconciliation regardless of lifecycle changes.
 */
class FeedStagePlanner {
    fun reconcile(
        configuredFeeds: List<FeedConfiguration>,
        profileStatuses: Map<AppProfileId, FeedProfileStatus> = emptyMap(),
        cacheProjections: Map<FeedId, FeedStageCacheProjection> = emptyMap(),
        preferences: FeedStagePreferences = FeedStagePreferences(),
        previous: FeedStageSnapshot? = null,
    ): FeedStageSnapshot {
        val configOrder = configuredFeeds.map(FeedConfiguration::id)
        val stages =
            configuredFeeds
                .distinctBy(FeedConfiguration::id)
                .mapNotNull { configuration -> stageFor(configuration, profileStatuses, cacheProjections) }
                .sortedWith(stageOrder(preferences.pinnedStageIds, configOrder))
        val availableIds = stages.map(FeedStage::id).toSet()
        return FeedStageSnapshot(
            stages = stages,
            preferences =
                FeedStagePreferences(
                    pinnedStageIds = preferences.pinnedStageIds.distinct().filter { id -> id in availableIds },
                    selectedStageId = selectedStageId(preferences.selectedStageId, availableIds, stages, previous),
                ),
        )
    }
}

private fun stageFor(
    configuration: FeedConfiguration,
    profileStatuses: Map<AppProfileId, FeedProfileStatus>,
    cacheProjections: Map<FeedId, FeedStageCacheProjection>,
): FeedStage? {
    val availability = configuration.availability(profileStatuses)
    if (availability == FeedAvailability.PROFILE_REMOVED) return null

    val projection = cacheProjections[configuration.id] ?: FeedStageCacheProjection.NeverAttempted
    val lifecycle = lifecycleFor(availability, projection)
    val items =
        if (lifecycle == FeedStageLifecycle.PROFILE_LOCKED) {
            emptyList()
        } else {
            (projection as? FeedStageCacheProjection.Cached)?.items.orEmpty().sortedWith(itemOrder)
        }
    return FeedStage(
        id = FeedStageId(configuration.id),
        lifecycle = lifecycle,
        items = items,
    )
}

private fun lifecycleFor(
    availability: FeedAvailability,
    projection: FeedStageCacheProjection,
): FeedStageLifecycle =
    when (availability) {
        FeedAvailability.PROFILE_LOCKED -> FeedStageLifecycle.PROFILE_LOCKED
        FeedAvailability.PROFILE_REMOVED ->
            error("Removed-profile feeds must be filtered out before lifecycle resolution.")
        FeedAvailability.DISABLED -> FeedStageLifecycle.UNAVAILABLE
        FeedAvailability.ENABLED ->
            when (projection) {
                FeedStageCacheProjection.NeverAttempted -> FeedStageLifecycle.LOADING
                FeedStageCacheProjection.FetchFailed -> FeedStageLifecycle.ERROR
                is FeedStageCacheProjection.Cached ->
                    when {
                        projection.items.isEmpty() -> FeedStageLifecycle.EMPTY
                        projection.freshness == FeedCacheFreshness.STALE -> FeedStageLifecycle.STALE
                        else -> FeedStageLifecycle.ACTIVE
                    }
            }
    }

/**
 * Prefers the requested selection, then falls back to the previous selection if it is still
 * present (a feed stage becoming empty/stale/error never disappears on its own, but this keeps
 * selection resilient across a feed configuration briefly dropping out and returning), then the
 * first stage, then nothing.
 */
private fun selectedStageId(
    requested: FeedStageId?,
    availableIds: Set<FeedStageId>,
    stages: List<FeedStage>,
    previous: FeedStageSnapshot?,
): FeedStageId? {
    val previousSelection = previous?.preferences?.selectedStageId
    return when {
        requested != null && requested in availableIds -> requested
        previousSelection != null && previousSelection in availableIds -> previousSelection
        else -> stages.firstOrNull()?.id
    }
}

private fun stageOrder(
    pinnedIds: List<FeedStageId>,
    configOrder: List<FeedId>,
): Comparator<FeedStage> =
    compareBy<FeedStage> { stage -> pinnedIds.indexOf(stage.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        .thenBy { stage -> configOrder.indexOf(stage.id.feedId).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        .thenBy { stage -> stage.id.feedId.value }

/** Mirrors `FeedItemNormalizer`'s display order: newest published first, undated items by source order. */
private val itemOrder =
    Comparator<FeedStageItem> { left, right ->
        when {
            left.publishedAtEpochMillis != null && right.publishedAtEpochMillis != null ->
                compareValuesBy(right, left, FeedStageItem::publishedAtEpochMillis, FeedStageItem::digest)
            left.publishedAtEpochMillis != null -> -1
            right.publishedAtEpochMillis != null -> 1
            else -> left.sourceOrder.compareTo(right.sourceOrder)
        }
    }
