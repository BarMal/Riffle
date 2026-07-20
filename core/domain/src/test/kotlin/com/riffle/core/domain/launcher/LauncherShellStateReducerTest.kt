package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStageContentKind
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherShellStateReducerTest {
    private val reducer = LauncherShellStateReducer()

    @Test
    fun appStageSnapshotIncludesVisibleNotificationAndMediaContent() {
        val profile = AppProfile.personal()
        val app =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.example.music"),
                        activityName = AppActivityName(".MainActivity"),
                        profile = profile,
                    ),
                label = "Music",
            )
        val notification =
            LauncherNotification(
                key = LauncherNotificationKey("playing"),
                packageName = app.identity.packageName,
                profileId = profile.id,
                isMediaSession = true,
                postedAtEpochMillis = 42L,
            )
        val state =
            LauncherShellState(
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                profileContentVisibility = mapOf(profile.id to AppProfileContentVisibility.VISIBLE),
                installedApps = listOf(app),
                notificationGroupsByApp =
                    listOf(
                        AppNotificationGroup(
                            packageName = app.identity.packageName,
                            profileId = profile.id,
                            latestCategory = NotificationCategory.UNKNOWN,
                            latestAgeBucket = NotificationAgeBucket.RECENT,
                            notifications = listOf(notification),
                        ),
                    ),
            )

        val stage = state.appStageSnapshot().stages.single()

        assertEquals(AppStageContentKind.MEDIA, stage.content.single().kind)
        assertEquals("stage-notification:personal:playing", stage.content.single().id.value)

        val refreshed =
            state
                .copy(notificationGroupsByApp = emptyList())
                .appStageSnapshot(previous = state.appStageSnapshot())

        assertEquals(1, refreshed.stages.size)
        assertTrue(refreshed.stages.single().content.isEmpty())
    }

    @Test
    fun defaultHomeCompletesFirstRun() {
        val state =
            reducer.homeRoleChanged(
                currentState = LauncherShellState(),
                homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
            )

        assertEquals(FirstRunStatus.COMPLETE, state.firstRunStatus)
        assertFalse(state.shouldShowSetupCard)
        assertTrue(state.shouldShowEmptyHome)
    }

    @Test
    fun missingDefaultHomeKeepsPreviewSetupAvailableWithoutBlockingHome() {
        val state =
            reducer.homeRoleChanged(
                currentState = LauncherShellState(),
                homeRoleStatus = HomeRoleStatus.NOT_DEFAULT_HOME,
            )

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, state.firstRunStatus)
        assertTrue(state.shouldShowSetupCard)
        assertFalse(state.shouldShowDefaultHomePrompt)
        assertTrue(state.shouldShowEmptyHome)
    }

    @Test
    fun previewNavigationRemainsExplorableWithoutRestoringTheBlockingPrompt() {
        val previewState =
            reducer.homeRoleChanged(
                currentState = LauncherShellState(),
                homeRoleStatus = HomeRoleStatus.NOT_DEFAULT_HOME,
            )

        val settingsState =
            reducer.navigationActionSelected(
                currentState = previewState,
                action = ShellNavigationAction.OpenSettings,
            )

        assertEquals(ShellDestination.SETTINGS, settingsState.destination)
        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, settingsState.firstRunStatus)
        assertTrue(settingsState.shouldShowSetupCard)
        assertFalse(settingsState.shouldShowDefaultHomePrompt)
    }

    @Test
    fun completedFirstRunDoesNotOverrideUnknownLiveHomeStatus() {
        val state =
            reducer.homeRoleChanged(
                currentState = LauncherShellState(firstRunStatus = FirstRunStatus.COMPLETE),
                homeRoleStatus = HomeRoleStatus.UNKNOWN,
            )

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, state.firstRunStatus)
        assertEquals(HomeRoleStatus.UNKNOWN, state.homeRoleStatus)
        assertTrue(state.shouldShowSetupCard)
        assertTrue(state.shouldShowEmptyHome)
    }

    @Test
    fun completedDefaultHomeDoesNotOverrideUnknownLiveHomeStatus() {
        val state =
            reducer.homeRoleChanged(
                currentState =
                    LauncherShellState(
                        firstRunStatus = FirstRunStatus.COMPLETE,
                        homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
                    ),
                homeRoleStatus = HomeRoleStatus.UNKNOWN,
            )

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, state.firstRunStatus)
        assertEquals(HomeRoleStatus.UNKNOWN, state.homeRoleStatus)
        assertTrue(state.shouldShowSetupCard)
    }

    @Test
    fun unresolvedHomeRoleAfterRequestDoesNotShowAnotherSetupPrompt() {
        val state =
            reducer.homeRoleChanged(
                currentState =
                    reducer.defaultHomeRequestStarted(
                        LauncherShellState(),
                    ),
                homeRoleStatus = HomeRoleStatus.UNKNOWN,
            )

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, state.firstRunStatus)
        assertTrue(state.shouldShowSetupCard)
        assertTrue(state.shouldShowEmptyHome)
    }

    @Test
    fun failedHomeRoleRequestReturnsToRecoverableState() {
        val state =
            reducer.defaultHomeRequestLaunchFailed(
                reducer.defaultHomeRequestStarted(LauncherShellState()),
            )

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, state.firstRunStatus)
    }

    @Test
    fun returnedHomeRoleRequestRestoresRetryWithoutAssumingLiveRoleTruth() {
        val state =
            reducer.defaultHomeRequestReturned(
                reducer.defaultHomeRequestStarted(
                    LauncherShellState(
                        destination = ShellDestination.SETTINGS,
                        homeRoleStatus = HomeRoleStatus.UNKNOWN,
                    ),
                ),
            )

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, state.firstRunStatus)
        assertEquals(HomeRoleStatus.UNKNOWN, state.homeRoleStatus)
        assertEquals(ShellDestination.SETTINGS, state.destination)
    }

    @Test
    fun unresolvedHomeRoleAfterRequestPreservesThePreviewDestination() {
        val settingsState =
            reducer.navigationActionSelected(
                currentState = LauncherShellState(),
                action = ShellNavigationAction.OpenSettings,
            )

        val returnedState =
            reducer.homeRoleChanged(
                currentState = reducer.defaultHomeRequestStarted(settingsState),
                homeRoleStatus = HomeRoleStatus.UNKNOWN,
            )

        assertEquals(ShellDestination.SETTINGS, returnedState.destination)
        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, returnedState.firstRunStatus)
        assertFalse(returnedState.shouldShowDefaultHomePrompt)
    }

    @Test
    fun dismissedSetupCardDoesNotChangeLiveHomeStatus() {
        val state = reducer.setupCardDismissed(LauncherShellState(homeRoleStatus = HomeRoleStatus.NOT_DEFAULT_HOME))

        assertTrue(state.setupCardDismissed)
        assertFalse(state.shouldShowSetupCard)
        assertEquals(HomeRoleStatus.NOT_DEFAULT_HOME, state.homeRoleStatus)
    }

    @Test
    fun navigationActionsSelectShellDestinations() {
        val appDrawerState =
            reducer.navigationActionSelected(
                currentState = LauncherShellState(firstRunStatus = FirstRunStatus.COMPLETE),
                action = ShellNavigationAction.OpenAppDrawer,
            )
        val searchState =
            reducer.navigationActionSelected(
                currentState = appDrawerState,
                action = ShellNavigationAction.OpenSearch,
            )
        val settingsState =
            reducer.navigationActionSelected(
                currentState = searchState,
                action = ShellNavigationAction.OpenSettings,
            )
        val notificationsState =
            reducer.navigationActionSelected(
                currentState = settingsState,
                action = ShellNavigationAction.OpenNotifications,
            )
        val homeState =
            reducer.navigationActionSelected(
                currentState = notificationsState,
                action = ShellNavigationAction.OpenHome,
            )

        assertEquals(ShellDestination.APP_DRAWER, appDrawerState.destination)
        assertEquals(ShellDestination.SEARCH, searchState.destination)
        assertEquals(ShellDestination.SETTINGS, settingsState.destination)
        assertEquals(ShellDestination.NOTIFICATIONS, notificationsState.destination)
        assertEquals(ShellDestination.HOME, homeState.destination)
    }
}
