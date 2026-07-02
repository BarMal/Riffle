package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.home.WallpaperSettings

data class LauncherSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val haptics: HapticSettings = HapticSettings(),
    val overlayDock: OverlayDockSettings = OverlayDockSettings(),
)

data class AppearanceSettings(
    val wallpaper: WallpaperSettings = WallpaperSettings.system(),
)

data class GestureSettings(
    val homeSwipe: HomeSwipeGestureSettings = HomeSwipeGestureSettings(),
)

data class HapticSettings(
    val feedbackStrength: HapticFeedbackStrength = HapticFeedbackStrength.MEDIUM,
)

data class OverlayDockSettings(
    val enabled: Boolean = false,
)

data class HomeSwipeGestureSettings(
    val up: LauncherGestureAction = LauncherGestureAction.OPEN_APP_DRAWER,
    val down: LauncherGestureAction = LauncherGestureAction.OPEN_NOTIFICATIONS,
    val left: LauncherGestureAction = LauncherGestureAction.SELECT_NEXT_HOME_PAGE,
    val right: LauncherGestureAction = LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE,
)

enum class HomeSwipeGestureDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

enum class LauncherGestureAction {
    NONE,
    OPEN_APP_DRAWER,
    OPEN_NOTIFICATIONS,
    OPEN_SEARCH,
    OPEN_SETTINGS,
    ENTER_HOME_EDIT_MODE,
    SELECT_NEXT_HOME_PAGE,
    SELECT_PREVIOUS_HOME_PAGE,
}

enum class HapticFeedbackStrength {
    OFF,
    LIGHT,
    MEDIUM,
    STRONG,
}

interface LauncherSettingsRepository {
    fun loadLauncherSettings(): LauncherSettings?

    fun saveLauncherSettings(settings: LauncherSettings)
}
