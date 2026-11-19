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
import com.riffle.core.domain.launcher.home.HomeDragTuning
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
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
import com.riffle.core.domain.launcher.settings.AppsBehindCards
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.DockMotionSettings
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.LauncherFeature
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherGestureLaunchTarget
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography
import com.riffle.core.domain.launcher.settings.LayoutPosture
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockItemMoveDirection
import com.riffle.core.domain.launcher.settings.PageIndicatorPlacement
import com.riffle.core.domain.launcher.settings.PageScrollDirection
import com.riffle.core.domain.launcher.settings.PairRecipeKind
import com.riffle.core.domain.launcher.settings.PairsMode
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

    /** A drag's candidate page that was not dropped on: removed when it is still empty, else left alone. */
    data class DiscardEmptyHomePage(val pageId: LauncherPageId) : LauncherShellAction

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

    data object ToggleSelectedHomePagePinned : LauncherShellAction

    data class SelectSelectedHomePageGridDimensions(val dimensions: GridDimensions) : LauncherShellAction

    data class SelectHomeGridDimensions(val dimensions: GridDimensions) : LauncherShellAction

    data class SelectHomeGridMargin(
        val horizontalDp: Int,
        val verticalDp: Int,
    ) : LauncherShellAction

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

    /** Takes the item off Home; [pageId] names its page when it is not the selected one (a drop on Remove). */
    data class RemoveHomeShortcut(
        val itemId: LauncherItemId,
        val pageId: LauncherPageId? = null,
    ) : LauncherShellAction

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

    /** Settings > Stream > Pairs > Show pairs. */
    data class SelectPairsMode(val mode: PairsMode) : LauncherShellAction

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
        /** The localized name a folder made by this drop starts with when no shared category names it. */
        val folderLabel: String? = null,
    ) : LauncherShellAction

    /** The drop of a Home drag: the item lands at [cell] on [targetPageId] after [pushed] neighbours moved aside. */
    data class DropHomeItem(
        val itemId: LauncherItemId,
        val sourcePageId: LauncherPageId,
        val targetPageId: LauncherPageId,
        val cell: GridCell,
        val pushed: Map<LauncherItemId, GridCell> = emptyMap(),
        /**
         * Dropped on top of this item of [targetPageId] (in its dead zone) from another page: the item joins that page
         * and makes a folder with it (or joins the folder), exactly as a same-page drop on top does.
         */
        val dropOnItemId: LauncherItemId? = null,
        /** The localized name a folder made by this drop starts with when no shared category names it. */
        val folderLabel: String? = null,
        /** The page is full and the push cannot make room: the displaced item goes to the next page (HomeSpill). */
        val spill: Boolean = false,
    ) : LauncherShellAction

    /**
     * The drop of a Home drag that began outside the pages: [source] (an app of the Library, which is added as a new
     * item, or an item of the Dock, which moves) lands at [cell] on [targetPageId] after [pushed] neighbours moved
     * aside, spilling to the next page when the page is full, or (with [dropOnItemId]) joins that item in a folder.
     */
    data class DropExternalHomeItem(
        val source: ExternalHomeDragSource,
        val targetPageId: LauncherPageId,
        val cell: GridCell,
        val pushed: Map<LauncherItemId, GridCell> = emptyMap(),
        val dropOnItemId: LauncherItemId? = null,
        val folderLabel: String? = null,
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

    /**
     * Aims the widget picker at the stack of [itemId] (a lone widget becomes one): it lists the widgets that can take
     * the stack's span and the pick joins the stack instead of taking a cell (ADR 0003, Amendment 14).
     */
    data class OpenWidgetPickerForStack(val itemId: LauncherItemId) : LauncherShellAction

    /** Opens, and closes, the sheet that edits the widgets of the stack [itemId]. */
    data class OpenWidgetStackEditor(val itemId: LauncherItemId) : LauncherShellAction

    data object CloseWidgetStackEditor : LauncherShellAction

    /** Asks before removing the stack [itemId] (it holds more than one widget); the answer is [RemoveHomeShortcut]. */
    data class ConfirmRemoveWidgetStack(val itemId: LauncherItemId) : LauncherShellAction

    data object DismissRemoveWidgetStack : LauncherShellAction

    /** Remembers which widget of the stack [itemId] shows. */
    data class ShowStackedWidget(val itemId: LauncherItemId, val index: Int) : LauncherShellAction

    data class MoveStackedWidget(val itemId: LauncherItemId, val from: Int, val to: Int) : LauncherShellAction

    /** Removes one widget from a stack, as removing a widget does (its host id is deleted). */
    data class RemoveStackedWidget(val itemId: LauncherItemId, val index: Int) : LauncherShellAction

    /** "Move out to Home": the widget becomes an item of its own in the nearest free cells. */
    data class MoveStackedWidgetOut(val itemId: LauncherItemId, val index: Int) : LauncherShellAction

    /** The pick of [OpenWidgetPickerForStack], bound and configured: it joins the stack [itemId]. */
    data class AddHostedWidgetToStack(
        val itemId: LauncherItemId,
        override val hostedWidgetId: HostedWidgetId,
        override val label: String,
        val resizeConstraints: WidgetResizeConstraints = WidgetResizeConstraints(),
    ) : LauncherShellAction,
        HostedWidgetAddAction

    /** One of Riffle's own widgets joins the stack [itemId]. */
    data class AddGlanceWidgetToStack(
        val itemId: LauncherItemId,
        val kind: GlanceWidgetKind,
        val label: String,
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
        /** With [WidgetAddTarget.HOME_STACK]: the stack the widget joins. */
        val targetStackId: LauncherItemId? = null,
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

    /** Settings > Stream (a later slice): how much of its app's colour a card's fill takes, in percent. */
    data class SelectStreamCardTint(
        val percent: Int,
    ) : LauncherShellAction

    /** Whether a card shows its app's colour as a glow along its top edge. */
    data class SetStreamCardTopGlow(
        val enabled: Boolean,
    ) : LauncherShellAction

    /** Where an open Stream card sits. */
    data class SelectOpenCardPlacement(
        val placement: OpenCardPlacement,
    ) : LauncherShellAction

    /** Where an open Stream card's actions sit. */
    data class SelectCardActionsPlacement(
        val placement: CardActionsPlacement,
    ) : LauncherShellAction

    /** Settings > Motion: the Dock's and the Stream's adjustable motion (held to its ranges when saved). */
    data class SetDockMotion(
        val settings: DockMotionSettings,
    ) : LauncherShellAction

    /** Settings > About > Developer options > Drag tuning: the numbers of a Home drag (held to their ranges). */
    data class SetDragTuning(
        val tuning: HomeDragTuning,
    ) : LauncherShellAction

    /** Settings > About: the Version row was tapped seven times, so the "Developer options" switch shows. */
    data object RevealDeveloperOptions : LauncherShellAction

    /** Settings > About > "Developer options" switch: offers (or hides) the Developer options page. */
    data class SetDeveloperOptions(
        val enabled: Boolean,
    ) : LauncherShellAction

    /** Settings > About > Developer options > Pair recipe: how the Pairs stack starts a pair. */
    data class SetPairRecipe(
        val recipe: PairRecipeKind,
    ) : LauncherShellAction

    /** Settings > Home > "Add new apps to Home": a newly installed app is placed on Home, or only in the Library. */
    data class SetAddNewApps(
        val enabled: Boolean,
    ) : LauncherShellAction

    /**
     * Settings > Home > "Add all apps to Home" (also the empty-Home hint's button): every visible app that is not on
     * Home yet, after the last occupied cell in A to Z order. Acts on the layout Settings is showing.
     */
    data object AddAllAppsToHome : LauncherShellAction

    /** Settings > Home > "Arrange Home A to Z" (confirmed first): re-lays Home's apps and folders out A to Z. */
    data object ArrangeHomeAtoZ : LauncherShellAction

    /** Settings > Home > "Compact: fill each page completely"; off leaves room on each page. */
    data class SetCompactArrange(
        val enabled: Boolean,
    ) : LauncherShellAction

    /**
     * Undo of [AddAllAppsToHome] or [ArrangeHomeAtoZ]: puts [before] back as the layout Settings is showing, exactly as
     * it was.
     */
    data class UndoHomeBulkEdit(
        val before: HomeLayout,
    ) : LauncherShellAction

    /** The empty-Home hint was dismissed (or is no longer needed): it is never shown again. */
    data object DismissEmptyHomeHint : LauncherShellAction

    /** Settings: one feature switch (Stream, Dock live apps, Library) on or off. */
    data class SetFeatureEnabled(
        val feature: LauncherFeature,
        val enabled: Boolean,
    ) : LauncherShellAction

    /** Settings: Simple mode, the preset that leaves only the app library as Home and the Dock. */
    data class SetSimpleMode(
        val enabled: Boolean,
    ) : LauncherShellAction

    /** Settings > Home > Show hints again: the stored press-hint flag is cleared (the other hints are onboarding's). */
    data object ShowHintsAgain : LauncherShellAction

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

    /** Settings > Layout: the Dock edge of one posture (the same edge the Dock page sets for its device class). */
    data class SelectPostureDockEdge(
        val posture: LayoutPosture,
        val position: DockPosition,
    ) : LauncherShellAction

    /** Settings > Layout: which way a posture's app pages scroll. */
    data class SelectPageScroll(
        val posture: LayoutPosture,
        val direction: PageScrollDirection,
    ) : LauncherShellAction

    /** Settings > Layout: where a posture's page indicator sits (or that it is off). */
    data class SelectPageIndicator(
        val posture: LayoutPosture,
        val placement: PageIndicatorPlacement,
    ) : LauncherShellAction

    /** Settings > Layout: whether dragging along a posture's dots scrolls quickly, or they only show where you are. */
    data class SetQuickScroll(
        val posture: LayoutPosture,
        val enabled: Boolean,
    ) : LauncherShellAction

    /** Settings > Layout (and Stream > Cards): what Home does behind the Stream's cards, per posture. */
    data class SetAppsBehindCards(
        val posture: LayoutPosture,
        val choice: AppsBehindCards,
    ) : LauncherShellAction

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

    /** A widget stack of Home: the widget joins it instead of taking a cell. */
    HOME_STACK,
}

sealed interface HostedWidgetAddAction {
    val hostedWidgetId: HostedWidgetId
    val label: String
}
