package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass

data class LauncherSettings(
    val appDrawer: AppDrawerSettings = AppDrawerSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val haptics: HapticSettings = HapticSettings(),
    val home: HomeBehaviourSettings = HomeBehaviourSettings(),
    val liquidGlass: LiquidGlassSettings = LiquidGlassSettings(),
    val motion: MotionSettings = MotionSettings(),
    val notificationHiding: NotificationHidingSettings = NotificationHidingSettings(),
    val overlayDock: OverlayDockSettings = OverlayDockSettings(),
    val privacy: PrivacySettings = PrivacySettings(),
    val rss: RssSettings = RssSettings(),
    val search: SearchSettings = SearchSettings(),
)

/**
 * Durable home behaviour. [returnBehavior] picks which page of the active workspace shows when the launcher
 * comes back (Restore by default); it is global, not per workspace, and never changes the workspace.
 */
data class HomeBehaviourSettings(
    val returnBehavior: ReturnBehavior = ReturnBehavior.RESTORE,
)

/**
 * Durable presentation preferences for the Library (the app drawer, ADR 0003): which [LibraryView] each device class
 * draws, and the icon grid's column count. A class with no stored view draws [LibraryView.DEFAULT].
 */
data class AppDrawerSettings(
    val libraryViews: Map<HomeLayoutDeviceClass, LibraryView> = emptyMap(),
    val iconGridColumns: Int = DEFAULT_APP_DRAWER_ICON_GRID_COLUMNS,
) {
    /** The view the Library draws on [deviceClass]; landscape phones share the phone's (as workspaces do). */
    fun libraryViewFor(deviceClass: HomeLayoutDeviceClass): LibraryView =
        libraryViews[deviceClass.workspaceLayoutClass()] ?: LibraryView.DEFAULT

    fun withLibraryView(
        deviceClass: HomeLayoutDeviceClass,
        view: LibraryView,
    ): AppDrawerSettings = copy(libraryViews = libraryViews + (deviceClass.workspaceLayoutClass() to view))
}

/** The app drawer's old presentation choice; only read to carry a stored value over to a [LibraryView]. */
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

data class AppearanceSettings(
    val wallpaper: WallpaperSettings = WallpaperSettings.system(),
    val themeMode: LauncherThemeMode = LauncherThemeMode.SYSTEM,
    val themeAccent: LauncherThemeAccent = LauncherThemeAccent.DEFAULT,
    val themeCornerStyle: LauncherThemeCornerStyle = LauncherThemeCornerStyle.PRESET,
    val themeTypography: LauncherThemeTypography = LauncherThemeTypography.PRESET,
    /** The optional custom accent. */
    val themeColors: LauncherThemeColors = LauncherThemeColors(),
    val hideStatusBarOnHome: Boolean = false,
    val hideNavigationBarOnHome: Boolean = false,
    /**
     * The one off switch for glass: every glass surface draws an opaque tint, samples no backdrop and
     * keeps its sheen faint. Replaces the copy that lived in the adaptive stage's motion settings.
     */
    val reducedTransparency: Boolean = false,
)

/**
 * The one custom colour left: an accent. Null keeps the accent chosen by [LauncherThemeAccent]. The value
 * is Android's packed ARGB so alpha survives backup and restore. The removed background, dock, label and
 * label-background colours are ignored when old data is decoded.
 */
data class LauncherThemeColors(
    val accentArgb: Int? = null,
)

enum class LauncherThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** User-selected colour family applied consistently to Material theme roles. */
enum class LauncherThemeAccent {
    DEFAULT,
    BLUE,
    TEAL,
    ROSE,
    AMBER,
}

/** Corner choice for launcher cards, panels, the dock and settings rows ("Corners" in Settings). */
enum class LauncherThemeCornerStyle {
    /** The standard corners ("Standard" in Settings). */
    PRESET,
    COMPACT,
    ROUNDED,
}

/** Optional typography override applied through the shared launcher theme. */
enum class LauncherThemeTypography {
    /** The standard typeface; the same as [SYSTEM]. */
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

/**
 * Defaults for the bindable home gestures. None of them switches mode or opens the app drawer, and
 * neither can be bound: the dock pull is the only mode-transition trigger, and Library is the app
 * drawer (docs/product/gestures.md).
 */
val defaultHomeGestureActions: Map<HomeGesture, LauncherGestureAction> =
    mapOf(
        HomeGesture.ONE_FINGER_UP to LauncherGestureAction.NONE,
        HomeGesture.ONE_FINGER_DOWN to LauncherGestureAction.OPEN_SEARCH,
        HomeGesture.ONE_FINGER_LEFT to LauncherGestureAction.SELECT_NEXT_HOME_PAGE,
        HomeGesture.ONE_FINGER_RIGHT to LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE,
        HomeGesture.TWO_FINGER_UP to LauncherGestureAction.OPEN_NOTIFICATIONS,
        HomeGesture.TWO_FINGER_DOWN to LauncherGestureAction.OPEN_SETTINGS,
        HomeGesture.TWO_FINGER_LEFT to LauncherGestureAction.NONE,
        HomeGesture.TWO_FINGER_RIGHT to LauncherGestureAction.NONE,
        HomeGesture.THREE_FINGER_UP to LauncherGestureAction.NONE,
        HomeGesture.THREE_FINGER_DOWN to LauncherGestureAction.NONE,
        HomeGesture.THREE_FINGER_LEFT to LauncherGestureAction.NONE,
        HomeGesture.THREE_FINGER_RIGHT to LauncherGestureAction.NONE,
        HomeGesture.PINCH_IN to LauncherGestureAction.ENTER_HOME_EDIT_MODE,
        HomeGesture.PINCH_OUT to LauncherGestureAction.NONE,
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

/**
 * Motion settings. [reducedMotionPreference] is the persisted user intent; [systemReducedMotion] is the
 * platform's current animation state (for example "Remove animations" or an animator duration scale of 0).
 *
 * [systemReducedMotion] is runtime-only: it is never persisted and is projected onto the settings by the
 * launcher shell from a platform source. [reducedMotion] is the single resolved value every surface reads.
 */
data class MotionSettings(
    val reducedMotionPreference: ReducedMotionPreference = ReducedMotionPreference.SYSTEM,
    val performanceTargetFps: MotionPerformanceTargetFps = MotionPerformanceTargetFps.FPS_120,
    val systemReducedMotion: Boolean = false,
) {
    val reducedMotion: Boolean
        get() = reducedMotionPreference.resolve(systemReducedMotion)
}

/** Projects the platform's current reduced-motion state onto these settings without changing stored intent. */
fun LauncherSettings.withSystemReducedMotion(systemReducedMotion: Boolean): LauncherSettings =
    if (motion.systemReducedMotion == systemReducedMotion) {
        this
    } else {
        copy(motion = motion.copy(systemReducedMotion = systemReducedMotion))
    }

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
    val up: LauncherGestureAction = LauncherGestureAction.NONE,
    val down: LauncherGestureAction = LauncherGestureAction.OPEN_SEARCH,
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

    /**
     * Opens the Stream (ADR 0003): the actions centre that replaces Home until Back or the Home button. Added by P4;
     * stored settings written before it never name it, and a stored name this build does not know decodes to no action.
     */
    OPEN_STREAM,

    /** Opens the Library (the app drawer until P7 reworks it), the same place the Dock's pinned-side pull leads. */
    OPEN_LIBRARY,
    ;

    companion object {
        /**
         * The action a stored [name] means, or null when it names none. Mode-switching and app-drawer
         * bindings (NEXT_MODE / PREVIOUS_MODE, their older ENTER_ / EXIT_ADAPTIVE_STAGE names, and
         * OPEN_APP_DRAWER) were removed when the dock pull became the only mode-transition trigger, and the
         * workspace menu and app stage actions (OPEN_WORKSPACE_MENU, SELECT_NEXT_APP_STAGE,
         * SELECT_PREVIOUS_APP_STAGE) when the Dock became the handle of the Stream and the Library (P5);
         * like any other unknown name they decode to null, which callers treat as "no action".
         */
        fun fromStoredName(name: String): LauncherGestureAction? = entries.firstOrNull { action -> action.name == name }
    }
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
