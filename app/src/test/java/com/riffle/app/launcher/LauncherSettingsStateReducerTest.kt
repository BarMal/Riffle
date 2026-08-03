package com.riffle.app.launcher

import com.riffle.app.launcher.rss.CachedFeedArticle
import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.app.launcher.rss.FeedCacheResult
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.cards.TimeScapePaneArrangement
import com.riffle.core.domain.launcher.cards.TimeScapeRailSide
import com.riffle.core.domain.launcher.contextual.ContextualSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.AppDrawerPresentation
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.RssSettings
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.homeSystemBars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingsStateReducerTest {
    @Test
    fun appliesSettingsStateActions() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state = LauncherShellState()

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectHapticFeedbackStrength(HapticFeedbackStrength.STRONG),
            )

        assertEquals(HapticFeedbackStrength.STRONG, updatedState.launcherSettings.haptics.feedbackStrength)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun persistsSearchResultPresentationSelection() {
        val repository = FakeLauncherSettingsRepository()

        val updatedState =
            reducer(launcherSettingsRepository = repository).reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SelectSearchResultPresentation(SearchResultPresentation.LIST),
            )

        assertEquals(SearchResultPresentation.LIST, updatedState.launcherSettings.search.resultPresentation)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun persistsAppDrawerPresentationAndCoercesGridColumns() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)

        val iconState =
            reducer.reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SelectAppDrawerPresentation(AppDrawerPresentation.ICONS),
            )
        val updatedState =
            reducer.reduce(
                state = iconState,
                action = LauncherShellAction.SelectAppDrawerIconGridColumns(columns = 99),
            )

        assertEquals(AppDrawerPresentation.ICONS, updatedState.launcherSettings.appDrawer.presentation)
        assertEquals(6, updatedState.launcherSettings.appDrawer.iconGridColumns)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun replacesAndCoercesTheCompleteTimeScapeProfileAtomically() {
        val repository = FakeLauncherSettingsRepository()
        val requested =
            TimeScapeAppearanceSettings.modern().copy(
                geometry = TimeScapeAppearanceSettings.modern().geometry.copy(visibleDepth = 99),
                surface = TimeScapeAppearanceSettings.modern().surface.copy(blurStrengthPercent = -5),
            )

        val updatedState =
            reducer(launcherSettingsRepository = repository).reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.UpdateTimeScapeAppearance(requested),
            )

        assertEquals(6, updatedState.launcherSettings.cards.timeScapeAppearance.geometry.visibleDepth)
        assertEquals(0, updatedState.launcherSettings.cards.timeScapeAppearance.surface.blurStrengthPercent)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun persistsTimeScapePaneArrangementSelection() {
        val repository = FakeLauncherSettingsRepository()

        val updatedState =
            reducer(launcherSettingsRepository = repository).reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SelectTimeScapePaneArrangement(TimeScapePaneArrangement.SPLIT),
            )

        assertEquals(TimeScapePaneArrangement.SPLIT, updatedState.launcherSettings.cards.timeScapePaneArrangement)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun persistsTimeScapeRailSideSelection() {
        val repository = FakeLauncherSettingsRepository()

        val updatedState =
            reducer(launcherSettingsRepository = repository).reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SelectTimeScapeRailSide(TimeScapeRailSide.TOP),
            )

        assertEquals(TimeScapeRailSide.TOP, updatedState.launcherSettings.cards.timeScapeRailSide)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun contextualSettingsDefaultOff() {
        val state = LauncherShellState()

        assertEquals(false, state.launcherSettings.contextual.enabled)
    }

    @Test
    fun enablesContextualSettingsAndPersistsSelection() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)

        val updatedState =
            reducer.reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SelectContextualEnabled(enabled = true),
            )

        assertEquals(true, updatedState.launcherSettings.contextual.enabled)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun disablesContextualSettingsAndPersistsSelection() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val enabledState =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        contextual = ContextualSettings(enabled = true),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = enabledState,
                action = LauncherShellAction.SelectContextualEnabled(enabled = false),
            )

        assertEquals(false, updatedState.launcherSettings.contextual.enabled)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun fullscreenHomeSelectionPreservesIndependentSystemBarSettings() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance = AppearanceSettings(hideStatusBarOnHome = true),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectFullscreenHomeEnabled(enabled = true),
            )

        assertEquals(true, updatedState.launcherSettings.appearance.fullscreenHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideStatusBarOnHome)
        assertEquals(false, updatedState.launcherSettings.appearance.hideNavigationBarOnHome)
        assertEquals(
            HomeSystemBars(
                fullscreenHome = true,
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = false,
            ),
            updatedState.launcherSettings.appearance.homeSystemBars,
        )
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun fullscreenHomeClearingRestoresIndependentSystemBarSelection() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance =
                            AppearanceSettings(
                                fullscreenHome = true,
                                hideStatusBarOnHome = true,
                            ),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectFullscreenHomeEnabled(enabled = false),
            )

        assertEquals(false, updatedState.launcherSettings.appearance.fullscreenHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideStatusBarOnHome)
        assertEquals(false, updatedState.launcherSettings.appearance.hideNavigationBarOnHome)
        assertEquals(
            HomeSystemBars(
                fullscreenHome = false,
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = false,
            ),
            updatedState.launcherSettings.appearance.homeSystemBars,
        )
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun independentSystemBarSelectionUpdatesFullscreenHomeWhenBothAreHidden() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance = AppearanceSettings(hideNavigationBarOnHome = true),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectHomeStatusBarHidden(hidden = true),
            )

        assertEquals(true, updatedState.launcherSettings.appearance.fullscreenHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideStatusBarOnHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideNavigationBarOnHome)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun independentSystemBarSelectionClearsFullscreenHomeWhenOneBarIsVisible() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance =
                            AppearanceSettings(
                                fullscreenHome = true,
                                hideStatusBarOnHome = true,
                                hideNavigationBarOnHome = true,
                            ),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectHomeNavigationBarHidden(hidden = false),
            )

        assertEquals(false, updatedState.launcherSettings.appearance.fullscreenHome)
        assertEquals(true, updatedState.launcherSettings.appearance.hideStatusBarOnHome)
        assertEquals(false, updatedState.launcherSettings.appearance.hideNavigationBarOnHome)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun ignoresSettingsSideEffectActions() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state = LauncherShellState()

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.RequestNotificationAccess,
            )

        assertSame(state, updatedState)
        assertEquals(null, repository.savedSettings)
    }

    @Test
    fun ignoresNonSettingsActions() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state = LauncherShellState()

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.RefreshInstalledApps,
            )

        assertSame(state, updatedState)
        assertEquals(null, repository.savedSettings)
    }

    @Test
    fun addsRssFeedAndSyncsConfiguredFeeds() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val url = FeedUrl.parse("https://example.com/feed.xml").getOrThrow()

        val updatedState =
            reducer.reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.AddRssFeed(url),
            )

        assertEquals(1, updatedState.launcherSettings.rss.feeds.size)
        assertEquals(url, updatedState.launcherSettings.rss.feeds.single().url)
        assertEquals(updatedState.launcherSettings.rss.feeds, updatedState.configuredFeeds)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun removingRssFeedClearsItsCacheAndSyncsConfiguredFeeds() {
        val repository = FakeLauncherSettingsRepository()
        val cacheRepository = FakeFeedArticleCacheRepository()
        val reducer =
            reducer(launcherSettingsRepository = repository, feedArticleCacheRepository = cacheRepository)
        val feed =
            FeedConfiguration(id = FeedId("feed-1"), url = FeedUrl.parse("https://example.com/feed.xml").getOrThrow())
        val state = LauncherShellState(launcherSettings = LauncherSettings(rss = RssSettings(feeds = listOf(feed))))

        val updatedState =
            reducer.reduce(state = state, action = LauncherShellAction.RemoveRssFeed(feed.id))

        assertEquals(emptyList<FeedConfiguration>(), updatedState.launcherSettings.rss.feeds)
        assertEquals(emptyList<FeedConfiguration>(), updatedState.configuredFeeds)
        assertEquals(listOf(feed.id), cacheRepository.clearedFeedIds)
    }

    @Test
    fun disablingRssFeedClearsItsCacheButEnablingDoesNot() {
        val cacheRepository = FakeFeedArticleCacheRepository()
        val reducer = reducer(feedArticleCacheRepository = cacheRepository)
        val feed =
            FeedConfiguration(id = FeedId("feed-1"), url = FeedUrl.parse("https://example.com/feed.xml").getOrThrow())
        val state = LauncherShellState(launcherSettings = LauncherSettings(rss = RssSettings(feeds = listOf(feed))))

        val disabledState =
            reducer.reduce(state = state, action = LauncherShellAction.SetRssFeedEnabled(feed.id, enabled = false))

        assertEquals(false, disabledState.launcherSettings.rss.feeds.single().enabled)
        assertEquals(listOf(feed.id), cacheRepository.clearedFeedIds)

        val reEnabledState =
            reducer.reduce(
                state = disabledState,
                action = LauncherShellAction.SetRssFeedEnabled(feed.id, enabled = true),
            )

        assertEquals(true, reEnabledState.launcherSettings.rss.feeds.single().enabled)
        assertEquals(listOf(feed.id), cacheRepository.clearedFeedIds)
    }

    @Test
    fun persistsRssRefreshIntervalSelection() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)

        val updatedState =
            reducer.reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SelectRssRefreshInterval(FeedRefreshIntervalOption.MINUTES_30),
            )

        assertEquals(FeedRefreshIntervalOption.MINUTES_30, updatedState.launcherSettings.rss.refreshInterval)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun addingADuplicateRssFeedIsIgnoredAndDoesNotDuplicateConfiguredFeeds() {
        val reducer = reducer()
        val url = FeedUrl.parse("https://example.com/feed.xml").getOrThrow()
        val firstState =
            reducer.reduce(state = LauncherShellState(), action = LauncherShellAction.AddRssFeed(url))

        val secondState = reducer.reduce(state = firstState, action = LauncherShellAction.AddRssFeed(url))

        assertEquals(1, secondState.launcherSettings.rss.feeds.size)
        assertTrue(secondState.configuredFeeds.size == 1)
    }

    private fun reducer(
        launcherSettingsRepository: LauncherSettingsRepository = FakeLauncherSettingsRepository(),
        feedArticleCacheRepository: FeedArticleCacheRepository = FakeFeedArticleCacheRepository(),
    ): LauncherSettingsStateReducer =
        LauncherSettingsStateReducer(
            homeLayoutRepository = FakeHomeLayoutRepository(),
            launcherSettingsRepository = launcherSettingsRepository,
            appVisibilityRepository = FakeAppVisibilityRepository(),
            feedArticleCacheRepository = feedArticleCacheRepository,
        )

    private class FakeFeedArticleCacheRepository : FeedArticleCacheRepository {
        val clearedFeedIds = mutableListOf<FeedId>()

        override fun loadFeed(
            feedId: FeedId,
            staleAfterMillis: Long,
        ): FeedCacheResult = FeedCacheResult.Empty

        override fun replaceFeed(
            feedId: FeedId,
            articles: List<CachedFeedArticle>,
        ) = Unit

        override fun clearFeed(feedId: FeedId) {
            clearedFeedIds += feedId
        }

        override fun isRead(digest: String): Boolean = false

        override fun markRead(digest: String) = Unit

        override fun isDismissed(digest: String): Boolean = false

        override fun markDismissed(digest: String) = Unit

        override fun cachedImage(digest: String): ByteArray? = null

        override fun cacheImage(
            digest: String,
            bytes: ByteArray,
        ) = Unit

        override fun clear() = Unit
    }

    private class FakeHomeLayoutRepository : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = null

        override fun saveHomeLayout(layout: HomeLayout) = Unit
    }

    private class FakeLauncherSettingsRepository(
        var savedSettings: LauncherSettings? = null,
    ) : LauncherSettingsRepository {
        override fun loadLauncherSettings(): LauncherSettings? = savedSettings

        override fun saveLauncherSettings(settings: LauncherSettings) {
            savedSettings = settings
        }
    }

    private class FakeAppVisibilityRepository : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }
}
