package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.CardsChapterPlanner
import com.riffle.core.domain.launcher.cards.CardsChapterState
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStageIdentitySnapshot
import com.riffle.core.domain.launcher.cards.AppStagePlanner
import com.riffle.core.domain.launcher.cards.AppStageProfileState
import com.riffle.core.domain.launcher.cards.AppStageSnapshot
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.stagePreferencesFor
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

data class LauncherShellState(
    val firstRunStatus: FirstRunStatus = FirstRunStatus.NEEDS_HOME_ROLE,
    /** Presentation-only state; the live [homeRoleStatus] remains authoritative. */
    val setupCardDismissed: Boolean = false,
    val homeRoleStatus: HomeRoleStatus = HomeRoleStatus.UNKNOWN,
    val overlayDockPermissionStatus: OverlayDockPermissionStatus = OverlayDockPermissionStatus.UNKNOWN,
    val destination: ShellDestination = ShellDestination.HOME,
    val homeLayout: HomeLayout = HomeLayoutDefaults.standard(),
    val homeLayoutSet: HomeLayoutSet = HomeLayoutSet.fromLayout(homeLayout),
    val settingsLayoutDeviceClass: HomeLayoutDeviceClass = homeLayoutSet.activeKey.deviceClass,
    val availableLayoutDeviceClasses: Set<HomeLayoutDeviceClass> = setOf(homeLayoutSet.activeKey.deviceClass),
    val launcherSettings: LauncherSettings = LauncherSettings(),
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    val notificationCountsByCategory: Map<NotificationCategory, Int> = emptyMap(),
    val notificationGroupsByApp: List<AppNotificationGroup> = emptyList(),
    val profileContentVisibility: Map<AppProfileId, AppProfileContentVisibility> = emptyMap(),
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
    val isWidgetPickerOpen: Boolean = false,
) {
    /** Rebuilds transient Cards content from the current notification snapshot and stored intent. */
    fun cardsChapterState(planner: CardsChapterPlanner = CardsChapterPlanner()): CardsChapterState =
        planner.state(
            notificationGroups = notificationGroupsByApp,
            preferences = launcherSettings.cards.chapterPreferences,
        )

    /** Profile content policy used by Cards surfaces; profiles without an app-state decision are redacted. */
    fun cardsProfileContentVisibility(): Map<AppProfileId, AppProfileContentVisibility> = profileContentVisibility

    fun withReconciledCardsChapterSelection(): LauncherShellState {
        val preferences = cardsChapterState().preferences
        return if (preferences == launcherSettings.cards.chapterPreferences) {
            this
        } else {
            copy(
                launcherSettings =
                    launcherSettings.copy(
                        cards = launcherSettings.cards.copy(chapterPreferences = preferences),
                    ),
            )
        }
    }

    /** Reconciles optional TimeScape stages from the same installed-app/profile/settings snapshot. */
    fun appStageSnapshot(planner: AppStagePlanner = AppStagePlanner()): AppStageSnapshot =
        planner.reconcile(
            identitySnapshot =
                AppStageIdentitySnapshot(
                    installedStageIds = installedApps.map(InstalledApp::toAppStageId).distinct(),
                    profileStates = profileStatesForStages(),
                ),
            preferences = launcherSettings.cards.stagePreferencesFor(homeLayoutSet.activeKey),
        )

    /**
     * Retained for callers migrating from the blocking first-run prompt. Preview-first setup
     * never blocks the launcher shell.
     */
    val shouldShowDefaultHomePrompt: Boolean = false

    val shouldShowEmptyHome: Boolean = true

    val shouldShowSetupCard: Boolean =
        !setupCardDismissed && homeRoleStatus != HomeRoleStatus.DEFAULT_HOME
}

private fun InstalledApp.toAppStageId(): AppStageId =
    AppStageId(packageName = identity.packageName, profileId = identity.profile.id)

private fun LauncherShellState.profileStatesForStages(): Map<AppProfileId, AppStageProfileState> =
    profileContentVisibility.mapValues { (_, visibility) ->
        when (visibility) {
            AppProfileContentVisibility.VISIBLE,
            AppProfileContentVisibility.REDACTED_QUIET,
            -> AppStageProfileState.AVAILABLE

            AppProfileContentVisibility.REDACTED_LOCKED,
            AppProfileContentVisibility.REDACTED_UNAVAILABLE,
            -> AppStageProfileState.LOCKED
        }
    }

enum class FirstRunStatus {
    NEEDS_HOME_ROLE,
    REQUESTING_HOME_ROLE,
    COMPLETE,
}
