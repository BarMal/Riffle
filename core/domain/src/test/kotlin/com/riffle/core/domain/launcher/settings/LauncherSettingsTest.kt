package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherSettingsTest {
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
    }

    @Test
    fun defaultsHapticFeedbackStrengthToMedium() {
        val settings = LauncherSettings()

        assertEquals(HapticFeedbackStrength.MEDIUM, settings.haptics.feedbackStrength)
    }

    @Test
    fun defaultsOverlayDockToDisabled() {
        val settings = LauncherSettings()

        assertEquals(false, settings.overlayDock.enabled)
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
}
