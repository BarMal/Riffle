package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.FolderItemMoveDirection
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureDirection
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockItemMoveDirection
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherActionDomainTest {
    @Test
    fun routeOwnershipTableDocumentsEveryCurrentActionRepresentative() {
        assertEquals(
            "Update routeOwnershipTable when LauncherShellAction changes.",
            currentLauncherShellActionNames(),
            routeOwnershipTable.map { it.name }.toSet(),
        )
    }

    @Test
    fun routeOwnershipTableMapsRepresentativesToExpectedDomains() {
        routeOwnershipTable.forEach { row ->
            assertEquals(row.name, row.expectedDomain, row.action.launcherActionDomain())
        }
    }

    @Test
    fun routeOwnershipTableRepresentativesMatchExactlyOneRouteHelperOrAreUnhandled() {
        val failures =
            routeOwnershipTable.mapNotNull { row ->
                val matchedHelpers = row.action.matchedRouteHelpers()
                val expectedHelpers =
                    row.expectedDomain
                        ?.let { domain -> listOf(RouteHelper.forDomain(domain)) }
                        ?: emptyList()

                when (matchedHelpers) {
                    expectedHelpers -> null
                    else -> "${row.name}: expected $expectedHelpers but matched $matchedHelpers"
                }
            }

        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun documentsNoUnhandledCurrentActionRepresentatives() {
        assertEquals(
            emptyList<String>(),
            routeOwnershipTable
                .filter { it.expectedDomain == null }
                .map { it.name },
        )
    }

    private data class ActionRouteOwnership(
        val name: String,
        val action: LauncherShellAction,
        val expectedDomain: LauncherActionDomain?,
    )

    private enum class RouteHelper(
        val domain: LauncherActionDomain,
    ) {
        ACTIVITY(LauncherActionDomain.ACTIVITY),
        NOTIFICATION(LauncherActionDomain.NOTIFICATION),
        SETTINGS(LauncherActionDomain.SETTINGS),
        APP(LauncherActionDomain.APP),
        ;

        companion object {
            fun forDomain(domain: LauncherActionDomain): RouteHelper {
                return entries.single { routeHelper -> routeHelper.domain == domain }
            }
        }
    }

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.app"),
                activityName = AppActivityName(".MainActivity"),
            )
        val installedApp = InstalledApp(identity = appIdentity, label = "Example")
        val appShortcut =
            AppShortcut(
                id = AppShortcutId("shortcut"),
                appIdentity = appIdentity,
                shortLabel = "Shortcut",
            )
        val itemId = LauncherItemId("item")
        val pageId = LauncherPageId("page")
        val backupDocument =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.standard(),
                launcherSettings = LauncherSettings(),
            )
        val widgetProvider =
            WidgetProviderIdentity(
                packageName = AppPackageName("com.example.widgets"),
                className = WidgetProviderClassName(".Weather"),
            )

        val routeOwnershipTable =
            listOf(
                activity("RequestDefaultHome", LauncherShellAction.RequestDefaultHome),
                activity("OpenHome", LauncherShellAction.OpenHome),
                activity("OpenDefaultHome", LauncherShellAction.OpenDefaultHome),
                activity("OpenAppDrawer", LauncherShellAction.OpenAppDrawer),
                activity("OpenSearch", LauncherShellAction.OpenSearch),
                activity("OpenNotifications", LauncherShellAction.OpenNotifications),
                activity("OpenSettings", LauncherShellAction.OpenSettings),
                activity("OpenSettingsPage", LauncherShellAction.OpenSettingsPage(SettingsPage.APPEARANCE)),
                settings("RequestNotificationAccess", LauncherShellAction.RequestNotificationAccess),
                settings("RequestOverlayDockPermission", LauncherShellAction.RequestOverlayDockPermission),
                settings("ChangeWallpaper", LauncherShellAction.ChangeWallpaper),
                settings("ExportLauncherBackup", LauncherShellAction.ExportLauncherBackup),
                settings("RequestImportLauncherBackup", LauncherShellAction.RequestImportLauncherBackup),
                settings("ImportLauncherBackup", LauncherShellAction.ImportLauncherBackup(backupDocument)),
                activity("EnterHomeEditMode", LauncherShellAction.EnterHomeEditMode),
                activity("ExitHomeEditMode", LauncherShellAction.ExitHomeEditMode),
                activity("EnterHomePageOverview", LauncherShellAction.EnterHomePageOverview),
                activity("AddHomePage", LauncherShellAction.AddHomePage),
                activity("DuplicateSelectedHomePage", LauncherShellAction.DuplicateSelectedHomePage),
                activity("SelectHomePage", LauncherShellAction.SelectHomePage(pageId)),
                activity("SelectPreviousHomePage", LauncherShellAction.SelectPreviousHomePage),
                activity("SelectNextHomePage", LauncherShellAction.SelectNextHomePage),
                activity("MoveSelectedHomePageLeft", LauncherShellAction.MoveSelectedHomePageLeft),
                activity("MoveSelectedHomePageRight", LauncherShellAction.MoveSelectedHomePageRight),
                activity("MoveHomePage", LauncherShellAction.MoveHomePage(pageId, targetIndex = 1)),
                activity("DeleteSelectedHomePage", LauncherShellAction.DeleteSelectedHomePage),
                activity(
                    "SelectSelectedHomePageType",
                    LauncherShellAction.SelectSelectedHomePageType(LauncherPageType.AllApps),
                ),
                activity(
                    "SelectSelectedHomePageGridDimensions",
                    LauncherShellAction.SelectSelectedHomePageGridDimensions(GridDimensions(4, 6)),
                ),
                activity(
                    "SelectHomeGridDimensions",
                    LauncherShellAction.SelectHomeGridDimensions(GridDimensions(4, 6)),
                ),
                activity(
                    "SelectLibraryPageCompaction",
                    LauncherShellAction.SelectLibraryPageCompaction(enabled = true),
                ),
                activity(
                    "SelectHomeLabelBackgroundAlpha",
                    LauncherShellAction.SelectHomeLabelBackgroundAlpha(alphaPercent = 48),
                ),
                activity("SelectHomeLabelTextSize", LauncherShellAction.SelectHomeLabelTextSize(textSizeSp = 12)),
                activity("SelectHomeLabelTextVisible", LauncherShellAction.SelectHomeLabelTextVisible(visible = true)),
                activity("SelectHomeLabelMaxWidth", LauncherShellAction.SelectHomeLabelMaxWidth(maxWidthDp = 80)),
                activity("SelectHomeLabelMaxLines", LauncherShellAction.SelectHomeLabelMaxLines(maxLines = 2)),
                activity("SelectHomeLabelSizing", LauncherShellAction.SelectHomeLabelSizing(HomeLabelSizing.DYNAMIC)),
                activity(
                    "SelectLauncherViewMode",
                    LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
                ),
                activity(
                    "SelectHomeLayoutDeviceClass",
                    LauncherShellAction.SelectHomeLayoutDeviceClass(HomeLayoutDeviceClass.TABLET),
                ),
                settings(
                    "SelectSettingsLayoutDeviceClass",
                    LauncherShellAction.SelectSettingsLayoutDeviceClass(HomeLayoutDeviceClass.FOLDABLE),
                ),
                app("LaunchApp", LauncherShellAction.LaunchApp(appIdentity)),
                app("LaunchAppShortcut", LauncherShellAction.LaunchAppShortcut(appShortcut)),
                app("SearchWeb", LauncherShellAction.SearchWeb("weather")),
                app("OpenAppInfo", LauncherShellAction.OpenAppInfo(appIdentity)),
                app("UninstallApp", LauncherShellAction.UninstallApp(appIdentity)),
                app("HideApp", LauncherShellAction.HideApp(appIdentity)),
                app("UnhideApp", LauncherShellAction.UnhideApp(appIdentity)),
                app("AddAppToHome", LauncherShellAction.AddAppToHome(installedApp)),
                activity("AddAppShortcutToHome", LauncherShellAction.AddAppShortcutToHome(appShortcut)),
                activity("AddAppToDock", LauncherShellAction.AddAppToDock(installedApp)),
                settings("AddAppToFloatingDock", LauncherShellAction.AddAppToFloatingDock(installedApp)),
                settings("AddAppShortcutToFloatingDock", LauncherShellAction.AddAppShortcutToFloatingDock(appShortcut)),
                settings("RemoveFloatingDockShortcut", LauncherShellAction.RemoveFloatingDockShortcut(itemId)),
                settings(
                    "MoveFloatingDockShortcut",
                    LauncherShellAction.MoveFloatingDockShortcut(itemId, OverlayDockItemMoveDirection.DOWN),
                ),
                activity("SelectDockEnabled", LauncherShellAction.SelectDockEnabled(enabled = true)),
                activity(
                    "SelectDockNotificationCardsEnabled",
                    LauncherShellAction.SelectDockNotificationCardsEnabled(enabled = false),
                ),
                activity("SelectDockCapacity", LauncherShellAction.SelectDockCapacity(capacity = 6)),
                activity("SelectDockIconSize", LauncherShellAction.SelectDockIconSize(sizeDp = 52)),
                activity("SelectDockBackgroundAlpha", LauncherShellAction.SelectDockBackgroundAlpha(alphaPercent = 80)),
                activity(
                    "SelectDockBackgroundSizing",
                    LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.FIXED),
                ),
                activity("SelectDockItemSpacing", LauncherShellAction.SelectDockItemSpacing(spacingDp = 12)),
                app("AppDrawerQueryChanged", LauncherShellAction.AppDrawerQueryChanged("query")),
                app("RefreshInstalledApps", LauncherShellAction.RefreshInstalledApps),
                app(
                    "AppDrawerProfileFilterSelected",
                    LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.WORK),
                ),
                activity("RemoveHomeShortcut", LauncherShellAction.RemoveHomeShortcut(itemId)),
                activity("CreateEmptyHomeFolder", LauncherShellAction.CreateEmptyHomeFolder(label = "Folder")),
                activity("CreateHomeFolder", LauncherShellAction.CreateHomeFolder(listOf(itemId), label = "Folder")),
                activity("RenameHomeFolder", LauncherShellAction.RenameHomeFolder(itemId, label = "Renamed")),
                activity("AddAppToFolder", LauncherShellAction.AddAppToFolder(itemId, installedApp)),
                activity("RemoveAppFromFolder", LauncherShellAction.RemoveAppFromFolder(itemId, itemId)),
                activity(
                    "MoveAppInFolder",
                    LauncherShellAction.MoveAppInFolder(itemId, itemId, FolderItemMoveDirection.DOWN),
                ),
                activity("MoveAppOutOfFolder", LauncherShellAction.MoveAppOutOfFolder(itemId, itemId)),
                activity("RemoveDockShortcut", LauncherShellAction.RemoveDockShortcut(itemId)),
                activity("MoveDockShortcut", LauncherShellAction.MoveDockShortcut(itemId, DockItemMoveDirection.RIGHT)),
                activity("MoveHomeShortcutToCell", LauncherShellAction.MoveHomeShortcutToCell(itemId, GridCell(1, 2))),
                activity("ResizeHomeWidget", LauncherShellAction.ResizeHomeWidget(itemId, GridSpan(2, 2))),
                app("OpenWidgetPicker", LauncherShellAction.OpenWidgetPicker),
                app("CloseWidgetPicker", LauncherShellAction.CloseWidgetPicker),
                app(
                    "RequestAddWidget",
                    LauncherShellAction.RequestAddWidget(
                        provider = widgetProvider,
                        label = "Weather",
                        dimensions = WidgetProviderDimensions(minWidthDp = 100, minHeightDp = 50),
                    ),
                ),
                activity(
                    "AddHostedWidgetToHome",
                    LauncherShellAction.AddHostedWidgetToHome(HostedWidgetId(42), label = "Weather"),
                ),
                activity(
                    "AddHostedWidgetToDock",
                    LauncherShellAction.AddHostedWidgetToDock(HostedWidgetId(42), label = "Weather"),
                ),
                app("SearchQueryChanged", LauncherShellAction.SearchQueryChanged("query")),
                app(
                    "SearchProfileFilterSelected",
                    LauncherShellAction.SearchProfileFilterSelected(AppDrawerProfileFilter.PERSONAL),
                ),
                app(
                    "ToggleSearchContentFilter",
                    LauncherShellAction.ToggleSearchContentFilter(AppSearchContentFilter.SHORTCUTS),
                ),
                app(
                    "ToggleSearchProfileFilter",
                    LauncherShellAction.ToggleSearchProfileFilter(AppProfileType.WORK),
                ),
                app("ResetSearchFilters", LauncherShellAction.ResetSearchFilters),
                settings(
                    "SelectWallpaperSource",
                    LauncherShellAction.SelectWallpaperSource(WallpaperSource.SOLID_COLOR),
                ),
                settings(
                    "SelectWallpaperScrollMode",
                    LauncherShellAction.SelectWallpaperScrollMode(WallpaperScrollMode.SCROLLING),
                ),
                settings(
                    "SelectFullscreenHomeEnabled",
                    LauncherShellAction.SelectFullscreenHomeEnabled(enabled = true),
                ),
                settings("SelectHomeStatusBarHidden", LauncherShellAction.SelectHomeStatusBarHidden(hidden = true)),
                settings(
                    "SelectHomeNavigationBarHidden",
                    LauncherShellAction.SelectHomeNavigationBarHidden(hidden = true),
                ),
                settings(
                    "SelectHomeSwipeGestureAction",
                    LauncherShellAction.SelectHomeSwipeGestureAction(
                        direction = HomeSwipeGestureDirection.UP,
                        action = LauncherGestureAction.OPEN_SEARCH,
                    ),
                ),
                settings(
                    "SelectHomeGestureAction",
                    LauncherShellAction.SelectHomeGestureAction(
                        gesture = HomeGesture.TWO_FINGER_UP,
                        action = LauncherGestureAction.OPEN_SEARCH,
                    ),
                ),
                settings("ResetHomeSwipeGestureActions", LauncherShellAction.ResetHomeSwipeGestureActions),
                settings(
                    "SelectHapticFeedbackStrength",
                    LauncherShellAction.SelectHapticFeedbackStrength(HapticFeedbackStrength.LIGHT),
                ),
                settings("SelectReducedMotionEnabled", LauncherShellAction.SelectReducedMotionEnabled(enabled = true)),
                settings("SelectContextualEnabled", LauncherShellAction.SelectContextualEnabled(enabled = true)),
                settings("SelectOverlayDockEnabled", LauncherShellAction.SelectOverlayDockEnabled(enabled = true)),
                settings("SelectOverlayDockEdge", LauncherShellAction.SelectOverlayDockEdge(OverlayDockEdge.START)),
                settings(
                    "SelectOverlayDockHandleThickness",
                    LauncherShellAction.SelectOverlayDockHandleThickness(thicknessDp = 24),
                ),
                settings(
                    "SelectOverlayDockHandleHeight",
                    LauncherShellAction.SelectOverlayDockHandleHeight(heightDp = 96),
                ),
                settings(
                    "SelectOverlayDockVerticalOffset",
                    LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp = -48),
                ),
                settings(
                    "SelectOverlayDockHandleAlpha",
                    LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent = 65),
                ),
                settings(
                    "SelectOverlayDockExpandedIconSize",
                    LauncherShellAction.SelectOverlayDockExpandedIconSize(sizeDp = 64),
                ),
                settings(
                    "SelectOverlayDockExpandedOrientation",
                    LauncherShellAction.SelectOverlayDockExpandedOrientation(OverlayDockExpandedOrientation.TALL),
                ),
                settings(
                    "SelectOverlayDockShowLabels",
                    LauncherShellAction.SelectOverlayDockShowLabels(showLabels = true),
                ),
                notification(
                    "DismissNotifications",
                    LauncherShellAction.DismissNotifications(listOf(LauncherNotificationKey("notification"))),
                ),
            )

        private fun activity(
            name: String,
            action: LauncherShellAction,
        ): ActionRouteOwnership {
            return ActionRouteOwnership(name, action, LauncherActionDomain.ACTIVITY)
        }

        private fun app(
            name: String,
            action: LauncherShellAction,
        ): ActionRouteOwnership {
            return ActionRouteOwnership(name, action, LauncherActionDomain.APP)
        }

        private fun notification(
            name: String,
            action: LauncherShellAction,
        ): ActionRouteOwnership {
            return ActionRouteOwnership(name, action, LauncherActionDomain.NOTIFICATION)
        }

        private fun settings(
            name: String,
            action: LauncherShellAction,
        ): ActionRouteOwnership {
            return ActionRouteOwnership(name, action, LauncherActionDomain.SETTINGS)
        }
    }

    private fun currentLauncherShellActionNames(): Set<String> =
        checkNotNull(LauncherShellAction::class.java.permittedSubclasses) {
            "LauncherShellAction sealed subclasses are required for ownership table coverage."
        }
            .map { subclass -> subclass.simpleName }
            .toSet()

    private fun LauncherShellAction.matchedRouteHelpers(): List<RouteHelper> =
        buildList {
            if (launcherActivityRoute() != null) add(RouteHelper.ACTIVITY)
            if (launcherNotificationActionRoute() != null) add(RouteHelper.NOTIFICATION)
            if (launcherSettingsActionRoute() != null) add(RouteHelper.SETTINGS)
            if (launcherAppActionRoute() != null) add(RouteHelper.APP)
        }
}
