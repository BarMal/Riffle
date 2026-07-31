package com.riffle.core.domain.launcher.rss

import com.riffle.core.domain.launcher.apps.AppProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedStagePlannerTest {
    private val planner = FeedStagePlanner()

    @Test
    fun projectsOneStagePerConfiguredFeedInConfigurationOrderByDefault() {
        val mail = feed("mail")
        val news = feed("news")

        val snapshot = planner.reconcile(configuredFeeds = listOf(mail, news))

        assertEquals(listOf(FeedStageId(mail.id), FeedStageId(news.id)), snapshot.stages.map(FeedStage::id))
        assertEquals(FeedStageLifecycle.LOADING, snapshot.stages[0].lifecycle)
    }

    @Test
    fun missingCacheBindingShowsLoadingNotACrash() {
        val neverFetched = feed("never-fetched")

        val snapshot = planner.reconcile(configuredFeeds = listOf(neverFetched))

        assertEquals(FeedStageLifecycle.LOADING, snapshot.stages.single().lifecycle)
        assertEquals(emptyList(), snapshot.stages.single().items)
    }

    @Test
    fun fetchFailureWithNoCacheReportsErrorDistinctFromUnavailable() {
        val broken = feed("broken")
        val disabled = feed("disabled").copy(enabled = false)

        val snapshot =
            planner.reconcile(
                configuredFeeds = listOf(broken, disabled),
                cacheProjections = mapOf(broken.id to FeedStageCacheProjection.FetchFailed),
            )

        assertEquals(FeedStageLifecycle.ERROR, snapshot.stages[0].lifecycle)
        assertEquals(FeedStageLifecycle.UNAVAILABLE, snapshot.stages[1].lifecycle)
    }

    @Test
    fun staleCacheWithItemsIsStaleButStaleEmptyCacheIsEmpty() {
        val stale = feed("stale")
        val staleEmpty = feed("stale-empty")

        val snapshot =
            planner.reconcile(
                configuredFeeds = listOf(stale, staleEmpty),
                cacheProjections =
                    mapOf(
                        stale.id to
                            FeedStageCacheProjection.Cached(
                                freshness = FeedCacheFreshness.STALE,
                                items = listOf(item("a", 100L, 0)),
                            ),
                        staleEmpty.id to
                            FeedStageCacheProjection.Cached(freshness = FeedCacheFreshness.STALE, items = emptyList()),
                    ),
            )

        assertEquals(FeedStageLifecycle.STALE, snapshot.stages[0].lifecycle)
        assertEquals(FeedStageLifecycle.EMPTY, snapshot.stages[1].lifecycle)
    }

    @Test
    fun freshCacheWithItemsIsActiveAndOrdersItemsNewestFirst() {
        val active = feed("active")

        val snapshot =
            planner.reconcile(
                configuredFeeds = listOf(active),
                cacheProjections =
                    mapOf(
                        active.id to
                            FeedStageCacheProjection.Cached(
                                freshness = FeedCacheFreshness.FRESH,
                                items = listOf(item("old", 10L, 1), item("new", 20L, 0)),
                            ),
                    ),
            )

        val stage = snapshot.stages.single()
        assertEquals(FeedStageLifecycle.ACTIVE, stage.lifecycle)
        assertEquals(listOf("new", "old"), stage.items.map(FeedStageItem::digest))
    }

    @Test
    fun coexistsWithPinnedOrderingAheadOfConfigurationOrder() {
        val mail = feed("mail")
        val news = feed("news")
        val sports = feed("sports")

        val snapshot =
            planner.reconcile(
                configuredFeeds = listOf(mail, news, sports),
                preferences = FeedStagePreferences(pinnedStageIds = listOf(FeedStageId(sports.id))),
            )

        assertEquals(
            listOf(FeedStageId(sports.id), FeedStageId(mail.id), FeedStageId(news.id)),
            snapshot.stages.map(FeedStage::id),
        )
    }

    @Test
    fun emptyPinnedFeedStageRemainsVisible() {
        val empty = feed("empty")

        val snapshot =
            planner.reconcile(
                configuredFeeds = listOf(empty),
                cacheProjections =
                    mapOf(empty.id to FeedStageCacheProjection.Cached(FeedCacheFreshness.FRESH, emptyList())),
                preferences = FeedStagePreferences(pinnedStageIds = listOf(FeedStageId(empty.id))),
            )

        assertEquals(FeedStageLifecycle.EMPTY, snapshot.stages.single().lifecycle)
        assertEquals(listOf(FeedStageId(empty.id)), snapshot.preferences.pinnedStageIds)
    }

    @Test
    fun focusIsRetainedAcrossAFeedStageBecomingEmptyThenStaleThenErroring() {
        val mail = feed("mail")
        val freshWithItem = FeedStageCacheProjection.Cached(FeedCacheFreshness.FRESH, listOf(item("a", 1L, 0)))
        val freshEmpty = FeedStageCacheProjection.Cached(FeedCacheFreshness.FRESH, emptyList())
        val staleWithItem = FeedStageCacheProjection.Cached(FeedCacheFreshness.STALE, listOf(item("a", 1L, 0)))

        val initial =
            planner.reconcile(
                configuredFeeds = listOf(mail),
                cacheProjections = mapOf(mail.id to freshWithItem),
                preferences = FeedStagePreferences(selectedStageId = FeedStageId(mail.id)),
            )

        val becameEmpty =
            planner.reconcile(
                configuredFeeds = listOf(mail),
                cacheProjections = mapOf(mail.id to freshEmpty),
                preferences = initial.preferences,
                previous = initial,
            )
        val becameStale =
            planner.reconcile(
                configuredFeeds = listOf(mail),
                cacheProjections = mapOf(mail.id to staleWithItem),
                preferences = becameEmpty.preferences,
                previous = becameEmpty,
            )
        val becameError =
            planner.reconcile(
                configuredFeeds = listOf(mail),
                cacheProjections = mapOf(mail.id to FeedStageCacheProjection.FetchFailed),
                preferences = becameStale.preferences,
                previous = becameStale,
            )

        assertEquals(FeedStageId(mail.id), becameEmpty.selectedStage?.id)
        assertEquals(FeedStageId(mail.id), becameStale.selectedStage?.id)
        assertEquals(FeedStageId(mail.id), becameError.selectedStage?.id)
        assertEquals(FeedStageLifecycle.ERROR, becameError.selectedStage?.lifecycle)
    }

    @Test
    fun selectionFallsBackWhenAFeedConfigurationIsRemovedThenToPreviousThenToFirstStage() {
        val mail = feed("mail")
        val news = feed("news")
        val initial =
            planner.reconcile(
                configuredFeeds = listOf(mail, news),
                preferences = FeedStagePreferences(selectedStageId = FeedStageId(mail.id)),
            )

        val mailRemoved =
            planner.reconcile(
                configuredFeeds = listOf(news),
                preferences = initial.preferences,
                previous = initial,
            )

        assertEquals(FeedStageId(news.id), mailRemoved.selectedStage?.id)
    }

    @Test
    fun profileLockedFeedHidesCachedItemsButKeepsTheStageVisible() {
        val work = feed("mail", AppProfile.work())

        val snapshot =
            planner.reconcile(
                configuredFeeds = listOf(work),
                profileStatuses = mapOf(work.profile.id to FeedProfileStatus.LOCKED),
                cacheProjections =
                    mapOf(
                        work.id to
                            FeedStageCacheProjection.Cached(FeedCacheFreshness.FRESH, listOf(item("a", 1L, 0))),
                    ),
                preferences = FeedStagePreferences(selectedStageId = FeedStageId(work.id)),
            )

        assertEquals(FeedStageLifecycle.PROFILE_LOCKED, snapshot.selectedStage?.lifecycle)
        assertEquals(emptyList(), snapshot.selectedStage?.items)
    }

    @Test
    fun removedProfileDropsTheFeedStageEntirelyAndUnpinsIt() {
        val personal = feed("mail")
        val work = feed("chat", AppProfile.work())

        val snapshot =
            planner.reconcile(
                configuredFeeds = listOf(personal, work),
                profileStatuses = mapOf(work.profile.id to FeedProfileStatus.REMOVED),
                preferences = FeedStagePreferences(pinnedStageIds = listOf(FeedStageId(work.id))),
            )

        assertEquals(listOf(FeedStageId(personal.id)), snapshot.stages.map(FeedStage::id))
        assertEquals(emptyList(), snapshot.preferences.pinnedStageIds)
    }

    @Test
    fun treatsPersonalAndWorkFeedsOfTheSameUrlAsIndependentStages() {
        val personal = feed("mail", AppProfile.personal())
        val work = feed("mail", AppProfile.work())

        val snapshot = planner.reconcile(configuredFeeds = listOf(personal, work))

        assertTrue(snapshot.stages.map(FeedStage::id).toSet().size == 2)
    }

    @Test
    fun producesEmptySnapshotForNoConfiguredFeeds() {
        val snapshot = planner.reconcile(configuredFeeds = emptyList())

        assertEquals(emptyList(), snapshot.stages)
        assertNull(snapshot.preferences.selectedStageId)
    }

    private fun feed(
        name: String,
        profile: AppProfile = AppProfile.personal(),
    ): FeedConfiguration =
        FeedConfiguration(
            id = FeedId("$name:${profile.id.value}"),
            url = FeedUrl.parse("https://example.com/$name").getOrThrow(),
            profile = profile,
        )

    private fun item(
        digest: String,
        publishedAtEpochMillis: Long?,
        sourceOrder: Int,
    ): FeedStageItem =
        FeedStageItem(
            digest = digest,
            publishedAtEpochMillis = publishedAtEpochMillis,
            sourceOrder = sourceOrder,
        )
}
