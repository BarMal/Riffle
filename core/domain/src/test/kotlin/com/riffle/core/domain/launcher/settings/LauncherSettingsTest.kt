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
}
