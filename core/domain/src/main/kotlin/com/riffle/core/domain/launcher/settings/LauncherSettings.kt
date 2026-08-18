package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneArrangement
import com.riffle.core.domain.launcher.cards.AdaptiveStageRailSide
import com.riffle.core.domain.launcher.cards.AdaptiveStageTemplateCatalogDefaults
import com.riffle.core.domain.launcher.cards.AdaptiveStageTemplateId
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.contextual.ContextualSettings
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.rss.FeedStagePreferences

data class LauncherSettings(
    val appDrawer: AppDrawerSettings = AppDrawerSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val cards: CardsSettings = CardsSettings(),
    val contextual: ContextualSettings = ContextualSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val haptics: HapticSettings = HapticSettings(),
    val motion: MotionSettings = MotionSettings(),
    val notificationHiding: NotificationHidingSettings = NotificationHidingSettings(),
    val overlayDock: OverlayDockSettings = OverlayDockSettings(),
    val rss: RssSettings = RssSettings(),
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

/** Stored user intent for the AdaptiveStage app-stage surface. */
data class CardsSettings(
    val stagePreferencesByLayout: Map<HomeLayoutKey, AppStagePreferences> = emptyMap(),
    /** Durable pin/select intent for AdaptiveStage feed stages, mirroring [stagePreferencesByLayout]. */
    val feedStagePreferencesByLayout: Map<HomeLayoutKey, FeedStagePreferences> = emptyMap(),
    /**
     * Durable visual intent for the optional AdaptiveStage presentation -- specifically, the *folded*
     * layout's card stack (the surface that's always present, whether or not a docked rail is also
     * shown). See [unfoldedAppearance] for the docked rail's independent appearance intent.
     */
    val adaptiveStageAppearance: AdaptiveStageAppearanceSettings = AdaptiveStageAppearanceSettings.modern(),
    /**
     * Durable visual intent for the *unfolded* layout's docked rail, fully independent of
     * [adaptiveStageAppearance] -- every field is separately user-adjustable (see #1058). Not yet
     * exposed in settings UI or persisted; both land in #1058, which owns the field-level UI and the
     * accompanying JSON codec extension.
     */
    val unfoldedAppearance: AdaptiveStageAppearanceSettings = AdaptiveStageAppearanceSettings.unfolded(),
    val adaptiveStageTemplateId: AdaptiveStageTemplateId = AdaptiveStageTemplateCatalogDefaults.sharedCanvasId,
    /**
     * Explicit user choice of rail edge, or `null` if the user has never changed it -- in which
     * case the active template's [com.riffle.core.domain.launcher.cards.AdaptiveStageTemplateVariant.railSide]
     * applies, falling back to [com.riffle.core.domain.launcher.cards.AdaptiveStageRailSide.LEADING].
     */
    val adaptiveStageRailSide: AdaptiveStageRailSide? = null,
    /** User-opted alternative to the full-stack surface: a top detail region over the card stack. */
    val adaptiveStagePaneArrangement: AdaptiveStagePaneArrangement = AdaptiveStagePaneArrangement.STACK,
    /** Message order within a conversation's thread view, once its message cards are grouped. */
    val threadMessageOrder: ThreadMessageOrder = ThreadMessageOrder.CHRONOLOGICAL,
    /** Whether a conversation's messages become one card each or one card between them. */
    val threadCardGrouping: ThreadCardGrouping = ThreadCardGrouping.PER_THREAD,
)

/**
 * How a messaging notification that carries message history becomes cards.
 *
 * [PER_MESSAGE] was the only prior behaviour: every message in the history becomes its own card.
 * A single conversation therefore spreads across a stack the user pages through, and each card --
 * sized for a whole notification -- carries one short line. Four unread chats read as a dozen
 * near-empty cards.
 *
 * [PER_THREAD] puts the conversation on one card instead, its messages as the body. Fewer cards,
 * and more on each of them, which is the same problem seen from both ends. It is the default
 * because the split was not a deliberate design so much as the shape the data arrived in.
 */
enum class ThreadCardGrouping {
    PER_MESSAGE,
    PER_THREAD,
}

/** How a conversation's messages are ordered within its thread view. */
enum class ThreadMessageOrder {
    /** Oldest message first, reading top-to-bottom like a conversation. */
    CHRONOLOGICAL,

    /** Newest message first, matching the main stack's own recency order. */
    RECENT_FIRST,
}

/** Resolves AdaptiveStage using the launcher-wide accessibility motion preference. */
fun LauncherSettings.resolveAdaptiveStageCardStack(
    viewport: AdaptiveStageViewportDp,
    capabilities: AdaptiveStageRendererCapabilities = AdaptiveStageRendererCapabilities(),
): AdaptiveStageCardStackResolution =
    cards.adaptiveStageAppearance.resolveCardStack(
        viewport = viewport,
        capabilities = capabilities,
        globalReducedMotion = motion.reducedMotion,
    )

/**
 * Resolves the docked rail's independent [CardsSettings.unfoldedAppearance], sized against [viewport]
 * -- the rail's own physical bounds, not the whole window -- with [AdaptiveStageCardStackRole.RAIL]'s
 * smaller reachability floor.
 */
fun LauncherSettings.resolveAdaptiveStageRailCardStack(
    viewport: AdaptiveStageViewportDp,
    capabilities: AdaptiveStageRendererCapabilities = AdaptiveStageRendererCapabilities(),
): AdaptiveStageCardStackResolution =
    cards.unfoldedAppearance.resolveCardStack(
        viewport = viewport,
        capabilities = capabilities,
        globalReducedMotion = motion.reducedMotion,
        role = AdaptiveStageCardStackRole.RAIL,
    )

/** Returns variant-specific AdaptiveStage intent. */
fun CardsSettings.stagePreferencesFor(layoutKey: HomeLayoutKey): AppStagePreferences =
    stagePreferencesByLayout[layoutKey] ?: AppStagePreferences()

fun CardsSettings.withStagePreferences(
    layoutKey: HomeLayoutKey,
    preferences: AppStagePreferences,
): CardsSettings = copy(stagePreferencesByLayout = stagePreferencesByLayout + (layoutKey to preferences))

/** Returns variant-specific feed stage intent. */
fun CardsSettings.feedStagePreferencesFor(layoutKey: HomeLayoutKey): FeedStagePreferences =
    feedStagePreferencesByLayout[layoutKey] ?: FeedStagePreferences()

fun CardsSettings.withFeedStagePreferences(
    layoutKey: HomeLayoutKey,
    preferences: FeedStagePreferences,
): CardsSettings = copy(feedStagePreferencesByLayout = feedStagePreferencesByLayout + (layoutKey to preferences))

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
    val dockGestures: DockGestureSettings = DockGestureSettings(),
) {
    val mappings: LauncherGestureMappings
        get() = dockGestures.toLauncherGestureMappings(homeGestures.toLauncherGestureMappings())

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
                } +
                LauncherGestureConflictDetector
                    .conflictsIn(mappings)
                    .filter { conflict -> conflict.surface == LauncherGestureSurface.DOCK }

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

/**
 * Durable binding for the Dock swipe-up gesture. Restricted to the mode-switch actions the Dock
 * physically supports: staying put, returning to Standard Home from Cards mode, or opening the
 * app drawer/search. Unlike [HomeGestureSettings], the Dock currently exposes a single gesture.
 */
data class DockGestureSettings(
    val swipeUp: LauncherGestureAction = LauncherGestureAction.EXIT_ADAPTIVE_STAGE,
) {
    companion object {
        val ALLOWED_SWIPE_UP_ACTIONS: Set<LauncherGestureAction> =
            setOf(
                LauncherGestureAction.NONE,
                LauncherGestureAction.EXIT_ADAPTIVE_STAGE,
                LauncherGestureAction.OPEN_APP_DRAWER,
            )
    }
}

val LauncherGestureAction.isValidDockSwipeUpAction: Boolean
    get() = this in DockGestureSettings.ALLOWED_SWIPE_UP_ACTIONS

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
        // Three fingers avoid the platform back/home edges and the one-finger card stack.
        HomeGesture.THREE_FINGER_UP to LauncherGestureAction.ENTER_ADAPTIVE_STAGE,
        HomeGesture.THREE_FINGER_DOWN to LauncherGestureAction.EXIT_ADAPTIVE_STAGE,
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
    ENTER_ADAPTIVE_STAGE,
    EXIT_ADAPTIVE_STAGE,
    SELECT_NEXT_APP_STAGE,
    SELECT_PREVIOUS_APP_STAGE,
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
