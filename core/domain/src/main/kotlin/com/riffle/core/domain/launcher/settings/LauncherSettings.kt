package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.cards.CardsChapterId
import com.riffle.core.domain.launcher.cards.CardsChapterPreferences
import com.riffle.core.domain.launcher.contextual.ContextualSettings
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.WallpaperSettings

data class LauncherSettings(
    val appDrawer: AppDrawerSettings = AppDrawerSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val cards: CardsSettings = CardsSettings(),
    val contextual: ContextualSettings = ContextualSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val haptics: HapticSettings = HapticSettings(),
    val motion: MotionSettings = MotionSettings(),
    val overlayDock: OverlayDockSettings = OverlayDockSettings(),
    val search: SearchSettings = SearchSettings(),
)

/** Durable presentation preferences for the launcher app drawer. */
data class AppDrawerSettings(
    val presentation: AppDrawerPresentation = AppDrawerPresentation.LIST,
    val iconGridColumns: Int = DEFAULT_APP_DRAWER_ICON_GRID_COLUMNS,
)

enum class AppDrawerPresentation {
    LIST,
    ICONS,
}

const val MIN_APP_DRAWER_ICON_GRID_COLUMNS = 3
const val MAX_APP_DRAWER_ICON_GRID_COLUMNS = 6
const val DEFAULT_APP_DRAWER_ICON_GRID_COLUMNS = 4

fun AppDrawerSettings.coerced(): AppDrawerSettings =
    copy(iconGridColumns = iconGridColumns.coerceIn(MIN_APP_DRAWER_ICON_GRID_COLUMNS, MAX_APP_DRAWER_ICON_GRID_COLUMNS))

/** Durable presentation preference for launcher search results. */
data class SearchSettings(
    val resultPresentation: SearchResultPresentation = SearchResultPresentation.ICONS,
)

enum class SearchResultPresentation {
    ICONS,
    LIST,
}

/** Stored user intent for Cards chapters. Notification content remains transient. */
data class CardsSettings(
    val chapterPreferences: CardsChapterPreferences = CardsChapterPreferences(),
    val stagePreferencesByLayout: Map<HomeLayoutKey, AppStagePreferences> = emptyMap(),
    /** Durable visual intent for the optional TimeScape presentation. */
    val timeScapeAppearance: TimeScapeAppearanceSettings = TimeScapeAppearanceSettings.modern(),
)

/** Resolves TimeScape using the launcher-wide accessibility motion preference. */
fun LauncherSettings.resolveTimeScapeCardStack(
    viewport: TimeScapeViewportDp,
    capabilities: TimeScapeRendererCapabilities = TimeScapeRendererCapabilities(),
): TimeScapeCardStackResolution =
    cards.timeScapeAppearance.resolveCardStack(
        viewport = viewport,
        capabilities = capabilities,
        globalReducedMotion = motion.reducedMotion,
    )

/** Returns variant-specific TimeScape intent, migrating compatible historical Cards intent on read. */
fun CardsSettings.stagePreferencesFor(layoutKey: HomeLayoutKey): AppStagePreferences =
    stagePreferencesByLayout[layoutKey] ?: chapterPreferences.toStagePreferences()

fun CardsSettings.withStagePreferences(
    layoutKey: HomeLayoutKey,
    preferences: AppStagePreferences,
): CardsSettings = copy(stagePreferencesByLayout = stagePreferencesByLayout + (layoutKey to preferences))

/** Materializes the legacy Cards mapping once so later Cards edits cannot alter stage intent. */
fun CardsSettings.withMigratedStagePreferences(layoutKey: HomeLayoutKey): CardsSettings =
    if (layoutKey in stagePreferencesByLayout) {
        this
    } else {
        withStagePreferences(layoutKey, stagePreferencesFor(layoutKey))
    }

fun CardsSettings.withMigratedStagePreferences(layoutKeys: Iterable<HomeLayoutKey>): CardsSettings =
    layoutKeys.fold(this) { settings, layoutKey -> settings.withMigratedStagePreferences(layoutKey) }

private fun CardsChapterPreferences.toStagePreferences(): AppStagePreferences =
    AppStagePreferences(
        pinnedStageIds = pinnedChapterIds.map(CardsChapterId.App::toAppStageId),
        selectedStageId = (selectedChapterId as? CardsChapterId.App)?.toAppStageId(),
    )

private fun CardsChapterId.App.toAppStageId(): AppStageId = AppStageId(packageName, profileId)

data class AppearanceSettings(
    val wallpaper: WallpaperSettings = WallpaperSettings.system(),
    val themeMode: LauncherThemeMode = LauncherThemeMode.SYSTEM,
    val themePreset: LauncherThemePreset = LauncherThemePreset.MATERIAL,
    val themeAccent: LauncherThemeAccent = LauncherThemeAccent.DEFAULT,
    val themeCornerStyle: LauncherThemeCornerStyle = LauncherThemeCornerStyle.PRESET,
    val themeTypography: LauncherThemeTypography = LauncherThemeTypography.PRESET,
    /** Optional ARGB overrides for the launcher surfaces that are visible on Home. */
    val themeColors: LauncherThemeColors = LauncherThemeColors(),
    val fullscreenHome: Boolean = false,
    val hideStatusBarOnHome: Boolean = false,
    val hideNavigationBarOnHome: Boolean = false,
)

/**
 * Renderer-independent custom colour intent. A null value leaves the corresponding theme role intact.
 * Values use Android's packed ARGB representation so alpha survives backup and restore.
 */
data class LauncherThemeColors(
    val backgroundArgb: Int? = null,
    val accentArgb: Int? = null,
    val dockArgb: Int? = null,
    val labelArgb: Int? = null,
    val labelBackgroundArgb: Int? = null,
) {
    fun colorFor(target: LauncherThemeColorTarget): Int? =
        when (target) {
            LauncherThemeColorTarget.BACKGROUND -> backgroundArgb
            LauncherThemeColorTarget.ACCENT -> accentArgb
            LauncherThemeColorTarget.DOCK -> dockArgb
            LauncherThemeColorTarget.LABEL -> labelArgb
            LauncherThemeColorTarget.LABEL_BACKGROUND -> labelBackgroundArgb
        }

    fun withColor(
        target: LauncherThemeColorTarget,
        argb: Int?,
    ): LauncherThemeColors =
        when (target) {
            LauncherThemeColorTarget.BACKGROUND -> copy(backgroundArgb = argb)
            LauncherThemeColorTarget.ACCENT -> copy(accentArgb = argb)
            LauncherThemeColorTarget.DOCK -> copy(dockArgb = argb)
            LauncherThemeColorTarget.LABEL -> copy(labelArgb = argb)
            LauncherThemeColorTarget.LABEL_BACKGROUND -> copy(labelBackgroundArgb = argb)
        }
}

enum class LauncherThemeColorTarget {
    BACKGROUND,
    ACCENT,
    DOCK,
    LABEL,
    LABEL_BACKGROUND,
}

enum class LauncherThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class LauncherThemePreset {
    MATERIAL,
    MINIMAL,
    RETRO,
    GLASS,
    TERMINAL,
    CUSTOM,
}

/** User-selected colour family applied consistently to Material theme roles. */
enum class LauncherThemeAccent {
    DEFAULT,
    BLUE,
    TEAL,
    ROSE,
    AMBER,
}

/** Optional corner override for launcher cards, panels, dock, and settings rows. */
enum class LauncherThemeCornerStyle {
    /** Keep the shape supplied by the selected theme preset. */
    PRESET,
    COMPACT,
    ROUNDED,
}

/** Optional typography override applied through the shared launcher theme. */
enum class LauncherThemeTypography {
    /** Keep the typeface supplied by the selected theme preset. */
    PRESET,
    SYSTEM,
    MONOSPACE,
}

data class GestureSettings(
    val homeGestures: HomeGestureSettings = HomeGestureSettings(),
) {
    val mappings: LauncherGestureMappings
        get() = homeGestures.toLauncherGestureMappings()

    val conflicts: List<LauncherGestureConflict>
        get() =
            HomeGestureConflictDetector
                .conflictsIn(homeGestures)
                .map { conflict ->
                    LauncherGestureConflict(
                        surface = LauncherGestureSurface.HOME_PAGE,
                        action = conflict.action,
                        gestures = conflict.gestures.map(HomeGesture::toLauncherGesture),
                    )
                }

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
    val launchTargets: Map<HomeGesture, LauncherGestureLaunchTarget> = emptyMap(),
) {
    fun actionFor(gesture: HomeGesture): LauncherGestureAction =
        actions[gesture] ?: defaultHomeGestureActions[gesture] ?: LauncherGestureAction.NONE

    fun withAction(
        gesture: HomeGesture,
        action: LauncherGestureAction,
        launchTarget: LauncherGestureLaunchTarget? = null,
    ): HomeGestureSettings =
        copy(
            actions = actions + (gesture to action),
            launchTargets =
                launchTarget?.let { target -> launchTargets + (gesture to target) }
                    ?: (launchTargets - gesture),
        )

    fun launchTargetFor(gesture: HomeGesture): LauncherGestureLaunchTarget? = launchTargets[gesture]
}

sealed interface LauncherGestureLaunchTarget {
    data class App(
        val identity: AppIdentity,
    ) : LauncherGestureLaunchTarget

    data class Shortcut(
        val shortcut: AppShortcut,
    ) : LauncherGestureLaunchTarget
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
    THREE_FINGER_UP,
    THREE_FINGER_DOWN,
    THREE_FINGER_LEFT,
    THREE_FINGER_RIGHT,
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
        HomeGesture.THREE_FINGER_UP to LauncherGestureAction.NONE,
        HomeGesture.THREE_FINGER_DOWN to LauncherGestureAction.NONE,
        HomeGesture.THREE_FINGER_LEFT to LauncherGestureAction.NONE,
        HomeGesture.THREE_FINGER_RIGHT to LauncherGestureAction.NONE,
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
    LAUNCH_APP,
    LAUNCH_APP_SHORTCUT,
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
