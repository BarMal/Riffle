package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HapticSettings
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSettingsJsonCodecTest {
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
    }

    @Test
    fun defaultsMissingAppearanceSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(WallpaperSettings.system(), decodedSettings.appearance.wallpaper)
    }

    @Test
    fun roundTripsHomeSwipeGestureActions() {
        val settings =
            LauncherSettings(
                gestures =
                    GestureSettings(
                        homeSwipe =
                            HomeSwipeGestureSettings(
                                up = LauncherGestureAction.OPEN_SEARCH,
                                down = LauncherGestureAction.NONE,
                                left = LauncherGestureAction.OPEN_SETTINGS,
                                right = LauncherGestureAction.ENTER_HOME_EDIT_MODE,
                            ),
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

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
                        showLabels = true,
                    ),
            )

        val decodedSettings = decodeLauncherSettings(encodeLauncherSettings(settings))

        assertEquals(true, decodedSettings.overlayDock.enabled)
        assertEquals(OverlayDockEdge.START, decodedSettings.overlayDock.edge)
        assertEquals(24, decodedSettings.overlayDock.handleThicknessDp)
        assertEquals(96, decodedSettings.overlayDock.handleHeightDp)
        assertEquals(-48, decodedSettings.overlayDock.verticalOffsetDp)
        assertEquals(65, decodedSettings.overlayDock.handleAlphaPercent)
        assertEquals(true, decodedSettings.overlayDock.showLabels)
    }

    @Test
    fun defaultsMissingOverlayDockSettings() {
        val decodedSettings = decodeLauncherSettings("{}")

        assertEquals(OverlayDockSettings(), decodedSettings.overlayDock)
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
}
