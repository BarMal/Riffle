package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.contextual.ContextualSettings
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.HomeSystemBars
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
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
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.settings.withHomeSystemBars
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSettingsJsonCodecTest {
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
        val settings = LauncherSettings(appearance = AppearanceSettings(themePreset = LauncherThemePreset.VICTORIAN))

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(LauncherThemePreset.VICTORIAN, decodedSettings.appearance.themePreset)
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
