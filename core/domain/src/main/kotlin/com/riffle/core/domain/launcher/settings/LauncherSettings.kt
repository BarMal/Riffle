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
    val edge: OverlayDockEdge = OverlayDockEdge.END,
    val handleThicknessDp: Int = DEFAULT_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
    val handleHeightDp: Int = DEFAULT_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
    val verticalOffsetDp: Int = DEFAULT_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
    val handleAlphaPercent: Int = DEFAULT_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
    val expandedIconSizeDp: Int = DEFAULT_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
    val showLabels: Boolean = false,
)

enum class OverlayDockEdge {
    START,
    END,
}

const val DEFAULT_OVERLAY_DOCK_HANDLE_THICKNESS_DP = 18
const val MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP = 6
const val MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP = 120
const val DEFAULT_OVERLAY_DOCK_HANDLE_HEIGHT_DP = 72
const val MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP = 24
const val MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP = 360
const val DEFAULT_OVERLAY_DOCK_VERTICAL_OFFSET_DP = 0
const val MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP = -240
const val MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP = 240
const val DEFAULT_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT = 80
const val MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT = 5
const val MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT = 100
const val DEFAULT_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP = 52
const val MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP = 40
const val MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP = 80

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
