package com.riffle.app.launcher

import com.riffle.app.launcher.rss.CachedFeedArticle
import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.app.launcher.rss.FeedCacheResult
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.designsystem.CardTint
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.search.wider.SearchSourceId
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.AppsBehindCards
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.DockMotionSettings
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HintSettings
import com.riffle.core.domain.launcher.settings.HomeMode
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.LauncherFeature
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.LayoutPosture
import com.riffle.core.domain.launcher.settings.LayoutSettings
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.settings.PageIndicatorPlacement
import com.riffle.core.domain.launcher.settings.PageScrollDirection
import com.riffle.core.domain.launcher.settings.PostureLayoutSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.settings.RssSettings
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import com.riffle.core.domain.launcher.settings.SearchSettings
import com.riffle.core.domain.launcher.settings.StreamSettings
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.summon.SUMMON_ARM_MILLIS_RANGE
import com.riffle.core.domain.launcher.summon.SUMMON_ICON_SIZE_DP_RANGE
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.SummonTimings
import com.riffle.core.domain.launcher.summon.collection
import com.riffle.core.domain.launcher.summon.homeFolderCollectionId
import com.riffle.core.domain.launcher.summon.summonSeedApps
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
    fun persistsTheSearchSourceSettings() {
        val repository = FakeLauncherSettingsRepository()
        val changed =
            SearchSettings().withSource(SearchSourceId.CONTACTS, true).movedGroup(SearchSourceId.FILES, -1)

        val updatedState =
            reducer(launcherSettingsRepository = repository).reduce(
                state = LauncherShellState(),
                action = LauncherShellAction.SetSearchSettings(changed),
            )

        assertEquals(changed, updatedState.launcherSettings.search)
        assertEquals(updatedState.launcherSettings, repository.savedSettings)
    }

    @Test
    fun persistsTheShowPairsMode() {
        val repository = FakeLauncherSettingsRepository()

        val updatedState =
            reducer(launcherSettingsRepository = repository).reduce(
                state = LauncherShellState(),
                action =
                    LauncherShellAction.SelectPairsMode(
                        com.riffle.core.domain.launcher.settings.PairsMode.OFF,
                    ),
            )

        assertEquals(
            com.riffle.core.domain.launcher.settings.PairsMode.OFF,
            updatedState.launcherSettings.stream.showPairs,
        )
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
    fun persistsTheStreamCardSettingsAndHoldsTheTintToItsRange() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        assertEquals(StreamSettings(), LauncherShellState().launcherSettings.stream)

        val tinted = reducer.reduce(LauncherShellState(), LauncherShellAction.SelectStreamCardTint(34))
        val glowing = reducer.reduce(tinted, LauncherShellAction.SetStreamCardTopGlow(enabled = true))
        val placed = reducer.reduce(glowing, LauncherShellAction.SelectOpenCardPlacement(OpenCardPlacement.ABOVE_DOCK))
        val acting =
            reducer.reduce(placed, LauncherShellAction.SelectCardActionsPlacement(CardActionsPlacement.ON_CARD))
        val clamped = reducer.reduce(acting, LauncherShellAction.SelectStreamCardTint(900))

        assertEquals(34, tinted.launcherSettings.stream.cardTintPercent)
        assertEquals(true, glowing.launcherSettings.stream.cardTopGlow)
        assertEquals(OpenCardPlacement.ABOVE_DOCK, placed.launcherSettings.stream.openCardPlacement)
        assertEquals(CardActionsPlacement.ON_CARD, acting.launcherSettings.stream.cardActionsPlacement)
        assertEquals(CardTint.MAX_STRENGTH_PERCENT, clamped.launcherSettings.stream.cardTintPercent)
        assertEquals(clamped.launcherSettings, repository.savedSettings)
        // Everything the earlier choices set is kept.
        assertEquals(OpenCardPlacement.ABOVE_DOCK, clamped.launcherSettings.stream.openCardPlacement)
    }

    @Test
    fun homeFoldersAddedToTheRingAreSavedAsCollectionCopies() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val folder =
            FolderItem(
                LauncherItemId("f1"),
                "Social",
                listOf(
                    AppShortcutItem(
                        LauncherItemId("a"),
                        AppIdentity(AppPackageName("pkg.chat"), AppActivityName("pkg.chat.Main")),
                        "Chat",
                    ),
                ),
            )
        val start = LauncherShellState()
        val layout = start.homeLayout
        val withFolder =
            start.copy(
                homeLayout =
                    layout.copy(
                        pages =
                            layout.pages.mapIndexed {
                                    i,
                                    p,
                                ->
                                if (i == 0) p.copy(items = p.items + folder) else p
                            },
                    ),
            )
        val saved =
            reducer.reduce(
                withFolder,
                LauncherShellAction.SetSummonSettings(SummonSettings(slots = listOf(SummonSlot.Folder(folder.id)))),
            )
        val id = homeFolderCollectionId(folder.id)
        assertEquals(listOf<SummonSlot>(SummonSlot.Collection(id)), saved.launcherSettings.summon.slots)
        assertEquals("Social", saved.launcherSettings.summon.collection(id)?.name)
    }

    @Test
    fun summonSeedsItsRingOnceAndLeavesEverythingElseForWhenItIsTurnedOff() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val start = LauncherShellState()

        val on = reducer.reduce(start, LauncherShellAction.SetSummonMode(true))
        assertEquals(true, on.launcherSettings.features.summon)
        assertEquals(
            summonSeedApps(start.homeLayout).take(7).size + 1,
            on.launcherSettings.summon.slots.size,
        )
        assertEquals(on.launcherSettings, repository.savedSettings)

        val edited =
            reducer.reduce(
                on,
                LauncherShellAction.SetSummonSettings(
                    on.launcherSettings.summon.withSlotCount(
                        5,
                    ).copy(timings = SummonTimings(armMillis = 1), iconSizeDp = 999),
                ),
            )
        assertEquals(5, edited.launcherSettings.summon.slotCount)
        assertEquals(SUMMON_ARM_MILLIS_RANGE.first, edited.launcherSettings.summon.timings.armMillis)
        assertEquals(SUMMON_ICON_SIZE_DP_RANGE.last, edited.launcherSettings.summon.iconSizeDp)

        // Off and on again keeps the user's ring rather than seeding a new one.
        val off = reducer.reduce(edited, LauncherShellAction.SetSummonMode(false))
        assertEquals(false, off.launcherSettings.features.summon)
        assertEquals(start.launcherSettings.copy(summon = edited.launcherSettings.summon), off.launcherSettings)
        val again = reducer.reduce(off, LauncherShellAction.SetSummonMode(true))
        assertEquals(edited.launcherSettings.summon, again.launcherSettings.summon)
    }

    @Test
    fun persistsAddNewAppsTheFeatureSwitchesAndSimpleMode() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val start = LauncherShellState()
        assertEquals(HomeMode.PlacedItems, start.launcherSettings.home.mode)
        assertEquals(true, start.launcherSettings.home.addNewApps)
        assertEquals(FeatureSettings(), start.launcherSettings.features)

        val library = reducer.reduce(start, LauncherShellAction.SetAddNewApps(false))
        val noStream = reducer.reduce(library, LauncherShellAction.SetFeatureEnabled(LauncherFeature.STREAM, false))
        val noLive =
            reducer.reduce(noStream, LauncherShellAction.SetFeatureEnabled(LauncherFeature.DOCK_LIVE_APPS, false))
        val noLibrary = reducer.reduce(noLive, LauncherShellAction.SetFeatureEnabled(LauncherFeature.LIBRARY, false))
        val simple = reducer.reduce(noLibrary, LauncherShellAction.SetSimpleMode(true))

        assertEquals(false, library.launcherSettings.home.addNewApps)
        assertEquals(false, noStream.launcherSettings.features.stream)
        assertEquals(false, noLive.launcherSettings.features.dockLiveApps)
        assertEquals(false, noLibrary.launcherSettings.features.library)
        assertEquals(true, simple.launcherSettings.features.simpleMode)
        assertEquals(simple.launcherSettings, repository.savedSettings)

        // Simple mode off gives every earlier choice back untouched, and a switch back on is just that switch.
        val off = reducer.reduce(simple, LauncherShellAction.SetSimpleMode(false))
        assertEquals(noLibrary.launcherSettings, off.launcherSettings)
        val again = reducer.reduce(off, LauncherShellAction.SetAddNewApps(true))
        assertEquals(true, again.launcherSettings.home.addNewApps)
        assertEquals(off.launcherSettings.features, again.launcherSettings.features)
    }

    @Test
    fun persistsEachPosturesLayoutOnItsOwn() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val start = LauncherShellState()
        assertEquals(LayoutSettings(), start.launcherSettings.layout)

        val vertical = LauncherShellAction.SelectPageScroll(LayoutPosture.FOLDED, PageScrollDirection.VERTICAL)
        val off = LauncherShellAction.SelectPageIndicator(LayoutPosture.UNFOLDED, PageIndicatorPlacement.OFF)
        val scrolled = reducer.reduce(start, vertical)
        val hidden = reducer.reduce(scrolled, off)
        val still = reducer.reduce(hidden, LauncherShellAction.SetQuickScroll(LayoutPosture.UNFOLDED, false))

        assertEquals(PageScrollDirection.VERTICAL, scrolled.launcherSettings.layout.folded.pageScroll)
        assertEquals(PostureLayoutSettings.UNFOLDED_DEFAULT, scrolled.launcherSettings.layout.unfolded)
        assertEquals(PageIndicatorPlacement.OFF, hidden.launcherSettings.layout.unfolded.indicator)
        assertEquals(PageScrollDirection.VERTICAL, hidden.launcherSettings.layout.folded.pageScroll)
        assertEquals(false, still.launcherSettings.layout.unfolded.quickScroll)
        assertEquals(true, still.launcherSettings.layout.folded.quickScroll)
        assertEquals(still.launcherSettings, repository.savedSettings)
    }

    @Test
    fun persistsAppsBehindTheCardsPerPostureAsAChoiceBehindAndAroundUnfoldedAndHiddenFoldedByDefault() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val start = LauncherShellState()
        assertEquals(AppsBehindCards.BEHIND_AND_AROUND, start.launcherSettings.layout.unfolded.appsBehind)
        assertEquals(AppsBehindCards.HIDDEN, start.launcherSettings.layout.folded.appsBehind)

        val around =
            reducer.reduce(
                start,
                LauncherShellAction.SetAppsBehindCards(LayoutPosture.UNFOLDED, AppsBehindCards.AROUND),
            )

        assertEquals(AppsBehindCards.AROUND, around.launcherSettings.layout.unfolded.appsBehind)
        assertEquals(AppsBehindCards.HIDDEN, around.launcherSettings.layout.folded.appsBehind)
        assertEquals(around.launcherSettings, repository.savedSettings)
        val back =
            reducer.reduce(
                around,
                LauncherShellAction.SetAppsBehindCards(LayoutPosture.UNFOLDED, AppsBehindCards.BEHIND_AND_AROUND),
            )
        assertEquals(LayoutSettings(), back.launcherSettings.layout)

        val foldedOn =
            reducer.reduce(start, LauncherShellAction.SetAppsBehindCards(LayoutPosture.FOLDED, AppsBehindCards.AROUND))
        assertEquals(AppsBehindCards.AROUND, foldedOn.launcherSettings.layout.folded.appsBehind)
        assertEquals(AppsBehindCards.BEHIND_AND_AROUND, foldedOn.launcherSettings.layout.unfolded.appsBehind)
        assertEquals(foldedOn.launcherSettings, repository.savedSettings)
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

    @Test
    fun persistsTheDockMotionSettingsHoldsThemToTheirRangesAndKeepsTheRest() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        val reduced =
            reducer.reduce(
                LauncherShellState(),
                LauncherShellAction.SelectReducedMotionPreference(ReducedMotionPreference.ON),
            )
        assertEquals(DockMotionSettings(), reduced.launcherSettings.motion.dock)

        val tuned =
            reducer.reduce(
                reduced,
                LauncherShellAction.SetDockMotion(DockMotionSettings(speedMillis = 320, ticks = false)),
            )
        val clamped =
            reducer.reduce(
                tuned,
                LauncherShellAction.SetDockMotion(DockMotionSettings(speedMillis = 5, inertiaPercent = 900)),
            )

        assertEquals(320, tuned.launcherSettings.motion.dock.speedMillis)
        assertEquals(false, tuned.launcherSettings.motion.dock.ticks)
        assertEquals(DockMotionSettings.MIN_SPEED_MILLIS, clamped.launcherSettings.motion.dock.speedMillis)
        assertEquals(DockMotionSettings.MAX_INERTIA_PERCENT, clamped.launcherSettings.motion.dock.inertiaPercent)
        // Reduced motion is its own setting and still on.
        assertEquals(ReducedMotionPreference.ON, clamped.launcherSettings.motion.reducedMotionPreference)
        assertEquals(clamped.launcherSettings, repository.savedSettings)
    }

    @Test
    fun showingHintsAgainClearsTheStoredPressHintFlag() {
        val repository = FakeLauncherSettingsRepository()
        val reducer = reducer(launcherSettingsRepository = repository)
        // The flag is only ever read from storage now: nothing sets it any more.
        val dismissed =
            LauncherShellState(launcherSettings = LauncherSettings(hints = HintSettings(dockPressSeen = true)))

        val again = reducer.reduce(dismissed, LauncherShellAction.ShowHintsAgain)

        assertEquals(false, again.launcherSettings.hints.dockPressSeen)
        assertEquals(again.launcherSettings, repository.savedSettings)
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
