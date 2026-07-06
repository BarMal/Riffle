package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
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
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockItemMoveDirection
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

sealed interface LauncherShellAction {
    data object RequestDefaultHome : LauncherShellAction

    data object OpenHome : LauncherShellAction

    data object OpenDefaultHome : LauncherShellAction

    data object OpenAppDrawer : LauncherShellAction

    data object OpenSearch : LauncherShellAction

    data object OpenNotifications : LauncherShellAction

    data object OpenSettings : LauncherShellAction

    data object RequestNotificationAccess : LauncherShellAction

    data object RequestOverlayDockPermission : LauncherShellAction

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

    data class SelectSelectedHomePageGridDimensions(val dimensions: GridDimensions) : LauncherShellAction

    data class SelectHomeGridDimensions(val dimensions: GridDimensions) : LauncherShellAction

    data class SelectLibraryPageCompaction(val enabled: Boolean) : LauncherShellAction

    data class SelectHomeLabelBackgroundAlpha(val alphaPercent: Int) : LauncherShellAction

    data class SelectHomeLabelTextSize(val textSizeSp: Int) : LauncherShellAction

    data class SelectHomeLabelTextVisible(val visible: Boolean) : LauncherShellAction

    data class SelectHomeLabelMaxWidth(val maxWidthDp: Int) : LauncherShellAction

    data class SelectHomeLabelMaxLines(val maxLines: Int) : LauncherShellAction

    data class SelectHomeLabelSizing(val sizing: HomeLabelSizing) : LauncherShellAction

    data class SelectLauncherViewMode(val mode: LauncherViewMode) : LauncherShellAction

    data class SelectHomeLayoutDeviceClass(
        val deviceClass: HomeLayoutDeviceClass,
        val availableDeviceClasses: Set<HomeLayoutDeviceClass> = setOf(deviceClass),
    ) : LauncherShellAction

    data class SelectSettingsLayoutDeviceClass(val deviceClass: HomeLayoutDeviceClass) : LauncherShellAction

    data class LaunchApp(val identity: AppIdentity) : LauncherShellAction

    data class LaunchAppShortcut(val shortcut: AppShortcut) : LauncherShellAction

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

    data class SelectDockCapacity(val capacity: Int) : LauncherShellAction

    data class SelectDockIconSize(val sizeDp: Int) : LauncherShellAction

    data class SelectDockBackgroundAlpha(val alphaPercent: Int) : LauncherShellAction

    data class SelectDockBackgroundSizing(val sizing: DockBackgroundSizing) : LauncherShellAction

    data class SelectDockItemSpacing(val spacingDp: Int) : LauncherShellAction

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

    data class MoveHomeShortcutToCell(
        val itemId: LauncherItemId,
        val cell: GridCell,
    ) : LauncherShellAction

    data class ResizeHomeWidget(
        val itemId: LauncherItemId,
        val span: GridSpan,
    ) : LauncherShellAction

    data object OpenWidgetPicker : LauncherShellAction

    data object CloseWidgetPicker : LauncherShellAction

    data class RequestAddWidget(
        val provider: WidgetProviderIdentity,
        val label: String,
        val dimensions: WidgetProviderDimensions,
    ) : LauncherShellAction

    data class AddHostedWidgetToHome(
        val hostedWidgetId: HostedWidgetId,
        val label: String,
        val preferredSpan: GridSpan = GridSpan(),
        val targetCell: GridCell? = null,
    ) : LauncherShellAction

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

    data class SelectWallpaperSource(val source: WallpaperSource) : LauncherShellAction

    data class SelectFullscreenHomeEnabled(val enabled: Boolean) : LauncherShellAction

    data class SelectHomeStatusBarHidden(val hidden: Boolean) : LauncherShellAction

    data class SelectHomeNavigationBarHidden(val hidden: Boolean) : LauncherShellAction

    data class SelectHomeSwipeGestureAction(
        val direction: HomeSwipeGestureDirection,
        val action: LauncherGestureAction,
    ) : LauncherShellAction

    data class SelectHomeGestureAction(
        val gesture: HomeGesture,
        val action: LauncherGestureAction,
    ) : LauncherShellAction

    data object ResetHomeSwipeGestureActions : LauncherShellAction

    data class SelectHapticFeedbackStrength(
        val strength: HapticFeedbackStrength,
    ) : LauncherShellAction

    data class SelectReducedMotionEnabled(val enabled: Boolean) : LauncherShellAction

    data class SelectContextualEnabled(val enabled: Boolean) : LauncherShellAction

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
}
