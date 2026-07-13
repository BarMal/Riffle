package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.contextual.ContextualSettings
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.WallpaperSettings

data class LauncherSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val contextual: ContextualSettings = ContextualSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val haptics: HapticSettings = HapticSettings(),
    val motion: MotionSettings = MotionSettings(),
    val overlayDock: OverlayDockSettings = OverlayDockSettings(),
)

data class AppearanceSettings(
    val wallpaper: WallpaperSettings = WallpaperSettings.system(),
    val themeMode: LauncherThemeMode = LauncherThemeMode.SYSTEM,
    val themePreset: LauncherThemePreset = LauncherThemePreset.MATERIAL,
    val fullscreenHome: Boolean = false,
    val hideStatusBarOnHome: Boolean = false,
    val hideNavigationBarOnHome: Boolean = false,
)

enum class LauncherThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class LauncherThemePreset {
    MATERIAL,
    MINIMAL,
    VICTORIAN,
    RETRO,
    GLASS,
    TERMINAL,
    CUSTOM,
}

data class GestureSettings(
    val homeGestures: HomeGestureSettings = HomeGestureSettings(),
) {
    val mappings: LauncherGestureMappings
        get() = homeGestures.toLauncherGestureMappings()

    val conflicts: List<LauncherGestureConflict>
        get() = LauncherGestureConflictDetector.conflictsIn(mappings)

    val homeSwipe: HomeSwipeGestureSettings
        get() =
            HomeSwipeGestureSettings(
                up = homeGestures.actionFor(HomeGesture.ONE_FINGER_UP),
                down = homeGestures.actionFor(HomeGesture.ONE_FINGER_DOWN),
                left = homeGestures.actionFor(HomeGesture.ONE_FINGER_LEFT),
                right = homeGestures.actionFor(HomeGesture.ONE_FINGER_RIGHT),
            )
}

data class HomeGestureSettings(
    val actions: Map<HomeGesture, LauncherGestureAction> = defaultHomeGestureActions,
) {
    fun actionFor(gesture: HomeGesture): LauncherGestureAction =
        actions[gesture] ?: defaultHomeGestureActions[gesture] ?: LauncherGestureAction.NONE

    fun withAction(
        gesture: HomeGesture,
        action: LauncherGestureAction,
    ): HomeGestureSettings = copy(actions = actions + (gesture to action))
}

enum class HomeGesture {
    ONE_FINGER_UP,
    ONE_FINGER_DOWN,
    ONE_FINGER_LEFT,
    ONE_FINGER_RIGHT,
    TWO_FINGER_UP,
    TWO_FINGER_DOWN,
    TWO_FINGER_LEFT,
    TWO_FINGER_RIGHT,
    PINCH_IN,
    PINCH_OUT,
}

val defaultHomeGestureActions: Map<HomeGesture, LauncherGestureAction> =
    mapOf(
        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.OPEN_APP_DRAWER,
        HomeGesture.ONE_FINGER_DOWN to LauncherGestureAction.OPEN_NOTIFICATIONS,
        HomeGesture.ONE_FINGER_LEFT to LauncherGestureAction.SELECT_NEXT_HOME_PAGE,
        HomeGesture.ONE_FINGER_RIGHT to LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE,
        HomeGesture.TWO_FINGER_UP to LauncherGestureAction.OPEN_SEARCH,
        HomeGesture.TWO_FINGER_DOWN to LauncherGestureAction.OPEN_SETTINGS,
        HomeGesture.TWO_FINGER_LEFT to LauncherGestureAction.NONE,
        HomeGesture.TWO_FINGER_RIGHT to LauncherGestureAction.NONE,
        HomeGesture.PINCH_IN to LauncherGestureAction.ENTER_HOME_EDIT_MODE,
        HomeGesture.PINCH_OUT to LauncherGestureAction.OPEN_APP_DRAWER,
    )

fun HomeSwipeGestureSettings.toHomeGestureSettings(): HomeGestureSettings =
    HomeGestureSettings(
        actions =
            defaultHomeGestureActions +
                mapOf(
                    HomeGesture.ONE_FINGER_UP to up,
                    HomeGesture.ONE_FINGER_DOWN to down,
                    HomeGesture.ONE_FINGER_LEFT to left,
                    HomeGesture.ONE_FINGER_RIGHT to right,
                ),
    )

fun HomeGestureSettings.toHomeSwipeGestureSettings(): HomeSwipeGestureSettings =
    HomeSwipeGestureSettings(
        up = actionFor(HomeGesture.ONE_FINGER_UP),
        down = actionFor(HomeGesture.ONE_FINGER_DOWN),
        left = actionFor(HomeGesture.ONE_FINGER_LEFT),
        right = actionFor(HomeGesture.ONE_FINGER_RIGHT),
    )

data class HapticSettings(
    val feedbackStrength: HapticFeedbackStrength = HapticFeedbackStrength.MEDIUM,
)

data class MotionSettings(
    val reducedMotion: Boolean = false,
    val performanceTargetFps: MotionPerformanceTargetFps = MotionPerformanceTargetFps.FPS_120,
)

enum class MotionPerformanceTargetFps(
    val framesPerSecond: Int,
) {
    FPS_60(60),
    FPS_90(90),
    FPS_120(120),
    ;

    fun next(): MotionPerformanceTargetFps = entries[(ordinal + 1) % entries.size]
}

data class OverlayDockSettings(
    val enabled: Boolean = false,
    val items: List<AppShortcutItem> = emptyList(),
    val edge: OverlayDockEdge = OverlayDockEdge.END,
    val handleThicknessDp: Int = DEFAULT_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
    val handleHeightDp: Int = DEFAULT_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
    val verticalOffsetDp: Int = DEFAULT_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
    val handleAlphaPercent: Int = DEFAULT_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
    val expandedIconSizeDp: Int = DEFAULT_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
    val expandedOrientation: OverlayDockExpandedOrientation = OverlayDockExpandedOrientation.WIDE,
    val showLabels: Boolean = false,
)

enum class OverlayDockEdge {
    START,
    END,
}

enum class OverlayDockExpandedOrientation {
    WIDE,
    TALL,
}

enum class OverlayDockItemMoveDirection(
    val indexDelta: Int,
) {
    UP(indexDelta = -1),
    DOWN(indexDelta = 1),
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
    ENTER_HOME_PAGE_OVERVIEW,
    ENTER_FULLSCREEN_HOME,
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
