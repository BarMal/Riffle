package com.riffle.app.launcher

import com.riffle.app.launcher.rss.CachedFeedArticle
import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.app.launcher.rss.FeedCacheResult
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.RssSettings
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
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
    fun persistsReturnBehaviorSelectionAndDefaultsToRestore() {
        val repository = FakeLauncherSettingsRepository()
        assertEquals(ReturnBehavior.RESTORE, LauncherShellState().launcherSettings.home.returnBehavior)

        val updatedState =
            reducer(launcherSettingsRepository = repository).reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SelectReturnBehavior(ReturnBehavior.START_PAGE),
            )

        assertEquals(ReturnBehavior.START_PAGE, updatedState.launcherSettings.home.returnBehavior)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun persistsScreenContentProtectionAndDefaultsToOff() {
        val repository = FakeLauncherSettingsRepository()
        assertEquals(false, LauncherShellState().launcherSettings.privacy.protectScreenContent)

        val updatedState =
            reducer(launcherSettingsRepository = repository).reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SetScreenContentProtection(enabled = true),
            )

        assertEquals(true, updatedState.launcherSettings.privacy.protectScreenContent)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun persistsTheLibraryViewPerDeviceClassAndCoercesGridColumns() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)

        val iconState =
            reducer.reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SelectLibraryView(LibraryView.Grid),
            )
        val tabletState =
            reducer.reduce(
                state = iconState,
                action = LauncherShellAction.SelectLibraryView(LibraryView.AlphaList, HomeLayoutDeviceClass.TABLET),
            )
        val updatedState =
            reducer.reduce(
                state = iconState,
                action = LauncherShellAction.SelectAppDrawerIconGridColumns(columns = 99),
            )

        val phone = HomeLayoutDeviceClass.PHONE
        assertEquals(LibraryView.Grid, updatedState.launcherSettings.appDrawer.libraryViewFor(phone))
        assertEquals(
            LibraryView.AlphaList,
            tabletState.launcherSettings.appDrawer.libraryViewFor(HomeLayoutDeviceClass.TABLET),
        )
        assertEquals(LibraryView.Grid, tabletState.launcherSettings.appDrawer.libraryViewFor(phone))
        assertEquals(6, updatedState.launcherSettings.appDrawer.iconGridColumns)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun fullscreenHomeActionHidesBothSystemBars() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings = LauncherSettings(appearance = AppearanceSettings(hideStatusBarOnHome = true)),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectFullscreenHomeEnabled(enabled = true),
            )

        assertEquals(
            HomeSystemBars(hideStatusBarOnHome = true, hideNavigationBarOnHome = true),
            updatedState.launcherSettings.appearance.homeSystemBars,
        )
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun clearingFullscreenHomeShowsBothSystemBars() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance = AppearanceSettings(hideStatusBarOnHome = true, hideNavigationBarOnHome = true),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectFullscreenHomeEnabled(enabled = false),
            )

        assertEquals(HomeSystemBars(), updatedState.launcherSettings.appearance.homeSystemBars)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun theTwoBarSwitchesAreIndependent() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance = AppearanceSettings(hideStatusBarOnHome = true, hideNavigationBarOnHome = true),
                    ),
            )

        val updatedState =
            reducer.reduce(
                state = state,
                action = LauncherShellAction.SelectHomeNavigationBarHidden(hidden = false),
            )

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
                action = LauncherShellAction.SelectRssRefreshInterval(FeedRefreshIntervalOption.HOURS_1),
            )

        assertEquals(FeedRefreshIntervalOption.HOURS_1, updatedState.launcherSettings.rss.refreshInterval)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun persistsRssBackgroundConstraintToggles() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)

        val wifiOff =
            reducer.reduce(LauncherShellState(), LauncherShellAction.SetRssBackgroundWifiOnly(enabled = false))
        val chargingOn =
            reducer.reduce(wifiOff, LauncherShellAction.SetRssBackgroundChargingOnly(enabled = true))

        assertEquals(false, chargingOn.launcherSettings.rss.backgroundWifiOnly)
        assertEquals(true, chargingOn.launcherSettings.rss.backgroundChargingOnly)
        assertEquals(chargingOn.launcherSettings, repository.savedSettings)
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
