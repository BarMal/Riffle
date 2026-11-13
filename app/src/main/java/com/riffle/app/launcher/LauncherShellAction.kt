package com.riffle.app.launcher

import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.FolderItemMoveDirection
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherTemplateId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LibraryExitTrigger
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.notifications.NotificationHideRuleId
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherGestureLaunchTarget
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockItemMoveDirection
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.ReturnBehavior

sealed interface LauncherShellAction {
    data object RequestDefaultHome : LauncherShellAction

    data object OpenHome : LauncherShellAction

    data object OpenDefaultHome : LauncherShellAction

    data object OpenAppDrawer : LauncherShellAction

    data object OpenSearch : LauncherShellAction

    data object OpenNotifications : LauncherShellAction

    data object OpenSettings : LauncherShellAction

    /** Opens the Stream over Home (a gesture bound to `OPEN_STREAM`, the Dock's "Open Stream" action). */
    data object OpenStream : LauncherShellAction

    data class OpenSettingsPage(
        val page: SettingsPage,
        /** The setting to scroll to on that page, when the request came from search. */
        val anchor: SettingKey? = null,
    ) : LauncherShellAction

    data object RequestNotificationAccess : LauncherShellAction

    /** Runs a contextual action for one live app-stage notification. */
    data class PerformNotificationStageAction(
        val key: LauncherNotificationKey,
        val action: NotificationStageAction,
    ) : LauncherShellAction

    data object RequestOverlayDockPermission : LauncherShellAction

    /**
     * An explicit user request to allow calendar access: the Settings row, or a surface showing a
     * "needs calendar access" state (which must show its rationale next to the button). Never dispatched
     * at launch or because a calendar source is merely present.
     */
    data object RequestCalendarAccess : LauncherShellAction

    data object ExportLauncherBackup : LauncherShellAction

    data object RequestImportLauncherBackup : LauncherShellAction

    data class ImportLauncherBackup(val document: LauncherBackupDocument) : LauncherShellAction

    data object EnterHomeEditMode : LauncherShellAction

    data object ExitHomeEditMode : LauncherShellAction

    data object EnterHomePageOverview : LauncherShellAction

    data object AddHomePage : LauncherShellAction

    data object DuplicateSelectedHomePage : LauncherShellAction

    data class SelectHomePage(val pageId: LauncherPageId) : LauncherShellAction

    data object SelectPreviousHomePage : LauncherShellAction

    data object SelectNextHomePage : LauncherShellAction

    data object MoveSelectedHomePageLeft : LauncherShellAction

    data object MoveSelectedHomePageRight : LauncherShellAction

    data class MoveHomePage(
        val pageId: LauncherPageId,
        val targetIndex: Int,
    ) : LauncherShellAction

    data object DeleteSelectedHomePage : LauncherShellAction

    data class SelectSelectedHomePageType(val type: LauncherPageType) : LauncherShellAction

    data object ToggleSelectedHomePagePinned : LauncherShellAction

    data class SelectSelectedHomePageGridDimensions(val dimensions: GridDimensions) : LauncherShellAction

    data class SelectHomeGridDimensions(val dimensions: GridDimensions) : LauncherShellAction

    data class SelectHomeGridMargin(
        val horizontalDp: Int,
        val verticalDp: Int,
    ) : LauncherShellAction

    data class SelectLibraryPageCompaction(val enabled: Boolean) : LauncherShellAction

    data class SelectHomeLabelBackgroundAlpha(val alphaPercent: Int) : LauncherShellAction

    data class SelectHomeLabelBackgroundVisible(val visible: Boolean) : LauncherShellAction

    data class SelectHomeIconSize(val sizeDp: Int) : LauncherShellAction

    data class SelectHomeLabelTextSize(val textSizeSp: Int) : LauncherShellAction

    data class SelectHomeLabelTextVisible(val visible: Boolean) : LauncherShellAction

    data class SelectHomeLabelMaxWidth(val maxWidthDp: Int) : LauncherShellAction

    data class SelectHomeLabelMaxLines(val maxLines: Int) : LauncherShellAction

    data class SelectHomeLabelSizing(val sizing: HomeLabelSizing) : LauncherShellAction

    data class SelectLauncherViewMode(val mode: LauncherViewMode) : LauncherShellAction

    /**
     * Makes [mode] the Home side of the Home <-> Library pair of the device class Settings is
     * editing (#1241). Library is always the other side, so [mode] is Cards or Standard.
     */
    data class SelectHomeSurfaceMode(val mode: LauncherViewMode) : LauncherShellAction

    data class SelectLauncherTemplate(
        val templateId: LauncherTemplateId,
        val mode: LauncherViewMode,
    ) : LauncherShellAction

    data class SelectHomeLayoutDeviceClass(
        val deviceClass: HomeLayoutDeviceClass,
        val availableDeviceClasses: Set<HomeLayoutDeviceClass> = setOf(deviceClass),
    ) : LauncherShellAction

    data class SelectSettingsLayoutDeviceClass(val deviceClass: HomeLayoutDeviceClass) : LauncherShellAction

    data class LaunchApp(val identity: AppIdentity) : LauncherShellAction

    data class LaunchAppShortcut(val shortcut: AppShortcut) : LauncherShellAction

    data class SearchWeb(val query: String) : LauncherShellAction

    data class OpenAppInfo(val identity: AppIdentity) : LauncherShellAction

    data class UninstallApp(val identity: AppIdentity) : LauncherShellAction

    data class HideApp(val identity: AppIdentity) : LauncherShellAction

    data class UnhideApp(val identity: AppIdentity) : LauncherShellAction

    data class AddAppToHome(val app: InstalledApp) : LauncherShellAction

    data class AddAppShortcutToHome(val shortcut: AppShortcut) : LauncherShellAction

    data class AddAppToDock(val app: InstalledApp) : LauncherShellAction

    data class AddAppToFloatingDock(val app: InstalledApp) : LauncherShellAction

    data class AddAppShortcutToFloatingDock(val shortcut: AppShortcut) : LauncherShellAction

    data class RemoveFloatingDockShortcut(val itemId: LauncherItemId) : LauncherShellAction

    data class MoveFloatingDockShortcut(
        val itemId: LauncherItemId,
        val direction: OverlayDockItemMoveDirection,
    ) : LauncherShellAction

    data class SelectDockEnabled(val enabled: Boolean) : LauncherShellAction

    data class SelectDockExpandable(val expandable: Boolean) : LauncherShellAction

    data class SelectDockExpandAffordance(val affordance: DockExpandAffordance) : LauncherShellAction

    data class SelectDockPanelEnabled(val enabled: Boolean) : LauncherShellAction

    data class RemoveDockPanelItem(val itemId: LauncherItemId) : LauncherShellAction

    data class SelectDockCapacity(val capacity: Int) : LauncherShellAction

    data class SelectDockIconSize(val sizeDp: Int) : LauncherShellAction

    data class SelectDockBackgroundAlpha(val alphaPercent: Int) : LauncherShellAction

    data class SelectDockBackgroundSizing(val sizing: DockBackgroundSizing) : LauncherShellAction

    data class SelectDockAlignment(val alignment: DockAlignment) : LauncherShellAction

    data class SelectDockItemSpacing(val spacingDp: Int) : LauncherShellAction

    data class SelectDockCornerRadius(val cornerRadiusDp: Int) : LauncherShellAction

    data class SelectDockHomeControlsSpacing(val spacingDp: Int) : LauncherShellAction

    data class SelectDockLiveAppSlots(val slots: Int) : LauncherShellAction

    data class AppDrawerQueryChanged(val query: String) : LauncherShellAction

    data object RefreshInstalledApps : LauncherShellAction

    data class AppDrawerProfileFilterSelected(
        val filter: AppDrawerProfileFilter,
    ) : LauncherShellAction

    data class RemoveHomeShortcut(val itemId: LauncherItemId) : LauncherShellAction

    data class CreateEmptyHomeFolder(
        val label: String,
    ) : LauncherShellAction

    data class CreateHomeFolder(
        val itemIds: List<LauncherItemId>,
        val label: String,
    ) : LauncherShellAction

    data class RenameHomeFolder(
        val itemId: LauncherItemId,
        val label: String,
    ) : LauncherShellAction

    data class AddAppToFolder(
        val folderId: LauncherItemId,
        val app: InstalledApp,
    ) : LauncherShellAction

    data class RemoveAppFromFolder(
        val folderId: LauncherItemId,
        val itemId: LauncherItemId,
    ) : LauncherShellAction

    data class MoveAppInFolder(
        val folderId: LauncherItemId,
        val itemId: LauncherItemId,
        val direction: FolderItemMoveDirection,
    ) : LauncherShellAction

    data class MoveAppOutOfFolder(
        val folderId: LauncherItemId,
        val itemId: LauncherItemId,
    ) : LauncherShellAction

    data class RemoveDockShortcut(val itemId: LauncherItemId) : LauncherShellAction

    data class MoveDockShortcut(
        val itemId: LauncherItemId,
        val direction: DockItemMoveDirection,
    ) : LauncherShellAction

    data class MoveDockShortcutToIndex(
        val itemId: LauncherItemId,
        val targetIndex: Int,
    ) : LauncherShellAction

    data class MoveHomeItemToDock(
        val itemId: LauncherItemId,
        val targetIndex: Int? = null,
        val pageId: LauncherPageId? = null,
    ) : LauncherShellAction

    data class MoveDockItemToHome(
        val itemId: LauncherItemId,
        val cell: GridCell? = null,
        val pageId: LauncherPageId? = null,
    ) : LauncherShellAction

    data class MoveHomeShortcutToCell(
        val itemId: LauncherItemId,
        val cell: GridCell,
    ) : LauncherShellAction

    data class MoveHomeItemToPage(
        val itemId: LauncherItemId,
        val sourcePageId: LauncherPageId,
        val targetPageId: LauncherPageId,
        val cell: GridCell,
    ) : LauncherShellAction

    data class ResizeHomeWidget(
        val itemId: LauncherItemId,
        val span: GridSpan,
    ) : LauncherShellAction

    data object OpenWidgetPicker : LauncherShellAction

    /**
     * Opens the widget picker aimed at the dock's panel.
     *
     * Its own action rather than a parameter on [OpenWidgetPicker] because the picker covers the
     * shelf: the panel cannot be dragged onto, so where a widget is going has to be settled before
     * the picker opens rather than by where it is dropped.
     */
    data object OpenWidgetPickerForDockPanel : LauncherShellAction

    data object CloseWidgetPicker : LauncherShellAction

    data class RequestAddWidget(
        val provider: WidgetProviderIdentity,
        val label: String,
        val dimensions: WidgetProviderDimensions,
        val supportsHorizontalResize: Boolean? = null,
        val supportsVerticalResize: Boolean? = null,
        val target: WidgetAddTarget = WidgetAddTarget.HOME,
        val targetPageId: LauncherPageId? = null,
        val targetCell: GridCell? = null,
        val dockIndex: Int? = null,
    ) : LauncherShellAction

    data class AddHostedWidgetToHome(
        override val hostedWidgetId: HostedWidgetId,
        override val label: String,
        val preferredSpan: GridSpan = GridSpan(),
        val resizeConstraints: WidgetResizeConstraints = WidgetResizeConstraints(),
        val targetCell: GridCell? = null,
        val targetPageId: LauncherPageId? = null,
    ) : LauncherShellAction,
        HostedWidgetAddAction

    /** Places one of Riffle's own glance widgets: no platform binding, so no host id and no configuration step. */
    data class AddGlanceWidgetToHome(
        val kind: GlanceWidgetKind,
        val label: String,
        val preferredSpan: GridSpan = kind.defaultSpan,
        val targetCell: GridCell? = null,
        val targetPageId: LauncherPageId? = null,
    ) : LauncherShellAction

    data class AddHostedWidgetToDock(
        override val hostedWidgetId: HostedWidgetId,
        override val label: String,
        val dockIndex: Int? = null,
    ) : LauncherShellAction,
        HostedWidgetAddAction

    data class AddHostedWidgetToDockPanel(
        override val hostedWidgetId: HostedWidgetId,
        override val label: String,
        val preferredSpan: GridSpan = GridSpan(),
        val resizeConstraints: WidgetResizeConstraints = WidgetResizeConstraints(),
        val targetCell: GridCell? = null,
    ) : LauncherShellAction,
        HostedWidgetAddAction

    data class SearchQueryChanged(val query: String) : LauncherShellAction

    data class SearchProfileFilterSelected(
        val filter: AppDrawerProfileFilter,
    ) : LauncherShellAction

    data class ToggleSearchContentFilter(
        val filter: AppSearchContentFilter,
    ) : LauncherShellAction

    data class ToggleSearchProfileFilter(
        val profileType: AppProfileType,
    ) : LauncherShellAction

    data object ResetSearchFilters : LauncherShellAction

    /** Sets which page of the active workspace shows when the launcher comes back (Settings > Workspaces). */
    data class SelectReturnBehavior(
        val behavior: ReturnBehavior,
    ) : LauncherShellAction

    data class SelectSearchResultPresentation(
        val presentation: SearchResultPresentation,
    ) : LauncherShellAction

    /** Settings > Privacy: hide launcher content in screenshots and recents (off by default). */
    data class SetScreenContentProtection(
        val enabled: Boolean,
    ) : LauncherShellAction

    /**
     * "Show as" on the Library (and Settings > Library): the view [deviceClass] draws, the active one when null (the
     * Library's own header always means the one on screen).
     */
    data class SelectLibraryView(
        val view: LibraryView,
        val deviceClass: HomeLayoutDeviceClass? = null,
    ) : LauncherShellAction

    data class SelectAppDrawerIconGridColumns(
        val columns: Int,
    ) : LauncherShellAction

    /**
     * Library may have been left by [trigger]; nothing changes the shown surface any more.
     */
    data class LeaveLibrary(
        val trigger: LibraryExitTrigger,
    ) : LauncherShellAction

    data class SelectWallpaperSource(val source: WallpaperSource) : LauncherShellAction

    data class SelectLauncherThemeMode(val mode: LauncherThemeMode) : LauncherShellAction

    data class SelectLauncherThemeAccent(val accent: LauncherThemeAccent) : LauncherShellAction

    data class SelectCustomAccent(val argb: Int?) : LauncherShellAction

    data class SelectLauncherThemeCornerStyle(val style: LauncherThemeCornerStyle) : LauncherShellAction

    data class SelectLauncherThemeTypography(val typography: LauncherThemeTypography) : LauncherShellAction

    data class SelectWallpaperScrollMode(val mode: WallpaperScrollMode) : LauncherShellAction

    data object ChangeWallpaper : LauncherShellAction

    data class SelectFullscreenHomeEnabled(val enabled: Boolean) : LauncherShellAction

    data class SelectHomeStatusBarHidden(val hidden: Boolean) : LauncherShellAction

    data class SelectHomeNavigationBarHidden(val hidden: Boolean) : LauncherShellAction

    data class SelectReducedTransparency(val reduced: Boolean) : LauncherShellAction

    data class SelectLiquidGlassFrostStrength(val percent: Int) : LauncherShellAction

    data class SelectLiquidGlassRefractionStrength(val percent: Int) : LauncherShellAction

    data class SelectLiquidGlassSpotlightFlow(val millis: Int) : LauncherShellAction

    data class SelectHomeSwipeGestureAction(
        val direction: HomeSwipeGestureDirection,
        val action: LauncherGestureAction,
    ) : LauncherShellAction

    data class SelectHomeGestureAction(
        val gesture: HomeGesture,
        val action: LauncherGestureAction,
        val launchTarget: LauncherGestureLaunchTarget? = null,
    ) : LauncherShellAction

    data object ResetHomeSwipeGestureActions : LauncherShellAction

    data class SelectHapticFeedbackStrength(
        val strength: HapticFeedbackStrength,
    ) : LauncherShellAction

    data class SelectReducedMotionPreference(
        val preference: ReducedMotionPreference,
    ) : LauncherShellAction

    data class SelectMotionPerformanceTargetFps(
        val targetFps: MotionPerformanceTargetFps,
    ) : LauncherShellAction

    data class SelectDockPosition(val position: DockPosition) : LauncherShellAction

    data class SelectOverlayDockEnabled(val enabled: Boolean) : LauncherShellAction

    data class SelectOverlayDockEdge(val edge: OverlayDockEdge) : LauncherShellAction

    data class SelectOverlayDockHandleThickness(val thicknessDp: Int) : LauncherShellAction

    data class SelectOverlayDockHandleHeight(val heightDp: Int) : LauncherShellAction

    data class SelectOverlayDockVerticalOffset(val offsetDp: Int) : LauncherShellAction

    data class SelectOverlayDockHandleAlpha(val alphaPercent: Int) : LauncherShellAction

    data class SelectOverlayDockExpandedIconSize(val sizeDp: Int) : LauncherShellAction

    data class SelectOverlayDockExpandedOrientation(
        val orientation: OverlayDockExpandedOrientation,
    ) : LauncherShellAction

    data class SelectOverlayDockShowLabels(val showLabels: Boolean) : LauncherShellAction

    data class DismissNotifications(val keys: List<LauncherNotificationKey>) : LauncherShellAction

    /** Adds an already-validated feed URL; the settings UI validates via [FeedUrl.parse] first. */
    data class AddRssFeed(
        val url: FeedUrl,
        val profile: AppProfile = AppProfile.personal(),
    ) : LauncherShellAction

    /** Removes a configured feed and clears its offline article cache. */
    data class RemoveRssFeed(val feedId: FeedId) : LauncherShellAction

    /** Disabling a feed also clears its offline article cache. */
    data class SetRssFeedEnabled(
        val feedId: FeedId,
        val enabled: Boolean,
    ) : LauncherShellAction

    /** Opt-in background refresh interval; [FeedRefreshIntervalOption.OFF] (the default) means no background work. */
    data class SelectRssRefreshInterval(val option: FeedRefreshIntervalOption) : LauncherShellAction

    /** Background refresh runs only on an unmetered network. */
    data class SetRssBackgroundWifiOnly(val enabled: Boolean) : LauncherShellAction

    /** Background refresh runs only while charging. */
    data class SetRssBackgroundChargingOnly(val enabled: Boolean) : LauncherShellAction

    /** Adds a durable rule that hides future notifications matching [kind]/[matchMode]/[value] for the source app. */
    data class AddNotificationHideRule(
        val packageName: AppPackageName,
        val profileId: AppProfileId,
        val kind: NotificationHideRule.Kind,
        val value: String = "",
        val matchMode: NotificationHideRule.MatchMode = NotificationHideRule.MatchMode.EXACT,
    ) : LauncherShellAction

    data class RemoveNotificationHideRule(val id: NotificationHideRuleId) : LauncherShellAction
}

enum class WidgetAddTarget {
    HOME,
    DOCK,

    /** The dock's panel -- a grid like a home page, reached through the dock rather than [HOME]. */
    DOCK_PANEL,
}

sealed interface HostedWidgetAddAction {
    val hostedWidgetId: HostedWidgetId
    val label: String
}
