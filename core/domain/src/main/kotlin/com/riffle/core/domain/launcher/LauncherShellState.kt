package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockEditRejectionReason
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedStageCacheProjection
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus

data class LauncherShellState(
    val firstRunStatus: FirstRunStatus = FirstRunStatus.NEEDS_HOME_ROLE,
    /** A Home-role request was pending when this shell was recreated and needs live reconciliation. */
    val hasRecoveredHomeRoleRequest: Boolean = false,
    /** Presentation-only state; the live [homeRoleStatus] remains authoritative. */
    val setupCardDismissed: Boolean = false,
    val homeRoleStatus: HomeRoleStatus = HomeRoleStatus.UNKNOWN,
    val overlayDockPermissionStatus: OverlayDockPermissionStatus = OverlayDockPermissionStatus.UNKNOWN,
    val destination: ShellDestination = ShellDestination.HOME,
    /** Whether the Library, when it shows, opened over the Stream rather than Home; closing it returns there. */
    val libraryOverStream: Boolean = false,
    val homeLayout: HomeLayout = HomeLayoutDefaults.standard(),
    val homeLayoutSet: HomeLayoutSet = HomeLayoutSet.fromLayout(homeLayout),
    val settingsLayoutDeviceClass: HomeLayoutDeviceClass = homeLayoutSet.activeKey.deviceClass,
    val availableLayoutDeviceClasses: Set<HomeLayoutDeviceClass> = setOf(homeLayoutSet.activeKey.deviceClass),
    val launcherSettings: LauncherSettings = LauncherSettings(),
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    /** Calendar access is explicit and user-initiated; this only reflects it, it never requests it. */
    val calendarAccessStatus: CalendarAccessStatus = CalendarAccessStatus.UNKNOWN,
    val notificationCountsByCategory: Map<NotificationCategory, Int> = emptyMap(),
    val notificationGroupsByApp: List<AppNotificationGroup> = emptyList(),
    val profileContentVisibility: Map<AppProfileId, AppProfileContentVisibility> = emptyMap(),
    /** Configured feeds, sourced from a ConfiguredFeedSource (a placeholder seam pending #1013). */
    val configuredFeeds: List<FeedConfiguration> = emptyList(),
    /** Latest offline-cache projection per feed, adapted from the app-module feed article cache. */
    val feedCacheProjections: Map<FeedId, FeedStageCacheProjection> = emptyMap(),
    val installedApps: List<InstalledApp> = emptyList(),
    val hiddenApps: List<InstalledApp> = emptyList(),
    val appShortcutsByApp: AppShortcutsByApp = emptyMap(),
    val appDrawerQuery: String = "",
    val appDrawerProfileFilter: AppDrawerProfileFilter = AppDrawerProfileFilter.ALL,
    val appDrawerApps: List<InstalledApp> = emptyList(),
    val searchQuery: String = "",
    val searchProfileFilter: AppDrawerProfileFilter = AppDrawerProfileFilter.ALL,
    val searchFilters: AppSearchFilters = AppSearchFilters(),
    val searchResults: List<InstalledApp> = emptyList(),
    val searchShortcutResults: List<AppShortcut> = emptyList(),
    val searchSettingsResults: List<LauncherSearchResult.Setting> = emptyList(),
    val installedWidgetProviders: List<InstalledWidgetProvider> = emptyList(),
    val widgetProviderCatalogStatus: WidgetProviderCatalogStatus = WidgetProviderCatalogStatus.READY,
    val isWidgetPickerOpen: Boolean = false,
    /** Whether the open picker is placing onto the dock's panel rather than onto home or the dock. */
    val isWidgetPickerTargetingDockPanel: Boolean = false,
    /** The latest rejected Dock edit, retained until another Dock edit succeeds. */
    val dockEditRejectionReason: DockEditRejectionReason? = null,
) {
    /** Profile content policy used by Cards surfaces; profiles without an app-state decision are redacted. */
    fun cardsProfileContentVisibility(): Map<AppProfileId, AppProfileContentVisibility> = profileContentVisibility

    /**
     * Retained for callers migrating from the blocking first-run prompt. Preview-first setup
     * never blocks the launcher shell.
     */
    val shouldShowDefaultHomePrompt: Boolean = false

    val shouldShowEmptyHome: Boolean = true

    val shouldShowSetupCard: Boolean =
        !setupCardDismissed && homeRoleStatus != HomeRoleStatus.DEFAULT_HOME
}

enum class WidgetProviderCatalogStatus {
    LOADING,
    READY,
    FAILED,
}

enum class FirstRunStatus {
    NEEDS_HOME_ROLE,
    REQUESTING_HOME_ROLE,
    COMPLETE,
}
