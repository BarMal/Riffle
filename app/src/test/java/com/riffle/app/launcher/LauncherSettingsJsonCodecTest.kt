package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.designsystem.CardTint
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedRefreshIntent
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.DockMotionSettings
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.HintSettings
import com.riffle.core.domain.launcher.settings.HomeBehaviourSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.HomeMode
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import com.riffle.core.domain.launcher.settings.LiquidGlassSettings
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
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.PrivacySettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.settings.RssSettings
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import com.riffle.core.domain.launcher.settings.SearchSettings
import com.riffle.core.domain.launcher.settings.StreamSettings
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.settings.resolveLiquidGlass
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
import com.riffle.core.domain.launcher.settings.withReducedTransparency
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("LargeClass")
class LauncherSettingsJsonCodecTest {
    @Test
    fun roundTripsTheLibraryViewsAndCoercesMalformedGridColumns() {
        val settings =
            LauncherSettings(
                appDrawer =
                    AppDrawerSettings(
                        libraryViews =
                            mapOf(
                                HomeLayoutDeviceClass.PHONE to LibraryView.Grid,
                                HomeLayoutDeviceClass.TABLET to LibraryView.AlphaList,
                            ),
                        iconGridColumns = 6,
                    ),
            )

        assertEquals(settings.appDrawer, decodeLauncherSettings(encodeLauncherSettings(settings)).appDrawer)
        assertEquals(
            AppDrawerSettings(iconGridColumns = 3),
            decodeLauncherSettings(
                "{\"appDrawer\": {\"iconGridColumns\": 1}}",
            ).appDrawer,
        )
    }

    @Test
    fun decodesTheOldAppDrawerPresentationIntoTheLibraryViewOfEveryDeviceClass() {
        fun viewOf(
            json: String,
            deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
        ) = decodeLauncherSettings(json).appDrawer.libraryViewFor(deviceClass)

        assertEquals(LibraryView.AlphaList, viewOf("{\"appDrawer\": {\"presentation\": \"LIST\"}}"))
        assertEquals(
            LibraryView.AlphaList,
            viewOf("{\"appDrawer\": {\"presentation\": \"LIST\"}}", HomeLayoutDeviceClass.TABLET),
        )
        assertEquals(LibraryView.Grid, viewOf("{\"appDrawer\": {\"presentation\": \"ICONS\"}}"))
        assertEquals(
            LibraryView.Grid,
            viewOf("{\"appDrawer\": {\"presentation\": \"ICONS\"}}", HomeLayoutDeviceClass.FOLDABLE),
        )
    }

    @Test
    fun anythingElseInTheStoredAppDrawerDecodesToCategories() {
        listOf(
            "{}",
            "{\"appDrawer\": {}}",
            "{\"appDrawer\": {\"presentation\": \"UNKNOWN\"}}",
            "{\"appDrawer\": {\"presentation\": 7}}",
            "{\"appDrawer\": {\"libraryViews\": {\"PHONE\": \"carousel\", \"WATCH\": \"grid\"}}}",
            "{\"appDrawer\": {\"libraryViews\": \"grid\"}}",
        ).forEach { json ->
            assertEquals(
                json,
                LibraryView.Categories,
                decodeLauncherSettings(json).appDrawer.libraryViewFor(HomeLayoutDeviceClass.PHONE),
            )
        }
    }

    @Test
    fun theNewLibraryViewsWinOverAnOldPresentationStoredBesideThem() {
        val decoded =
            decodeLauncherSettings(
                "{\"appDrawer\": {\"presentation\": \"LIST\", \"libraryViews\": {\"PHONE\": \"grid\"}}}",
            ).appDrawer

        assertEquals(LibraryView.Grid, decoded.libraryViewFor(HomeLayoutDeviceClass.PHONE))
        assertEquals(LibraryView.Categories, decoded.libraryViewFor(HomeLayoutDeviceClass.TABLET))
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
    fun roundTripsReducedTransparencyAsATopLevelAppearanceSetting() {
        val settings = LauncherSettings().withReducedTransparency(true)

        val json = JSONObject(encodeLauncherSettings(settings))
        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertTrue(json.getJSONObject("appearance").getBoolean("reducedTransparency"))
        assertTrue(decoded.appearance.reducedTransparency)
        assertFalse(decodeLauncherSettings(encodeLauncherSettings(LauncherSettings())).appearance.reducedTransparency)
    }

    @Test
    fun settingsSavedWithReducedTransparencyInTheAdaptiveMotionBlockDecodeItIntoAppearance() {
        val legacy =
            decodeLauncherSettings(
                """
                {
                  "cards": { "timeScapeAppearance": { "motion": { "reducedTransparency": true } } }
                }
                """.trimIndent(),
            )
        val legacyUnfolded =
            decodeLauncherSettings(
                """
                {
                  "cards": { "adaptiveStageUnfoldedAppearance": { "motion": { "reducedTransparency": true } } }
                }
                """.trimIndent(),
            )

        assertTrue(legacy.appearance.reducedTransparency)
        assertTrue(legacy.resolveLiquidGlass().reducedTransparency)
        assertTrue(legacyUnfolded.appearance.reducedTransparency)
    }

    @Test
    fun anExplicitTopLevelReducedTransparencyValueWinsOverTheLegacyOne() {
        val decoded =
            decodeLauncherSettings(
                """
                {
                  "appearance": { "reducedTransparency": false },
                  "cards": { "timeScapeAppearance": { "motion": { "reducedTransparency": true } } }
                }
                """.trimIndent(),
            )

        assertFalse(decoded.appearance.reducedTransparency)
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
    fun defaultsMissingAppearanceSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(WallpaperSettings.system(), decodedSettings.appearance.wallpaper)
        assertEquals(HomeSystemBars(), decodedSettings.appearance.homeSystemBars)
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
    fun roundTripsCustomAccentIncludingAlpha() {
        val colors =
            LauncherThemeColors(accentArgb = 0x80405060.toInt())

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
    fun aStoredThemePresetFromAnOlderBuildIsIgnored() {
        val expected = decodeLauncherSettings("{\"appearance\": {}}").appearance
        val presets = listOf("MATERIAL", "MINIMAL", "RETRO", "GLASS", "TERMINAL", "CUSTOM", "VICTORIAN", "UNKNOWN")
        presets.forEach { preset ->
            assertEquals(
                expected,
                decodeLauncherSettings("{\"appearance\": {\"themePreset\": \"$preset\"}}").appearance,
            )
        }
    }

    @Test
    fun removedCustomColourTargetsAreIgnoredAndTheAccentIsKept() {
        val old =
            """{"appearance": {"themeColors": {"backgroundArgb": 1, "accentArgb": 305419896, "dockArgb": 2,
                "labelArgb": 3, "labelBackgroundArgb": 4}}}"""

        assertEquals(LauncherThemeColors(accentArgb = 305419896), decodeLauncherSettings(old).appearance.themeColors)
    }

    @Test
    fun theRemovedLiquidGlassEnabledFlagIsIgnored() {
        val off = decodeLauncherSettings("""{"liquidGlass": {"enabled": false, "spotlightFlowMillis": 700}}""")

        assertEquals(LiquidGlassSettings(spotlightFlowMillis = 700), off.liquidGlass)
        assertFalse(JSONObject(encodeLauncherSettings(off)).getJSONObject("liquidGlass").has("enabled"))
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
                                        HomeGesture.TWO_FINGER_UP to LauncherGestureAction.OPEN_LIBRARY,
                                        HomeGesture.THREE_FINGER_LEFT to LauncherGestureAction.OPEN_STREAM,
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
            LauncherGestureAction.OPEN_LIBRARY,
            decodedSettings.gestures.homeGestures.actionFor(HomeGesture.TWO_FINGER_UP),
        )
        assertEquals(
            LauncherGestureAction.OPEN_STREAM,
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

        assertEquals(LauncherGestureAction.NONE, decodedSettings.gestures.homeSwipe.up)
        assertEquals(LauncherGestureAction.OPEN_SEARCH, decodedSettings.gestures.homeSwipe.down)
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
    fun roundTripsReturnBehaviorAndDefaultsToRestore() {
        ReturnBehavior.entries.forEach { behavior ->
            val settings = LauncherSettings(home = HomeBehaviourSettings(returnBehavior = behavior))
            assertEquals(behavior, decodeLauncherSettings(encodeLauncherSettings(settings)).home.returnBehavior)
        }
        assertEquals(ReturnBehavior.RESTORE, decodeLauncherSettings("{}").home.returnBehavior)
        assertEquals(ReturnBehavior.RESTORE, decodeLauncherSettings("""{"home": 1}""").home.returnBehavior)
        assertEquals(
            ReturnBehavior.RESTORE,
            decodeLauncherSettings("""{"home": {"returnBehavior": "NOT_A_THING"}}""").home.returnBehavior,
        )
    }

    @Test
    fun roundTripsScreenContentProtectionAndDefaultsToOff() {
        listOf(true, false).forEach { enabled ->
            val settings = LauncherSettings(privacy = PrivacySettings(protectScreenContent = enabled))
            assertEquals(enabled, decodeLauncherSettings(encodeLauncherSettings(settings)).privacy.protectScreenContent)
        }
        assertEquals(false, decodeLauncherSettings("{}").privacy.protectScreenContent)
        assertEquals(false, decodeLauncherSettings("""{"privacy": 1}""").privacy.protectScreenContent)
        assertEquals(
            false,
            decodeLauncherSettings("""{"privacy": {"protectScreenContent": "yes"}}""").privacy.protectScreenContent,
        )
    }

    @Test
    fun defaultsMissingHapticSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(HapticFeedbackStrength.MEDIUM, decodedSettings.haptics.feedbackStrength)
    }

    @Test
    fun roundTripsLiquidGlassSettings() {
        val settings =
            LauncherSettings(
                liquidGlass =
                    LiquidGlassSettings(spotlightFlowMillis = 650),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(settings.liquidGlass, decodedSettings.liquidGlass)
    }

    @Test
    fun roundTripsStreamSettings() {
        val settings =
            LauncherSettings(
                stream =
                    StreamSettings(
                        cardTintPercent = 36,
                        cardTopGlow = true,
                        openCardPlacement = OpenCardPlacement.ABOVE_DOCK,
                        cardActionsPlacement = CardActionsPlacement.ON_CARD,
                    ),
            )

        assertEquals(settings.stream, decodeLauncherSettings(encodeLauncherSettings(settings)).stream)
    }

    @Test
    fun streamSettingsDefaultToTwentyTwoPercentNoGlowCentreAndActionsInTheDock() {
        val defaults = decodeLauncherSettings("{}").stream

        assertEquals(StreamSettings(), defaults)
        assertEquals(22, defaults.cardTintPercent)
        assertFalse(defaults.cardTopGlow)
        assertEquals(OpenCardPlacement.CENTRE, defaults.openCardPlacement)
        assertEquals(CardActionsPlacement.IN_DOCK, defaults.cardActionsPlacement)
    }

    @Test
    fun aBlobSavedBeforeStreamSettingsExistedDecodesEverythingElseUnchanged() {
        val decoded = decodeLauncherSettings("""{"version": 11, "liquidGlass": {"spotlightFlowMillis": 700}}""")

        assertEquals(StreamSettings(), decoded.stream)
        assertEquals(700, decoded.liquidGlass.spotlightFlowMillis)
    }

    @Test
    fun streamSettingsHoldTheTintToItsRangeAndReadAnUnknownPlacementAsTheDefault() {
        val json =
            """{"stream": {"cardTintPercent": 400, "openCardPlacement": "FLOATING", """ +
                """"cardActionsPlacement": "ON_CARD"}}"""
        val decoded = decodeLauncherSettings(json).stream

        assertEquals(CardTint.MAX_STRENGTH_PERCENT, decoded.cardTintPercent)
        assertEquals(OpenCardPlacement.CENTRE, decoded.openCardPlacement)
        assertEquals(CardActionsPlacement.ON_CARD, decoded.cardActionsPlacement)
        assertEquals(StreamSettings(), decodeLauncherSettings("""{"stream": "broken"}""").stream)
    }

    @Test
    fun theDeferredGrowsOutOfTheDockPlacementIsStored() {
        val settings =
            LauncherSettings(stream = StreamSettings(openCardPlacement = OpenCardPlacement.GROWS_OUT_OF_DOCK))

        assertEquals(
            OpenCardPlacement.GROWS_OUT_OF_DOCK,
            decodeLauncherSettings(encodeLauncherSettings(settings)).stream.openCardPlacement,
        )
    }

    @Test
    fun roundTripsTheDockMotionSettings() {
        val settings =
            LauncherSettings(
                motion =
                    MotionSettings(
                        dock =
                            DockMotionSettings(
                                speedMillis = 300,
                                trampolinePercent = 0,
                                inertiaPercent = 180,
                                magnetPercent = 70,
                                holdMillis = 400,
                                ticks = false,
                            ),
                    ),
            )

        assertEquals(settings.motion.dock, decodeLauncherSettings(encodeLauncherSettings(settings)).motion.dock)
    }

    @Test
    fun aBlobSavedBeforeTheDockMotionSettingsExistedDecodesToTheDefaults() {
        // The motion block as the previous build wrote it: no `dock` key at all.
        val old =
            """{"version": 11, "motion": {"reducedMotionPreference": "ON", "reducedMotion": true, """ +
                """"performanceTargetFps": "FPS_90"}}"""
        val decoded = decodeLauncherSettings(old)

        assertEquals(DockMotionSettings(), decoded.motion.dock)
        assertEquals(ReducedMotionPreference.ON, decoded.motion.reducedMotionPreference)
        assertEquals(MotionPerformanceTargetFps.FPS_90, decoded.motion.performanceTargetFps)
        assertEquals(DockMotionSettings(), decodeLauncherSettings("{}").motion.dock)
        assertEquals(HintSettings(), decodeLauncherSettings("{}").hints)
    }

    @Test
    fun theDockMotionDefaultsAreTodaysConstants() {
        val defaults = DockMotionSettings()

        assertEquals(200, defaults.speedMillis)
        assertEquals(220, defaults.holdMillis)
        assertEquals(DockMotionTuning(), defaults.tuning())
        assertTrue(defaults.ticks)
    }

    @Test
    fun outOfRangeDockMotionValuesAreHeldToTheirRangesAndAMissingKeyKeepsItsDefault() {
        val json =
            """{"motion": {"dock": {"speedMillis": 9000, "trampolinePercent": -5, "inertiaPercent": 9999, """ +
                """"holdMillis": 1}}}"""
        val decoded = decodeLauncherSettings(json).motion.dock

        assertEquals(DockMotionSettings.MAX_SPEED_MILLIS, decoded.speedMillis)
        assertEquals(0, decoded.trampolinePercent)
        assertEquals(DockMotionSettings.MAX_INERTIA_PERCENT, decoded.inertiaPercent)
        assertEquals(DockMotionSettings.MIN_HOLD_MILLIS, decoded.holdMillis)
        assertEquals(DockMotionSettings.DEFAULT_MAGNET_PERCENT, decoded.magnetPercent)
        assertTrue(decoded.ticks)
        assertEquals(DockMotionSettings(), decodeLauncherSettings("""{"motion": {"dock": "broken"}}""").motion.dock)
    }

    @Test
    fun theFirstRunHintIsStoredAsSeenAndAnOldBlobHasNotSeenIt() {
        val seen = LauncherSettings(hints = HintSettings(dockPressSeen = true))

        assertTrue(decodeLauncherSettings(encodeLauncherSettings(seen)).hints.dockPressSeen)
        assertFalse(decodeLauncherSettings("""{"version": 11}""").hints.dockPressSeen)
        assertFalse(decodeLauncherSettings("""{"hints": 3}""").hints.dockPressSeen)
    }

    @Test
    fun defaultsMissingLiquidGlassSettings() {
        // A settings JSON saved before this field existed decodes to the same conservative
        // defaults a fresh install gets, rather than failing or leaving the field unset.
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(LiquidGlassSettings(), decodedSettings.liquidGlass)
    }

    @Test
    fun coercesTheOutOfRangeSpotlightFlowOnDecode() {
        val json = """{"liquidGlass": {"enabled": true, "spotlightFlowMillis": 5000}}"""

        assertEquals(1000, decodeLauncherSettings(json).liquidGlass.spotlightFlowMillis)
    }

    @Test
    fun theRemovedFrostAndRefractionStrengthsStillDecodeAndAreDropped() {
        // Blobs written while glass blurred and refracted a backdrop carry both strengths.
        val json =
            """
            {
                "liquidGlass": {
                    "enabled": true,
                    "frostStrengthPercent": 70,
                    "refractionStrengthPercent": 35,
                    "spotlightFlowMillis": 650
                },
                "appearance": {"reducedTransparency": true}
            }
            """.trimIndent()

        val decoded = decodeLauncherSettings(json)

        assertEquals(LiquidGlassSettings(spotlightFlowMillis = 650), decoded.liquidGlass)
        assertTrue(decoded.appearance.reducedTransparency)
        val reEncoded = JSONObject(encodeLauncherSettings(decoded)).getJSONObject("liquidGlass")
        assertFalse(reEncoded.has("frostStrengthPercent"))
        assertFalse(reEncoded.has("refractionStrengthPercent"))
        assertEquals(650, reEncoded.getInt("spotlightFlowMillis"))
    }

    @Test
    fun roundTripsMotionSettings() {
        ReducedMotionPreference.entries.forEach { preference ->
            val settings =
                LauncherSettings(
                    motion =
                        MotionSettings(
                            reducedMotionPreference = preference,
                            performanceTargetFps = MotionPerformanceTargetFps.FPS_90,
                        ),
                )

            val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

            assertEquals(preference, decodedSettings.motion.reducedMotionPreference)
            assertEquals(MotionPerformanceTargetFps.FPS_90, decodedSettings.motion.performanceTargetFps)
        }
    }

    @Test
    fun neverPersistsTheRuntimeSystemReducedMotionState() {
        val settings = LauncherSettings(motion = MotionSettings(systemReducedMotion = true))

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(false, decodedSettings.motion.systemReducedMotion)
        assertEquals(MotionSettings(), decodedSettings.motion)
    }

    @Test
    fun migratesLegacyReducedMotionTrueToOn() {
        val decodedSettings = decodeLauncherSettings("""{ "motion": { "reducedMotion": true } }""")

        assertEquals(ReducedMotionPreference.ON, decodedSettings.motion.reducedMotionPreference)
        assertEquals(true, decodedSettings.motion.reducedMotion)
    }

    @Test
    fun migratesLegacyReducedMotionFalseToSystem() {
        val decodedSettings = decodeLauncherSettings("""{ "motion": { "reducedMotion": false } }""")

        assertEquals(ReducedMotionPreference.SYSTEM, decodedSettings.motion.reducedMotionPreference)
    }

    @Test
    fun prefersTriStatePreferenceOverLegacyBoolean() {
        val decodedSettings =
            decodeLauncherSettings(
                """{ "motion": { "reducedMotion": true, "reducedMotionPreference": "OFF" } }""",
            )

        assertEquals(ReducedMotionPreference.OFF, decodedSettings.motion.reducedMotionPreference)
    }

    @Test
    fun defaultsMalformedReducedMotionPreferenceToSystem() {
        val decodedSettings = decodeLauncherSettings("""{ "motion": { "reducedMotionPreference": "SOMETIMES" } }""")

        assertEquals(ReducedMotionPreference.SYSTEM, decodedSettings.motion.reducedMotionPreference)
    }

    @Test
    fun defaultsMissingMotionSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(ReducedMotionPreference.SYSTEM, decodedSettings.motion.reducedMotionPreference)
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
    fun defaultsMalformedSettingsSectionsIndependently() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "appearance": [],
                  "gestures": [],
                  "haptics": 1,
                  "motion": true
                }
                """.trimIndent(),
            )

        assertEquals(AppearanceSettings(), decodedSettings.appearance)
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
    fun decodesAnUnknownGestureActionAsNoAction() {
        // An unknown stored name leaves the gesture unbound rather than reviving its default.
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "gestures": {
                    "homeSwipe": {
                      "left": "UNKNOWN"
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(LauncherGestureAction.NONE, decodedSettings.gestures.homeSwipe.left)
        assertEquals(LauncherGestureAction.OPEN_SEARCH, decodedSettings.gestures.homeSwipe.down)
    }

    @Test
    fun decodesAWholeSettingsFileCarryingRemovedGestureNames() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "gestures": {
                    "homeGestures": {
                      "THREE_FINGER_UP": "NEXT_MODE",
                      "THREE_FINGER_DOWN": "PREVIOUS_MODE",
                      "TWO_FINGER_LEFT": "ENTER_ADAPTIVE_STAGE",
                      "TWO_FINGER_RIGHT": "EXIT_ADAPTIVE_STAGE",
                      "ONE_FINGER_UP": "OPEN_APP_DRAWER",
                      "PINCH_OUT": "OPEN_APP_DRAWER"
                    },
                    "dockGestures": {
                      "swipeUp": "PREVIOUS_MODE"
                    }
                  }
                }
                """.trimIndent(),
            )

        val gestures = decodedSettings.gestures.homeGestures
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.THREE_FINGER_UP))
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.THREE_FINGER_DOWN))
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.TWO_FINGER_LEFT))
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.TWO_FINGER_RIGHT))
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.ONE_FINGER_UP))
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.PINCH_OUT))
    }

    @Test
    fun theWorkspaceMenuAndStageGestureActionsRemovedByTheDockHandleDecodeToNoAction() {
        val decodedSettings =
            decodeLauncherSettings(
                """
                {
                  "gestures": {
                    "homeGestures": {
                      "THREE_FINGER_UP": "OPEN_WORKSPACE_MENU",
                      "THREE_FINGER_LEFT": "SELECT_NEXT_APP_STAGE",
                      "THREE_FINGER_RIGHT": "SELECT_PREVIOUS_APP_STAGE",
                      "TWO_FINGER_UP": "OPEN_SEARCH",
                      "TWO_FINGER_LEFT": "OPEN_LIBRARY",
                      "TWO_FINGER_RIGHT": "OPEN_STREAM"
                    }
                  }
                }
                """.trimIndent(),
            )

        val gestures = decodedSettings.gestures.homeGestures
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.THREE_FINGER_UP))
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.THREE_FINGER_LEFT))
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.THREE_FINGER_RIGHT))
        assertEquals(LauncherGestureAction.OPEN_SEARCH, gestures.actionFor(HomeGesture.TWO_FINGER_UP))
        assertEquals(LauncherGestureAction.OPEN_LIBRARY, gestures.actionFor(HomeGesture.TWO_FINGER_LEFT))
        assertEquals(LauncherGestureAction.OPEN_STREAM, gestures.actionFor(HomeGesture.TWO_FINGER_RIGHT))
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
                rss =
                    RssSettings(
                        feeds = listOf(feed),
                        refreshInterval = FeedRefreshIntervalOption.HOURS_1,
                        backgroundWifiOnly = false,
                        backgroundChargingOnly = true,
                    ),
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
    fun legacyRssRefreshIntervalNamesDecodeToOffSoNobodyIsOptedIn() {
        listOf("MINUTES_30", "MINUTES_60", "MINUTES_180", "MINUTES_360").forEach { legacy ->
            val decoded = decodeLauncherSettings("""{"rss": {"refreshInterval": "$legacy"}}""")

            assertEquals(FeedRefreshIntervalOption.OFF, decoded.rss.refreshInterval)
        }
    }

    @Test
    fun rssBackgroundRefreshDefaultsToOffWifiOnlyAndNotChargingOnly() {
        val decoded = decodeLauncherSettings("{}").rss

        assertEquals(FeedRefreshIntervalOption.OFF, decoded.refreshInterval)
        assertEquals(true, decoded.backgroundWifiOnly)
        assertEquals(false, decoded.backgroundChargingOnly)
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

    /** A settings blob as the P8 build wrote it: it still carries every key P9 removed. */
    private val p8Blob =
        """
        {
          "version": 10,
          "appDrawer": {
            "libraryViews": { "PHONE": "grid" },
            "iconGridColumns": 5,
            "afterLeavingLibrary": "HOME"
          },
          "appearance": {
            "fullscreenHome": true,
            "hideStatusBarOnHome": false,
            "hideNavigationBarOnHome": false,
            "reducedTransparency": true,
            "themeMode": "DARK",
            "themeAccent": "TEAL",
            "wallpaper": { "source": "SOLID_COLOR" }
          },
          "cards": {
            "stagePreferencesByLayout": [],
            "timeScapeAppearance": { "version": 2, "geometry": { "visibleDepth": 5 } },
            "adaptiveStageUnfoldedAppearance": { "version": 2 },
            "timeScapeTemplateId": "shared-canvas",
            "timeScapePaneArrangement": "SPLIT",
            "threadMessageOrder": "RECENT_FIRST",
            "threadCardGrouping": "PER_MESSAGE",
            "foldedShowAllNotifications": true,
            "unfoldedShowAllNotifications": true,
            "showStageSpine": true,
            "pageVerticalOffsetDp": 40
          },
          "contextual": { "enabled": true },
          "haptics": { "feedbackStrength": "STRONG" },
          "home": { "returnBehavior": "FIRST_PAGE" },
          "rss": { "refreshInterval": "OFF" },
          "search": { "resultPresentation": "LIST" }
        }
        """.trimIndent()

    @Test
    fun aBlobCarryingEveryKeyP9RemovedStillDecodesAndKeepsTheSettingsThatSurvive() {
        val decoded = decodeLauncherSettings(p8Blob)

        assertEquals(LibraryView.Grid, decoded.appDrawer.libraryViewFor(HomeLayoutDeviceClass.PHONE))
        assertEquals(5, decoded.appDrawer.iconGridColumns)
        assertEquals(LauncherThemeMode.DARK, decoded.appearance.themeMode)
        assertEquals(LauncherThemeAccent.TEAL, decoded.appearance.themeAccent)
        assertEquals(WallpaperSource.SOLID_COLOR, decoded.appearance.wallpaper.source)
        assertEquals(HapticFeedbackStrength.STRONG, decoded.haptics.feedbackStrength)
        assertEquals(ReturnBehavior.FIRST_PAGE, decoded.home.returnBehavior)
        assertEquals(SearchResultPresentation.LIST, decoded.search.resultPresentation)
    }

    @Test
    fun theOldFullscreenFlagBecomesBothBarsHiddenAndReducedTransparencyIsKept() {
        val decoded = decodeLauncherSettings(p8Blob)

        assertEquals(
            HomeSystemBars(hideStatusBarOnHome = true, hideNavigationBarOnHome = true),
            decoded.appearance.homeSystemBars,
        )
        assertTrue(decoded.appearance.reducedTransparency)
    }

    @Test
    fun reEncodingThatBlobDropsTheRemovedKeysAndWritesTheCurrentVersion() {
        val encoded = JSONObject(encodeLauncherSettings(decodeLauncherSettings(p8Blob)))

        assertEquals(LAUNCHER_SETTINGS_JSON_VERSION, encoded.getInt("version"))
        assertFalse(encoded.has("cards"))
        assertFalse(encoded.has("contextual"))
        assertFalse(encoded.getJSONObject("appDrawer").has("afterLeavingLibrary"))
        assertFalse(encoded.getJSONObject("appearance").has("fullscreenHome"))
        assertEquals(true, encoded.getJSONObject("appearance").getBoolean("hideStatusBarOnHome"))
        assertEquals(true, encoded.getJSONObject("appearance").getBoolean("hideNavigationBarOnHome"))
    }

    @Test
    fun theCodecReadsTheVersionAndOnlyFoldsTheFullscreenFlagForOlderBlobs() {
        val older =
            decodeLauncherSettings("""{ "version": 10, "appearance": { "fullscreenHome": true } }""")
        val unversioned = decodeLauncherSettings("""{ "appearance": { "fullscreenHome": true } }""")
        val current =
            decodeLauncherSettings(
                """{ "version": $LAUNCHER_SETTINGS_JSON_VERSION, "appearance": { "fullscreenHome": true } }""",
            )

        assertTrue(older.appearance.homeSystemBars.statusBarHidden)
        assertTrue(unversioned.appearance.homeSystemBars.navigationBarHidden)
        assertFalse(current.appearance.homeSystemBars.statusBarHidden)
        assertEquals(10, storedLauncherSettingsVersion(p8Blob))
        assertEquals(0, storedLauncherSettingsVersion("{}"))
        assertEquals(null, storedLauncherSettingsVersion("not json"))
    }

    @Test
    fun aBlobFromANewerBuildDecodesWhatItCanAndNeverThrows() {
        val newer =
            decodeLauncherSettings(
                """
                {
                  "version": ${LAUNCHER_SETTINGS_JSON_VERSION + 5},
                  "appearance": { "themeMode": "LIGHT", "themeAccent": "FUTURE_ACCENT", "somethingNew": [1, 2] },
                  "haptics": { "feedbackStrength": "LIGHT" },
                  "home": "no longer an object",
                  "motion": { "reducedMotionPreference": { "nested": true } },
                  "aSectionThisBuildNeverHeardOf": { "a": 1 }
                }
                """.trimIndent(),
            )

        assertEquals(LauncherThemeMode.LIGHT, newer.appearance.themeMode)
        assertEquals(LauncherThemeAccent.DEFAULT, newer.appearance.themeAccent)
        assertEquals(HapticFeedbackStrength.LIGHT, newer.haptics.feedbackStrength)
        assertEquals(LauncherSettings().home, newer.home)
        assertEquals(LauncherSettings().motion, newer.motion)
    }

    @Test
    fun aBlobFromANewerBuildIsFlaggedSoItsCopyIsKeptBeforeBeingOverwritten() {
        assertTrue(isNewerThanThisBuild("""{ "version": ${LAUNCHER_SETTINGS_JSON_VERSION + 1} }"""))
        assertFalse(isNewerThanThisBuild("""{ "version": $LAUNCHER_SETTINGS_JSON_VERSION }"""))
        assertFalse(isNewerThanThisBuild(p8Blob))
        assertFalse(isNewerThanThisBuild("{}"))
        assertFalse(isNewerThanThisBuild("not json"))
        assertFalse(isNewerThanThisBuild(null))
    }

    @Test
    fun roundTripsTheHomeModeAndOldBlobsDecodeToPlacedItems() {
        HomeMode.entries.forEach { mode ->
            val settings = LauncherSettings(home = HomeBehaviourSettings(mode = mode))
            assertEquals(mode, decodeLauncherSettings(encodeLauncherSettings(settings)).home.mode)
        }
        // A blob from before Home mode has a home section with only the return behaviour (or none at all).
        assertEquals(
            HomeMode.PlacedItems,
            decodeLauncherSettings("""{"version": 11, "home": {"returnBehavior": "RESTORE"}}""").home.mode,
        )
        assertEquals(HomeMode.PlacedItems, decodeLauncherSettings("""{"version": 11}""").home.mode)
        assertEquals(HomeMode.PlacedItems, decodeLauncherSettings("""{"home": {"mode": "carousel"}}""").home.mode)
        assertEquals(HomeMode.PlacedItems, decodeLauncherSettings("""{"home": {"mode": 4}}""").home.mode)
        // The return behaviour next to it is not disturbed.
        val both =
            LauncherSettings(home = HomeBehaviourSettings(ReturnBehavior.START_PAGE, HomeMode.AppLibrary))
        assertEquals(both.home, decodeLauncherSettings(encodeLauncherSettings(both)).home)
    }

    @Test
    fun roundTripsTheFeatureSwitchesAndOldBlobsDecodeToTodaysBehaviour() {
        val features = FeatureSettings(stream = false, dockLiveApps = false, library = false, simpleMode = true)
        val settings = LauncherSettings(features = features)
        assertEquals(features, decodeLauncherSettings(encodeLauncherSettings(settings)).features)
        assertEquals(FeatureSettings(), decodeLauncherSettings(encodeLauncherSettings(LauncherSettings())).features)
        assertEquals(FeatureSettings(), decodeLauncherSettings("""{"version": 11}""").features)
        assertEquals(FeatureSettings(), decodeLauncherSettings("""{"features": "off"}""").features)
        assertEquals(
            FeatureSettings(library = false),
            decodeLauncherSettings("""{"features": {"library": false, "stream": "no", "simpleMode": 1}}""").features,
        )
    }

    @Test
    fun theStoredPagedLibraryViewRoundTripsAndOtherViewsKeepTheirs() {
        val settings =
            LauncherSettings(
                appDrawer =
                    AppDrawerSettings(
                        libraryViews =
                            mapOf(
                                HomeLayoutDeviceClass.PHONE to LibraryView.Paged,
                                HomeLayoutDeviceClass.TABLET to LibraryView.AlphaList,
                            ),
                    ),
            )
        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))
        assertEquals(LibraryView.Paged, decoded.appDrawer.libraryViewFor(HomeLayoutDeviceClass.PHONE))
        assertEquals(LibraryView.AlphaList, decoded.appDrawer.libraryViewFor(HomeLayoutDeviceClass.TABLET))
        // A blob that stored nothing for a class still draws the default.
        assertEquals(LibraryView.Categories, decoded.appDrawer.libraryViewFor(HomeLayoutDeviceClass.FOLDABLE))
    }
}
