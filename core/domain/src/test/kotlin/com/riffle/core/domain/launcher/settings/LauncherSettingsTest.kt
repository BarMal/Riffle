package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.CardsChapterId
import com.riffle.core.domain.launcher.cards.CardsChapterPreferences
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherSettingsTest {
    @Test
    fun defaultsThemeAccentToDefault() {
        assertEquals(LauncherThemeAccent.DEFAULT, LauncherSettings().appearance.themeAccent)
    }

    @Test
    fun customThemeColoursKeepIndependentOverridesPerSurface() {
        val colors =
            LauncherThemeColors()
                .withColor(LauncherThemeColorTarget.DOCK, 0x80445566.toInt())
                .withColor(LauncherThemeColorTarget.LABEL, 0xFF112233.toInt())

        assertEquals(0x80445566.toInt(), colors.dockArgb)
        assertEquals(0xFF112233.toInt(), colors.labelArgb)
        assertEquals(null, colors.backgroundArgb)
    }

    @Test
    fun defaultsThemeOverridesToPresetTokens() {
        assertEquals(LauncherThemeCornerStyle.PRESET, LauncherSettings().appearance.themeCornerStyle)
        assertEquals(LauncherThemeTypography.PRESET, LauncherSettings().appearance.themeTypography)
    }

    @Test
    fun defaultsSearchResultsToIconPresentation() {
        assertEquals(SearchResultPresentation.ICONS, LauncherSettings().search.resultPresentation)
    }

    @Test
    fun defaultsAppDrawerToListPresentationWithFourIconColumns() {
        assertEquals(AppDrawerPresentation.LIST, LauncherSettings().appDrawer.presentation)
        assertEquals(DEFAULT_APP_DRAWER_ICON_GRID_COLUMNS, LauncherSettings().appDrawer.iconGridColumns)
    }

    @Test
    fun cardsChapterIntentDefaultsToOverviewWithoutPins() {
        val cards = LauncherSettings().cards.chapterPreferences

        assertEquals(emptyList(), cards.pinnedChapterIds)
        assertEquals(CardsChapterId.Overview, cards.selectedChapterId)
    }

    @Test
    fun cardsDefaultToTheModernTimeScapeAppearanceIntent() {
        assertEquals(TimeScapeAppearanceSettings.modern(), LauncherSettings().cards.timeScapeAppearance)
    }

    @Test
    fun migratesHistoricalCardsAppIntentToTheRequestedStageLayout() {
        val chapter = CardsChapterId.App(AppPackageName("com.riffle.mail"), AppProfile.personal().id)
        val cards = CardsSettings(chapterPreferences = CardsChapterPreferences(listOf(chapter), chapter))

        val stagePreferences = cards.stagePreferencesFor(HomeLayoutKey(LauncherViewMode.CARD_INTERFACE))

        val stageId = AppStageId(chapter.packageName, chapter.profileId)

        assertEquals(listOf(stageId), stagePreferences.pinnedStageIds)
        assertEquals(stageId, stagePreferences.selectedStageId)
    }

    @Test
    fun materializedStageIntentDoesNotChangeAfterCardsIntentIsEdited() {
        val mail = CardsChapterId.App(AppPackageName("com.riffle.mail"), AppProfile.personal().id)
        val calendar = CardsChapterId.App(AppPackageName("com.riffle.calendar"), AppProfile.personal().id)
        val key = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE)
        val migrated =
            CardsSettings(chapterPreferences = CardsChapterPreferences(listOf(mail), mail))
                .withMigratedStagePreferences(key)

        val updatedCards = migrated.copy(chapterPreferences = CardsChapterPreferences(listOf(calendar), calendar))

        assertEquals(migrated.stagePreferencesFor(key), updatedCards.stagePreferencesFor(key))
    }

    @Test
    fun materializesIndependentStageIntentForEveryCompatibleVariant() {
        val mail = CardsChapterId.App(AppPackageName("com.riffle.mail"), AppProfile.personal().id)
        val phone = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, HomeLayoutDeviceClass.PHONE)
        val tablet = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, HomeLayoutDeviceClass.TABLET)
        val migrated =
            CardsSettings(chapterPreferences = CardsChapterPreferences(listOf(mail), mail))
                .withMigratedStagePreferences(listOf(phone, tablet))
        val updatedCards = migrated.copy(chapterPreferences = CardsChapterPreferences())

        assertEquals(migrated.stagePreferencesFor(phone), updatedCards.stagePreferencesFor(phone))
        assertEquals(migrated.stagePreferencesFor(tablet), updatedCards.stagePreferencesFor(tablet))
    }

    @Test
    fun replacesLaunchTargetWhenGestureActionChanges() {
        val identity =
            AppIdentity(
                packageName = AppPackageName("com.riffle.mail"),
                activityName = AppActivityName("com.riffle.mail.MainActivity"),
            )
        val configured =
            HomeGestureSettings().withAction(
                gesture = HomeGesture.THREE_FINGER_UP,
                action = LauncherGestureAction.LAUNCH_APP,
                launchTarget = LauncherGestureLaunchTarget.App(identity),
            )

        val updated =
            configured.withAction(
                gesture = HomeGesture.THREE_FINGER_UP,
                action = LauncherGestureAction.OPEN_SEARCH,
            )

        assertEquals(null, updated.launchTargetFor(HomeGesture.THREE_FINGER_UP))
    }

    @Test
    fun doesNotReportConflictForLaunchAppGesturesWithDifferentTargets() {
        val mail = appIdentity("mail")
        val calendar = appIdentity("calendar")
        val settings =
            GestureSettings(
                homeGestures =
                    HomeGestureSettings(
                        actions =
                            mapOf(
                                HomeGesture.TWO_FINGER_LEFT to LauncherGestureAction.LAUNCH_APP,
                                HomeGesture.TWO_FINGER_RIGHT to LauncherGestureAction.LAUNCH_APP,
                            ),
                        launchTargets =
                            mapOf(
                                HomeGesture.TWO_FINGER_LEFT to LauncherGestureLaunchTarget.App(mail),
                                HomeGesture.TWO_FINGER_RIGHT to LauncherGestureLaunchTarget.App(calendar),
                            ),
                    ),
            )

        assertEquals(
            emptyList<LauncherGestureConflict>(),
            settings.conflicts.filter { it.action == LauncherGestureAction.LAUNCH_APP },
        )
    }

    @Test
    fun reportsConflictForLaunchAppGesturesWithSameTarget() {
        val mail = appIdentity("mail")
        val settings =
            GestureSettings(
                homeGestures =
                    HomeGestureSettings(
                        actions =
                            mapOf(
                                HomeGesture.TWO_FINGER_LEFT to LauncherGestureAction.LAUNCH_APP,
                                HomeGesture.TWO_FINGER_RIGHT to LauncherGestureAction.LAUNCH_APP,
                            ),
                        launchTargets =
                            mapOf(
                                HomeGesture.TWO_FINGER_LEFT to LauncherGestureLaunchTarget.App(mail),
                                HomeGesture.TWO_FINGER_RIGHT to LauncherGestureLaunchTarget.App(mail),
                            ),
                    ),
            )

        assertEquals(
            listOf(
                LauncherGestureConflict(
                    surface = LauncherGestureSurface.HOME_PAGE,
                    action = LauncherGestureAction.LAUNCH_APP,
                    gestures = listOf(LauncherGesture.TWO_FINGER_LEFT, LauncherGesture.TWO_FINGER_RIGHT),
                ),
            ),
            settings.conflicts.filter { it.action == LauncherGestureAction.LAUNCH_APP },
        )
    }

    @Test
    fun defaultsToSystemWallpaper() {
        val settings = LauncherSettings()

        assertEquals(WallpaperSettings.system(), settings.appearance.wallpaper)
    }

    @Test
    fun defaultsHomeSwipeGesturesToStandardLauncherActions() {
        val settings = LauncherSettings()

        assertEquals(LauncherGestureAction.OPEN_APP_DRAWER, settings.gestures.homeSwipe.up)
        assertEquals(LauncherGestureAction.OPEN_NOTIFICATIONS, settings.gestures.homeSwipe.down)
        assertEquals(LauncherGestureAction.SELECT_NEXT_HOME_PAGE, settings.gestures.homeSwipe.left)
        assertEquals(LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE, settings.gestures.homeSwipe.right)
        assertEquals(
            LauncherGestureAction.OPEN_SEARCH,
            settings.gestures.homeGestures.actionFor(HomeGesture.TWO_FINGER_UP),
        )
        assertEquals(
            LauncherGestureAction.OPEN_SETTINGS,
            settings.gestures.homeGestures.actionFor(HomeGesture.TWO_FINGER_DOWN),
        )
        assertEquals(
            LauncherGestureAction.ENTER_HOME_EDIT_MODE,
            settings.gestures.homeGestures.actionFor(HomeGesture.PINCH_IN),
        )
    }

    @Test
    fun surfacesSharedGestureConflictsThroughGestureSettings() {
        val settings =
            LauncherSettings(
                gestures =
                    GestureSettings(
                        homeGestures =
                            HomeGestureSettings(
                                actions =
                                    mapOf(
                                        HomeGesture.TWO_FINGER_LEFT to LauncherGestureAction.NONE,
                                    ),
                            ),
                    ),
            )

        assertEquals(
            listOf(
                LauncherGestureConflict(
                    surface = LauncherGestureSurface.HOME_PAGE,
                    action = LauncherGestureAction.OPEN_APP_DRAWER,
                    gestures = listOf(LauncherGesture.ONE_FINGER_UP, LauncherGesture.PINCH_OUT),
                ),
            ),
            settings.gestures.conflicts.filter { it.action == LauncherGestureAction.OPEN_APP_DRAWER },
        )
    }

    @Test
    fun defaultsHapticFeedbackStrengthToMedium() {
        val settings = LauncherSettings()

        assertEquals(HapticFeedbackStrength.MEDIUM, settings.haptics.feedbackStrength)
    }

    @Test
    fun defaultsReducedMotionToOff() {
        val settings = LauncherSettings()

        assertEquals(false, settings.motion.reducedMotion)
    }

    @Test
    fun defaultsContextualSettingsToDisabled() {
        val settings = LauncherSettings()

        assertEquals(false, settings.contextual.enabled)
    }

    @Test
    fun defaultsOverlayDockToDisabled() {
        val settings = LauncherSettings()

        assertEquals(false, settings.overlayDock.enabled)
        assertEquals(OverlayDockEdge.END, settings.overlayDock.edge)
        assertEquals(DEFAULT_OVERLAY_DOCK_HANDLE_THICKNESS_DP, settings.overlayDock.handleThicknessDp)
        assertEquals(DEFAULT_OVERLAY_DOCK_HANDLE_HEIGHT_DP, settings.overlayDock.handleHeightDp)
        assertEquals(DEFAULT_OVERLAY_DOCK_VERTICAL_OFFSET_DP, settings.overlayDock.verticalOffsetDp)
        assertEquals(DEFAULT_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT, settings.overlayDock.handleAlphaPercent)
        assertEquals(DEFAULT_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP, settings.overlayDock.expandedIconSizeDp)
        assertEquals(OverlayDockExpandedOrientation.WIDE, settings.overlayDock.expandedOrientation)
        assertEquals(false, settings.overlayDock.showLabels)
    }

    @Test
    fun appearanceCanSelectSolidColourWallpaperFallback() {
        val settings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                    ),
            )

        assertEquals(WallpaperSource.SOLID_COLOR, settings.appearance.wallpaper.source)
    }

    private fun appIdentity(name: String): AppIdentity =
        AppIdentity(
            packageName = AppPackageName("com.riffle.$name"),
            activityName = AppActivityName("com.riffle.$name.MainActivity"),
        )
}
