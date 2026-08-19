package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStageContent
import com.riffle.core.domain.launcher.cards.AppStageContentKind
import com.riffle.core.domain.launcher.cards.AppStageContentSnapshot
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStageIdentitySnapshot
import com.riffle.core.domain.launcher.cards.AppStagePlanner
import com.riffle.core.domain.launcher.cards.AppStageProfileState
import com.riffle.core.domain.launcher.cards.AppStageSnapshot
import com.riffle.core.domain.launcher.cards.LauncherCardId
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
import com.riffle.core.domain.launcher.rss.FeedProfileStatus
import com.riffle.core.domain.launcher.rss.FeedStageCacheProjection
import com.riffle.core.domain.launcher.rss.FeedStagePlanner
import com.riffle.core.domain.launcher.rss.FeedStageSnapshot
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.feedStagePreferencesFor
import com.riffle.core.domain.launcher.settings.stagePreferencesFor
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

data class LauncherShellState(
    val firstRunStatus: FirstRunStatus = FirstRunStatus.NEEDS_HOME_ROLE,
    /** A Home-role request was pending when this shell was recreated and needs live reconciliation. */
    val hasRecoveredHomeRoleRequest: Boolean = false,
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

    /** Reconciles optional AdaptiveStage stages from the same installed-app/profile/settings snapshot. */
    fun appStageSnapshot(
        contentSnapshot: AppStageContentSnapshot = appStageContentSnapshot(),
        previous: AppStageSnapshot? = null,
        planner: AppStagePlanner = AppStagePlanner(),
    ): AppStageSnapshot =
        planner.reconcile(
            identitySnapshot =
                AppStageIdentitySnapshot(
                    installedStageIds = installedApps.map(InstalledApp::toAppStageId).distinct(),
                    profileStates = profileStatesForStages(),
                ),
            contentSnapshot = contentSnapshot,
            preferences =
                launcherSettings
                    .cards
                    .stagePreferencesFor(homeLayoutSet.activeKey)
                    .let { preferences ->
                        if (preferences.selectedStageId == null) {
                            preferences.copy(selectedStageId = previous?.preferences?.selectedStageId)
                        } else {
                            preferences
                        }
                    },
            previous = previous,
        )

    /**
     * Reconciles AdaptiveStage feed stages from [configuredFeeds] and [feedCacheProjections].
     *
     * This is a parallel projection alongside [appStageSnapshot] rather than a merge into
     * [AppStageSnapshot]: feed stages have no package/app identity, and unlike app stages they are
     * never pruned for being empty (every configured, non-removed-profile feed always projects a
     * stage). Coexistence with pinned app stages, the selected app-stage focus, and the recent/
     * frequently-used stack is therefore preserved by construction -- this snapshot only ever adds
     * an independent, separately selectable list of feed stages for a renderer to also read.
     */
    fun feedStageSnapshot(
        previous: FeedStageSnapshot? = null,
        planner: FeedStagePlanner = FeedStagePlanner(),
    ): FeedStageSnapshot =
        planner.reconcile(
            configuredFeeds = configuredFeeds,
            profileStatuses = feedProfileStatusesForStages(),
            cacheProjections = feedCacheProjections,
            preferences =
                launcherSettings
                    .cards
                    .feedStagePreferencesFor(homeLayoutSet.activeKey)
                    .let { preferences ->
                        if (preferences.selectedStageId == null) {
                            preferences.copy(selectedStageId = previous?.preferences?.selectedStageId)
                        } else {
                            preferences
                        }
                    },
            previous = previous,
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

enum class WidgetProviderCatalogStatus {
    LOADING,
    READY,
    FAILED,
}

private fun InstalledApp.toAppStageId(): AppStageId = AppStageId(identity.packageName, identity.profile.id)

private fun LauncherShellState.appStageContentSnapshot(): AppStageContentSnapshot =
    if (notificationAccessStatus != NotificationAccessStatus.GRANTED) {
        AppStageContentSnapshot()
    } else {
        AppStageContentSnapshot(
            notificationGroupsByApp
                .flatMap(AppNotificationGroup::notifications)
                .filter { notification ->
                    profileContentVisibility[notification.profileId] in
                        setOf(
                            AppProfileContentVisibility.VISIBLE,
                            AppProfileContentVisibility.REDACTED_QUIET,
                        )
                }.map { notification ->
                    AppStageContent(
                        id =
                            LauncherCardId(
                                "stage-notification:${notification.profileId.value}:${notification.key.value}",
                            ),
                        stageId = AppStageId(notification.packageName, notification.profileId),
                        kind =
                            if (notification.isMediaSession) {
                                AppStageContentKind.MEDIA
                            } else {
                                AppStageContentKind.NOTIFICATION
                            },
                        meaningfulActivityAtEpochMillis = notification.postedAtEpochMillis.coerceAtLeast(0L),
                    )
                },
        )
    }

private fun LauncherShellState.profileStatesForStages(): Map<AppProfileId, AppStageProfileState> {
    return profileContentVisibility.mapValues { (_, visibility) ->
        when (visibility) {
            AppProfileContentVisibility.VISIBLE,
            AppProfileContentVisibility.REDACTED_QUIET,
            -> AppStageProfileState.AVAILABLE

            AppProfileContentVisibility.REDACTED_LOCKED,
            AppProfileContentVisibility.REDACTED_UNAVAILABLE,
            -> AppStageProfileState.LOCKED
        }
    }
}

/** Mirrors profileStatesForStages() above for feed profile-lock handling. */
private fun LauncherShellState.feedProfileStatusesForStages(): Map<AppProfileId, FeedProfileStatus> {
    return profileContentVisibility.mapValues { (_, visibility) ->
        when (visibility) {
            AppProfileContentVisibility.VISIBLE,
            AppProfileContentVisibility.REDACTED_QUIET,
            -> FeedProfileStatus.AVAILABLE

            AppProfileContentVisibility.REDACTED_LOCKED,
            AppProfileContentVisibility.REDACTED_UNAVAILABLE,
            -> FeedProfileStatus.LOCKED
        }
    }
}

enum class FirstRunStatus {
    NEEDS_HOME_ROLE,
    REQUESTING_HOME_ROLE,
    COMPLETE,
}
