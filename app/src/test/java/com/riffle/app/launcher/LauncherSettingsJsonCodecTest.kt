package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneArrangement
import com.riffle.core.domain.launcher.cards.AdaptiveStageTemplateCatalogDefaults
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.contextual.ContextualSettings
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedRefreshIntent
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageCardEffect
import com.riffle.core.domain.launcher.settings.AdaptiveStageFanDirection
import com.riffle.core.domain.launcher.settings.AdaptiveStageGeometry
import com.riffle.core.domain.launcher.settings.AdaptiveStageMotion
import com.riffle.core.domain.launcher.settings.AdaptiveStageSurface
import com.riffle.core.domain.launcher.settings.AppDrawerPresentation
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.NotificationHidingSettings
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.RssSettings
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import com.riffle.core.domain.launcher.settings.SearchSettings
import com.riffle.core.domain.launcher.settings.ThreadCardGrouping
import com.riffle.core.domain.launcher.settings.ThreadMessageOrder
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.settings.stagePreferencesFor
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LargeClass")
class LauncherSettingsJsonCodecTest {
    @Test
    fun roundTripsAppDrawerSettingsAndCoercesMalformedGridColumns() {
        val settings =
            LauncherSettings(
                appDrawer = AppDrawerSettings(AppDrawerPresentation.ICONS, iconGridColumns = 6),
            )

        assertEquals(settings.appDrawer, decodeLauncherSettings(encodeLauncherSettings(settings)).appDrawer)
        assertEquals(
            AppDrawerSettings(AppDrawerPresentation.LIST, iconGridColumns = 3),
            decodeLauncherSettings(
                "{\"appDrawer\": {\"presentation\": \"UNKNOWN\", \"iconGridColumns\": 1}}",
            ).appDrawer,
        )
    }

    @Test
    fun roundTripsSearchResultPresentationAndDefaultsMissingValuesToIcons() {
        val settings = LauncherSettings(search = SearchSettings(SearchResultPresentation.LIST))

        assertEquals(settings.search, decodeLauncherSettings(encodeLauncherSettings(settings)).search)
        assertEquals(SearchResultPresentation.ICONS, decodeLauncherSettings("{}").search.resultPresentation)
        assertEquals(
            SearchResultPresentation.ICONS,
            decodeLauncherSettings("{\"search\": {\"resultPresentation\": \"UNKNOWN\"}}").search.resultPresentation,
        )
    }

    @Test
    fun roundTripsAdaptiveStageStageIntent() {
        val mail = AppStageId(AppPackageName("com.riffle.mail"), AppProfile.personal().id)
        val key = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE)
        val settings =
            LauncherSettings(
                cards = CardsSettings(stagePreferencesByLayout = mapOf(key to AppStagePreferences(listOf(mail), mail))),
            )

        assertEquals(settings.cards, decodeLauncherSettings(encodeLauncherSettings(settings)).cards)
    }

    @Test
    fun roundTripsConfiguredAdaptiveStageTemplate() {
        val settings =
            LauncherSettings(
                cards = CardsSettings(adaptiveStageTemplateId = AdaptiveStageTemplateCatalogDefaults.sharedCanvasId),
            )

        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(settings.cards.adaptiveStageTemplateId, decoded.cards.adaptiveStageTemplateId)
    }

    @Test
    fun roundTripsConfiguredAdaptiveStagePaneArrangement() {
        val settings =
            LauncherSettings(
                cards = CardsSettings(adaptiveStagePaneArrangement = AdaptiveStagePaneArrangement.SPLIT),
            )

        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(settings.cards.adaptiveStagePaneArrangement, decoded.cards.adaptiveStagePaneArrangement)
    }

    @Test
    fun defaultsUnknownAdaptiveStagePaneArrangement() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "cards": {
                    "timeScapePaneArrangement": "UNKNOWN"
                  }
                }
                """.trimIndent(),
            )

        assertEquals(AdaptiveStagePaneArrangement.STACK, decodedSettings.cards.adaptiveStagePaneArrangement)
    }

    @Test
    fun roundTripsConfiguredThreadMessageOrder() {
        val settings =
            LauncherSettings(
                cards = CardsSettings(threadMessageOrder = ThreadMessageOrder.RECENT_FIRST),
            )

        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(settings.cards.threadMessageOrder, decoded.cards.threadMessageOrder)
    }

    @Test
    fun defaultsUnknownThreadMessageOrder() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "cards": {
                    "threadMessageOrder": "UNKNOWN"
                  }
                }
                """.trimIndent(),
            )

        assertEquals(ThreadMessageOrder.CHRONOLOGICAL, decodedSettings.cards.threadMessageOrder)
    }

    @Test
    fun roundTripsConfiguredThreadCardGrouping() {
        val settings =
            LauncherSettings(
                cards = CardsSettings(threadCardGrouping = ThreadCardGrouping.PER_MESSAGE),
            )

        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(settings.cards.threadCardGrouping, decoded.cards.threadCardGrouping)
    }

    @Test
    fun defaultsUnknownThreadCardGroupingToFoldingTheConversation() {
        // Settings written by a build that did not know this field decode to the folded default,
        // the same shape a fresh install gets.
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "cards": {
                    "threadCardGrouping": "UNKNOWN"
                  }
                }
                """.trimIndent(),
            )

        assertEquals(ThreadCardGrouping.PER_THREAD, decodedSettings.cards.threadCardGrouping)
    }

    @Test
    fun migratesLegacyCardsChapterJsonToAdaptiveStageStageIntent() {
        val legacyJson =
            """
            {
              "cards": {
                "pinnedChapterIds": [{"packageName":"com.riffle.mail","profileId":"personal"}],
                "selectedChapterId": {
                  "kind":"app",
                  "packageName":"com.riffle.mail",
                  "profileId":"personal"
                }
              }
            }
            """.trimIndent()
        val stageId = AppStageId(AppPackageName("com.riffle.mail"), AppProfile.personal().id)

        val stagePreferences =
            decodeLauncherSettings(legacyJson).cards.stagePreferencesFor(HomeLayoutKey(LauncherViewMode.CARD_INTERFACE))

        assertEquals(listOf(stageId), stagePreferences.pinnedStageIds)
        assertEquals(stageId, stagePreferences.selectedStageId)
    }

    @Test
    fun migratesLegacyCardsChapterJsonToNonPhoneAdaptiveStageStageIntent() {
        val legacyJson =
            """
            {
              "cards": {
                "pinnedChapterIds": [{"packageName":"com.riffle.mail","profileId":"personal"}],
                "selectedChapterId": {
                  "kind":"app",
                  "packageName":"com.riffle.mail",
                  "profileId":"personal"
                }
              }
            }
            """.trimIndent()
        val stageId = AppStageId(AppPackageName("com.riffle.mail"), AppProfile.personal().id)

        val stagePreferences =
            decodeLauncherSettings(legacyJson).cards.stagePreferencesFor(
                HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, HomeLayoutDeviceClass.TABLET),
            )

        assertEquals(listOf(stageId), stagePreferences.pinnedStageIds)
        assertEquals(stageId, stagePreferences.selectedStageId)
    }

    @Test
    fun roundTripsLayoutSpecificStageIntentWithoutTransientContent() {
        val mail = AppStageId(AppPackageName("com.riffle.mail"), AppProfile.personal().id)
        val key = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, HomeLayoutDeviceClass.TABLET)
        val settings =
            LauncherSettings(
                cards = CardsSettings(stagePreferencesByLayout = mapOf(key to AppStagePreferences(listOf(mail), mail))),
            )

        val cardsJson = JSONObject(encodeLauncherSettings(settings)).getJSONObject("cards")

        assertEquals(settings.cards, decodeLauncherSettings(encodeLauncherSettings(settings)).cards)
        assertFalse(cardsJson.has("content"))
    }

    @Test
    fun roundTripsAdaptiveStageAppearanceAndClampsImportedValues() {
        val settings =
            LauncherSettings(
                cards =
                    CardsSettings(
                        adaptiveStageAppearance =
                            AdaptiveStageAppearanceSettings(
                                geometry =
                                    AdaptiveStageGeometry(
                                        visibleDepth = 5,
                                        aboveFocusDepth = 2,
                                        stackPeakPercent = 30,
                                        rotationDegrees = 8,
                                        verticalFanDirection = AdaptiveStageFanDirection.START,
                                    ),
                                surface =
                                    AdaptiveStageSurface(
                                        blurStrengthPercent = 42,
                                        cardEffect = AdaptiveStageCardEffect.SOLID,
                                    ),
                                motion =
                                    AdaptiveStageMotion(
                                        magnetStrengthPercent = 15,
                                        reducedTransparency = true,
                                    ),
                            ),
                    ),
            )

        assertEquals(settings.cards, decodeLauncherSettings(encodeLauncherSettings(settings)).cards)

        val imported =
            decodeLauncherSettings(
                """
                {
                  "cards": {
                    "timeScapeAppearance": {
                      "geometry": { "visibleDepth": 999, "aboveFocusDepth": 999, "stackPeakPercent": 999 },
                      "surface": { "blurStrengthPercent": -1 },
                      "motion": { "settleDurationMillis": 9999, "magnetStrengthPercent": 999 }
                    }
                  }
                }
                """.trimIndent(),
            ).cards.adaptiveStageAppearance
        assertEquals(6, imported.geometry.visibleDepth)
        assertEquals(6, imported.geometry.aboveFocusDepth)
        assertEquals(85, imported.geometry.stackPeakPercent)
        assertEquals(0, imported.surface.blurStrengthPercent)
        assertEquals(600, imported.motion.settleDurationMillis)
        assertEquals(100, imported.motion.magnetStrengthPercent)
    }

    @Test
    fun defaultsMissingOrUnknownAdaptiveStageAppearanceValuesSafely() {
        val decoded =
            decodeLauncherSettings(
                """
                {
                  "cards": {
                    "timeScapeAppearance": {
                      "preset": "FUTURE",
                      "geometry": { "fanDirection": "FUTURE" },
                      "surface": { "backgroundSource": "FUTURE" }
                    }
                  }
                }
                """.trimIndent(),
            ).cards.adaptiveStageAppearance

        // "preset" is a stray key from before #1058 removed the preset system -- a real user's
        // already-persisted settings.json can still contain it, and decoding must ignore it
        // harmlessly rather than choke on it.
        assertEquals(AdaptiveStageAppearanceSettings.modern(), decoded)
    }

    @Test
    fun roundTripsUnfoldedAdaptiveStageAppearanceIndependentlyOfFolded() {
        val settings =
            LauncherSettings(
                cards =
                    CardsSettings(
                        adaptiveStageAppearance =
                            AdaptiveStageAppearanceSettings(
                                geometry = AdaptiveStageGeometry(visibleDepth = 5),
                            ),
                        unfoldedAppearance =
                            AdaptiveStageAppearanceSettings.unfolded()
                                .let { unfolded -> unfolded.copy(geometry = unfolded.geometry.copy(visibleDepth = 3)) },
                    ),
            )

        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(settings.cards, decoded.cards)
        assertEquals(5, decoded.cards.adaptiveStageAppearance.geometry.visibleDepth)
        assertEquals(3, decoded.cards.unfoldedAppearance.geometry.visibleDepth)
    }

    @Test
    fun missingUnfoldedAppearanceInLegacyJsonDefaultsToThePlainUnfoldedProfileWithoutLosingOtherCardsFields() {
        val decoded =
            decodeLauncherSettings(
                """
                {
                  "cards": {
                    "timeScapeAppearance": { "geometry": { "visibleDepth": 5 } },
                    "timeScapeRailSide": "TOP"
                  }
                }
                """.trimIndent(),
            ).cards

        assertEquals(AdaptiveStageAppearanceSettings.unfolded(), decoded.unfoldedAppearance)
        assertEquals(5, decoded.adaptiveStageAppearance.geometry.visibleDepth)
    }

    @Test
    fun encodesSettingsVersion() {
        val encodedSettings = JSONObject(encodeLauncherSettings(LauncherSettings()))

        assertEquals(LAUNCHER_SETTINGS_JSON_VERSION, encodedSettings.getInt("version"))
    }

    @Test
    fun decodesSettingsWithoutVersionForBackwardCompatibility() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "appearance": {
                    "wallpaper": {
                      "source": "SOLID_COLOR"
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(WallpaperSource.SOLID_COLOR, decodedSettings.appearance.wallpaper.source)
    }

    @Test
    fun roundTripsWallpaperSource() {
        val settings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(WallpaperSource.SOLID_COLOR, decodedSettings.appearance.wallpaper.source)
        assertEquals(WallpaperScrollMode.STATIC, decodedSettings.appearance.wallpaper.scrollMode)
    }

    @Test
    fun roundTripsThemeMode() {
        val settings = LauncherSettings(appearance = AppearanceSettings(themeMode = LauncherThemeMode.DARK))

        assertEquals(
            LauncherThemeMode.DARK,
            decodeLauncherSettings(encodeLauncherSettings(settings)).appearance.themeMode,
        )
    }

    @Test
    fun roundTripsWallpaperScrollMode() {
        val settings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        wallpaper =
                            WallpaperSettings(
                                source = WallpaperSource.SYSTEM,
                                scrollMode = WallpaperScrollMode.SCROLLING,
                            ),
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(WallpaperScrollMode.SCROLLING, decodedSettings.appearance.wallpaper.scrollMode)
    }

    @Test
    fun roundTripsFullscreenHome() {
        val settings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        fullscreenHome = true,
                        hideStatusBarOnHome = true,
                        hideNavigationBarOnHome = true,
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(true, decodedSettings.appearance.fullscreenHome)
        assertEquals(true, decodedSettings.appearance.hideStatusBarOnHome)
        assertEquals(true, decodedSettings.appearance.hideNavigationBarOnHome)
    }

    @Test
    fun roundTripsIndependentHomeSystemBarSettings() {
        val homeSystemBars =
            HomeSystemBars(
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = false,
            )
        val settings =
            LauncherSettings(
                appearance = AppearanceSettings().withHomeSystemBars(homeSystemBars),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(homeSystemBars, decodedSettings.appearance.homeSystemBars)
    }

    @Test
    fun decodesLegacyFullscreenHomeIntoIndependentSystemBarSettings() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "appearance": {
                    "fullscreenHome": true
                  }
                }
                """.trimIndent(),
            )

        assertEquals(
            HomeSystemBars(
                fullscreenHome = true,
                hideStatusBarOnHome = true,
                hideNavigationBarOnHome = true,
            ),
            decodedSettings.appearance.homeSystemBars,
        )
    }

    @Test
    fun defaultsMissingAppearanceSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(WallpaperSettings.system(), decodedSettings.appearance.wallpaper)
        assertEquals(HomeSystemBars(), decodedSettings.appearance.homeSystemBars)
    }

    @Test
    fun roundTripsThemePreset() {
        val settings = LauncherSettings(appearance = AppearanceSettings(themePreset = LauncherThemePreset.RETRO))

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(LauncherThemePreset.RETRO, decodedSettings.appearance.themePreset)
    }

    @Test
    fun roundTripsThemeAccent() {
        val settings = LauncherSettings(appearance = AppearanceSettings(themeAccent = LauncherThemeAccent.TEAL))

        assertEquals(
            LauncherThemeAccent.TEAL,
            decodeLauncherSettings(encodeLauncherSettings(settings)).appearance.themeAccent,
        )
    }

    @Test
    fun roundTripsCustomThemeColoursIncludingAlpha() {
        val colors =
            LauncherThemeColors(
                backgroundArgb = 0xCC102030.toInt(),
                accentArgb = 0xFF405060.toInt(),
                dockArgb = 0x80405060.toInt(),
                labelArgb = 0xFFE0E0E0.toInt(),
                labelBackgroundArgb = 0x99000000.toInt(),
            )

        val decoded =
            decodeLauncherSettings(
                encodeLauncherSettings(LauncherSettings(appearance = AppearanceSettings(themeColors = colors))),
            )

        assertEquals(colors, decoded.appearance.themeColors)
    }

    @Test
    fun roundTripsThemeTokenOverrides() {
        val settings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        themeCornerStyle = LauncherThemeCornerStyle.ROUNDED,
                        themeTypography = LauncherThemeTypography.MONOSPACE,
                    ),
            )

        val decodedAppearance = decodeLauncherSettings(encodeLauncherSettings(settings)).appearance

        assertEquals(LauncherThemeCornerStyle.ROUNDED, decodedAppearance.themeCornerStyle)
        assertEquals(LauncherThemeTypography.MONOSPACE, decodedAppearance.themeTypography)
    }

    @Test
    fun defaultsMissingOrInvalidThemeTokenOverrides() {
        val missing = decodeLauncherSettings("{\"appearance\": {}}").appearance
        val invalid =
            decodeLauncherSettings(
                "{\"appearance\": {\"themeCornerStyle\": \"UNKNOWN\", \"themeTypography\": \"UNKNOWN\"}}",
            ).appearance

        assertEquals(LauncherThemeCornerStyle.PRESET, missing.themeCornerStyle)
        assertEquals(LauncherThemeTypography.PRESET, missing.themeTypography)
        assertEquals(LauncherThemeCornerStyle.PRESET, invalid.themeCornerStyle)
        assertEquals(LauncherThemeTypography.PRESET, invalid.themeTypography)
    }

    @Test
    fun defaultsMissingOrInvalidThemeAccent() {
        assertEquals(
            LauncherThemeAccent.DEFAULT,
            decodeLauncherSettings("{\"appearance\": {}}").appearance.themeAccent,
        )
        assertEquals(
            LauncherThemeAccent.DEFAULT,
            decodeLauncherSettings("{\"appearance\": {\"themeAccent\": \"UNKNOWN\"}}").appearance.themeAccent,
        )
    }

    @Test
    fun defaultsMissingOrInvalidThemePreset() {
        assertEquals(
            LauncherThemePreset.MATERIAL,
            decodeLauncherSettings("{\"appearance\": {}}").appearance.themePreset,
        )
        assertEquals(
            LauncherThemePreset.MATERIAL,
            decodeLauncherSettings("{\"appearance\": {\"themePreset\": \"UNKNOWN\"}}").appearance.themePreset,
        )
    }

    @Test
    fun migratesHistoricalVictorianThemePresetToMaterial() {
        assertEquals(
            LauncherThemePreset.MATERIAL,
            decodeLauncherSettings(
                "{\"appearance\": {\"themePreset\": \"VICTORIAN\"}}",
            ).appearance.themePreset,
        )
    }

    @Test
    fun roundTripsHomeSwipeGestureActions() {
        val settings =
            LauncherSettings(
                gestures =
                    GestureSettings(
                        homeGestures =
                            HomeGestureSettings(
                                actions =
                                    mapOf(
                                        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
                                        HomeGesture.ONE_FINGER_DOWN to LauncherGestureAction.NONE,
                                        HomeGesture.ONE_FINGER_LEFT to LauncherGestureAction.OPEN_SETTINGS,
                                        HomeGesture.ONE_FINGER_RIGHT to LauncherGestureAction.ENTER_HOME_EDIT_MODE,
                                        HomeGesture.TWO_FINGER_UP to LauncherGestureAction.OPEN_NOTIFICATIONS,
                                        HomeGesture.THREE_FINGER_LEFT to LauncherGestureAction.OPEN_APP_DRAWER,
                                        HomeGesture.PINCH_OUT to LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW,
                                        HomeGesture.TWO_FINGER_RIGHT to LauncherGestureAction.ENTER_FULLSCREEN_HOME,
                                    ),
                            ),
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(LauncherGestureAction.OPEN_SEARCH, decodedSettings.gestures.homeSwipe.up)
        assertEquals(LauncherGestureAction.NONE, decodedSettings.gestures.homeSwipe.down)
        assertEquals(LauncherGestureAction.OPEN_SETTINGS, decodedSettings.gestures.homeSwipe.left)
        assertEquals(LauncherGestureAction.ENTER_HOME_EDIT_MODE, decodedSettings.gestures.homeSwipe.right)
        assertEquals(
            LauncherGestureAction.OPEN_NOTIFICATIONS,
            decodedSettings.gestures.homeGestures.actionFor(HomeGesture.TWO_FINGER_UP),
        )
        assertEquals(
            LauncherGestureAction.OPEN_APP_DRAWER,
            decodedSettings.gestures.homeGestures.actionFor(HomeGesture.THREE_FINGER_LEFT),
        )
        assertEquals(
            LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW,
            decodedSettings.gestures.homeGestures.actionFor(HomeGesture.PINCH_OUT),
        )
        assertEquals(
            LauncherGestureAction.ENTER_FULLSCREEN_HOME,
            decodedSettings.gestures.homeGestures.actionFor(HomeGesture.TWO_FINGER_RIGHT),
        )
    }

    @Test
    fun decodesLegacyHomeSwipeGestureActions() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "gestures": {
                    "homeSwipe": {
                      "up": "OPEN_SEARCH",
                      "down": "NONE",
                      "left": "OPEN_SETTINGS",
                      "right": "ENTER_HOME_EDIT_MODE"
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(LauncherGestureAction.OPEN_SEARCH, decodedSettings.gestures.homeSwipe.up)
        assertEquals(LauncherGestureAction.NONE, decodedSettings.gestures.homeSwipe.down)
        assertEquals(LauncherGestureAction.OPEN_SETTINGS, decodedSettings.gestures.homeSwipe.left)
        assertEquals(LauncherGestureAction.ENTER_HOME_EDIT_MODE, decodedSettings.gestures.homeSwipe.right)
    }

    @Test
    fun defaultsMissingGestureSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(LauncherGestureAction.OPEN_APP_DRAWER, decodedSettings.gestures.homeSwipe.up)
        assertEquals(LauncherGestureAction.OPEN_NOTIFICATIONS, decodedSettings.gestures.homeSwipe.down)
        assertEquals(LauncherGestureAction.SELECT_NEXT_HOME_PAGE, decodedSettings.gestures.homeSwipe.left)
        assertEquals(LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE, decodedSettings.gestures.homeSwipe.right)
    }

    @Test
    fun roundTripsHapticFeedbackStrength() {
        val settings =
            LauncherSettings(
                haptics =
                    HapticSettings(
                        feedbackStrength = HapticFeedbackStrength.STRONG,
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(HapticFeedbackStrength.STRONG, decodedSettings.haptics.feedbackStrength)
    }

    @Test
    fun defaultsMissingHapticSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(HapticFeedbackStrength.MEDIUM, decodedSettings.haptics.feedbackStrength)
    }

    @Test
    fun roundTripsMotionSettings() {
        val settings =
            LauncherSettings(
                motion =
                    MotionSettings(
                        reducedMotion = true,
                        performanceTargetFps = MotionPerformanceTargetFps.FPS_90,
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(true, decodedSettings.motion.reducedMotion)
        assertEquals(MotionPerformanceTargetFps.FPS_90, decodedSettings.motion.performanceTargetFps)
    }

    @Test
    fun defaultsMissingMotionSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(false, decodedSettings.motion.reducedMotion)
        assertEquals(MotionPerformanceTargetFps.FPS_120, decodedSettings.motion.performanceTargetFps)
    }

    @Test
    fun defaultsMalformedMotionPerformanceTarget() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "motion": {
                    "performanceTargetFps": "FPS_144"
                  }
                }
                """.trimIndent(),
            )

        assertEquals(MotionPerformanceTargetFps.FPS_120, decodedSettings.motion.performanceTargetFps)
    }

    @Test
    fun roundTripsContextualSettings() {
        val settings =
            LauncherSettings(
                contextual = ContextualSettings(enabled = true),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(true, decodedSettings.contextual.enabled)
    }

    @Test
    fun defaultsMissingContextualSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(false, decodedSettings.contextual.enabled)
    }

    @Test
    fun defaultsMalformedSettingsSectionsIndependently() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "appearance": [],
                  "contextual": "enabled",
                  "gestures": [],
                  "haptics": 1,
                  "motion": true
                }
                """.trimIndent(),
            )

        assertEquals(AppearanceSettings(), decodedSettings.appearance)
        assertEquals(ContextualSettings(), decodedSettings.contextual)
        assertEquals(GestureSettings(), decodedSettings.gestures)
        assertEquals(HapticSettings(), decodedSettings.haptics)
        assertEquals(MotionSettings(), decodedSettings.motion)
    }

    @Test
    fun roundTripsOverlayDockSettings() {
        val settings =
            LauncherSettings(
                overlayDock =
                    OverlayDockSettings(
                        enabled = true,
                        edge = OverlayDockEdge.START,
                        handleThicknessDp = 24,
                        handleHeightDp = 96,
                        verticalOffsetDp = -48,
                        handleAlphaPercent = 65,
                        expandedIconSizeDp = 64,
                        expandedOrientation = OverlayDockExpandedOrientation.TALL,
                        showLabels = true,
                        items =
                            listOf(
                                AppShortcutItem(
                                    id = LauncherItemId("floating-dock:camera:1"),
                                    appIdentity = appIdentity,
                                    label = "Camera",
                                ),
                            ),
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(true, decodedSettings.overlayDock.enabled)
        assertEquals(OverlayDockEdge.START, decodedSettings.overlayDock.edge)
        assertEquals(24, decodedSettings.overlayDock.handleThicknessDp)
        assertEquals(96, decodedSettings.overlayDock.handleHeightDp)
        assertEquals(-48, decodedSettings.overlayDock.verticalOffsetDp)
        assertEquals(65, decodedSettings.overlayDock.handleAlphaPercent)
        assertEquals(64, decodedSettings.overlayDock.expandedIconSizeDp)
        assertEquals(OverlayDockExpandedOrientation.TALL, decodedSettings.overlayDock.expandedOrientation)
        assertEquals(true, decodedSettings.overlayDock.showLabels)
        assertEquals(settings.overlayDock.items, decodedSettings.overlayDock.items)
    }

    @Test
    fun defaultsMissingOverlayDockSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(OverlayDockSettings(), decodedSettings.overlayDock)
    }

    @Test
    fun clampsDecodedOverlayDockNumericSettings() {
        val lowSettings =
            decodeLauncherSettings(
                """
                {
                  "overlayDock": {
                    "handleThicknessDp": -1,
                    "handleHeightDp": -1,
                    "verticalOffsetDp": -999,
                    "handleAlphaPercent": -1,
                    "expandedIconSizeDp": -1
                  }
                }
                """.trimIndent(),
            ).overlayDock

        assertEquals(MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP, lowSettings.handleThicknessDp)
        assertEquals(MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP, lowSettings.handleHeightDp)
        assertEquals(MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP, lowSettings.verticalOffsetDp)
        assertEquals(MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT, lowSettings.handleAlphaPercent)
        assertEquals(MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP, lowSettings.expandedIconSizeDp)

        val highSettings =
            decodeLauncherSettings(
                """
                {
                  "overlayDock": {
                    "handleThicknessDp": 999,
                    "handleHeightDp": 999,
                    "verticalOffsetDp": 999,
                    "handleAlphaPercent": 999,
                    "expandedIconSizeDp": 999
                  }
                }
                """.trimIndent(),
            ).overlayDock

        assertEquals(MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP, highSettings.handleThicknessDp)
        assertEquals(MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP, highSettings.handleHeightDp)
        assertEquals(MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP, highSettings.verticalOffsetDp)
        assertEquals(MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT, highSettings.handleAlphaPercent)
        assertEquals(MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP, highSettings.expandedIconSizeDp)
    }

    @Test
    fun defaultsUnknownOverlayDockEnums() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "overlayDock": {
                    "edge": "UNKNOWN",
                    "expandedOrientation": "UNKNOWN"
                  }
                }
                """.trimIndent(),
            )

        assertEquals(OverlayDockEdge.END, decodedSettings.overlayDock.edge)
        assertEquals(OverlayDockExpandedOrientation.WIDE, decodedSettings.overlayDock.expandedOrientation)
    }

    @Test
    fun ignoresMalformedOverlayDockItems() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "overlayDock": {
                    "enabled": true,
                    "items": [
                      {
                        "type": "shortcut",
                        "id": "floating-dock:camera:1",
                        "label": "Camera",
                        "packageName": "com.example.camera",
                        "activityName": ".CameraActivity"
                      },
                      {
                        "type": "shortcut",
                        "id": "floating-dock:broken:2",
                        "label": "Broken"
                      },
                      "not-an-object"
                    ]
                  }
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                AppShortcutItem(
                    id = LauncherItemId("floating-dock:camera:1"),
                    appIdentity = appIdentity,
                    label = "Camera",
                ),
            ),
            decodedSettings.overlayDock.items,
        )
    }

    @Test
    fun defaultsUnknownWallpaperSource() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "appearance": {
                    "wallpaper": {
                      "source": "UNKNOWN"
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(WallpaperSettings.system(), decodedSettings.appearance.wallpaper)
    }

    @Test
    fun defaultsUnknownWallpaperScrollMode() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "appearance": {
                    "wallpaper": {
                      "source": "SYSTEM",
                      "scrollMode": "UNKNOWN"
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(WallpaperSettings.system(), decodedSettings.appearance.wallpaper)
    }

    @Test
    fun defaultsUnknownGestureAction() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "gestures": {
                    "homeSwipe": {
                      "up": "UNKNOWN"
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(LauncherGestureAction.OPEN_APP_DRAWER, decodedSettings.gestures.homeSwipe.up)
    }

    @Test
    fun roundTripsRssFeedConfigurationAndRefreshInterval() {
        val feed =
            FeedConfiguration(
                id = FeedId("feed-1"),
                url = FeedUrl.parse("https://example.com/feed.xml?utm_source=x&keep=1").getOrThrow(),
                profile = AppProfile.personal(),
                enabled = false,
                refreshIntent = FeedRefreshIntent.ALLOW_SCHEDULED,
            )
        val settings =
            LauncherSettings(
                rss = RssSettings(feeds = listOf(feed), refreshInterval = FeedRefreshIntervalOption.MINUTES_30),
            )

        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(settings.rss, decoded.rss)
        assertEquals("https://example.com/feed.xml?keep=1", decoded.rss.feeds.single().url.value)
    }

    @Test
    fun defaultsMissingRssSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(RssSettings(), decodedSettings.rss)
    }

    @Test
    fun dropsInvalidFeedEntriesOnDecodeWhileKeepingValidOnes() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "rss": {
                    "feeds": [
                      {
                        "id": "valid",
                        "url": "https://example.com/feed.xml",
                        "profileId": "personal",
                        "profileType": "PERSONAL",
                        "enabled": true,
                        "refreshIntent": "MANUAL"
                      },
                      {
                        "id": "http-scheme",
                        "url": "http://example.com/feed.xml",
                        "profileId": "personal",
                        "profileType": "PERSONAL"
                      },
                      {
                        "id": "credentials",
                        "url": "https://user:pass@example.com/feed.xml",
                        "profileId": "personal",
                        "profileType": "PERSONAL"
                      },
                      {
                        "id": "blank-url",
                        "url": "",
                        "profileId": "personal",
                        "profileType": "PERSONAL"
                      },
                      {
                        "id": "",
                        "url": "https://example.com/other.xml",
                        "profileId": "personal",
                        "profileType": "PERSONAL"
                      },
                      "not-an-object"
                    ]
                  }
                }
                """.trimIndent(),
            )

        assertEquals(1, decodedSettings.rss.feeds.size)
        assertEquals("https://example.com/feed.xml", decodedSettings.rss.feeds.single().url.value)
    }

    @Test
    fun defaultsUnknownRssRefreshInterval() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "rss": {
                    "refreshInterval": "UNKNOWN"
                  }
                }
                """.trimIndent(),
            )

        assertEquals(FeedRefreshIntervalOption.DEFAULT, decodedSettings.rss.refreshInterval)
    }

    @Test
    fun encodesRssSettingsVersion() {
        val encodedSettings = JSONObject(encodeLauncherSettings(LauncherSettings()))

        assertEquals(LAUNCHER_SETTINGS_JSON_VERSION, encodedSettings.getInt("version"))
        assertTrue(encodedSettings.has("rss"))
    }

    @Test
    fun roundTripsNotificationHideRules() {
        val rule =
            com.riffle.core.domain.launcher.notifications.NotificationHideRule(
                id = com.riffle.core.domain.launcher.notifications.NotificationHideRuleId("rule-1"),
                packageName = AppPackageName("com.example.chat"),
                profileId = AppProfile.personal().id,
                kind = com.riffle.core.domain.launcher.notifications.NotificationHideRule.Kind.TITLE,
                value = "order #{?} shipped",
                matchMode = com.riffle.core.domain.launcher.notifications.NotificationHideRule.MatchMode.WILDCARD,
            )
        val settings = LauncherSettings(notificationHiding = NotificationHidingSettings(rules = listOf(rule)))

        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(settings.notificationHiding, decoded.notificationHiding)
    }

    @Test
    fun defaultsMissingNotificationHidingSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(NotificationHidingSettings(), decodedSettings.notificationHiding)
    }

    @Test
    fun dropsInvalidNotificationHideRuleEntriesOnDecodeWhileKeepingValidOnes() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "notificationHiding": {
                    "rules": [
                      {
                        "id": "valid",
                        "packageName": "com.example.chat",
                        "profileId": "personal",
                        "kind": "APP",
                        "value": "",
                        "matchMode": "EXACT"
                      },
                      {
                        "id": "",
                        "packageName": "com.example.chat",
                        "profileId": "personal",
                        "kind": "APP"
                      },
                      {
                        "id": "missing-kind",
                        "packageName": "com.example.chat",
                        "profileId": "personal",
                        "kind": "NOT_A_KIND"
                      },
                      "not-an-object"
                    ]
                  }
                }
                """.trimIndent(),
            )

        assertEquals(1, decodedSettings.notificationHiding.rules.size)
        assertEquals("valid", decodedSettings.notificationHiding.rules.single().id.value)
    }

    @Test
    fun defaultsUnknownHapticFeedbackStrength() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "haptics": {
                    "feedbackStrength": "UNKNOWN"
                  }
                }
                """.trimIndent(),
            )

        assertEquals(HapticFeedbackStrength.MEDIUM, decodedSettings.haptics.feedbackStrength)
    }

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.camera"),
                activityName = AppActivityName(".CameraActivity"),
            )
    }
}
