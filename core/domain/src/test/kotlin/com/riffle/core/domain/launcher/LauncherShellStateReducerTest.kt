package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherShellStateReducerTest {
    private val reducer = LauncherShellStateReducer()

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
    fun unresolvedRecoveredHomeRoleRequestReturnsToRetryAfterLiveReconciliation() {
        val state =
            reducer.homeRoleChanged(
                currentState =
                    LauncherShellState(
                        firstRunStatus = FirstRunStatus.REQUESTING_HOME_ROLE,
                        hasRecoveredHomeRoleRequest = true,
                    ),
                homeRoleStatus = HomeRoleStatus.UNKNOWN,
            )

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, state.firstRunStatus)
        assertFalse(state.hasRecoveredHomeRoleRequest)
        assertTrue(state.shouldShowSetupCard)
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

    @Test
    fun theLibraryRemembersWhetherItOpenedOverTheStream() {
        val stream = LauncherShellState(destination = ShellDestination.STREAM)

        val overStream = reducer.navigationActionSelected(stream, ShellNavigationAction.OpenAppDrawer)
        val searching = reducer.navigationActionSelected(overStream, ShellNavigationAction.OpenSearch)
        val closed = reducer.navigationActionSelected(searching, ShellNavigationAction.OpenStream)
        val overHome =
            reducer.navigationActionSelected(LauncherShellState(), ShellNavigationAction.OpenAppDrawer)

        assertTrue(overStream.libraryOverStream)
        assertTrue(searching.libraryOverStream)
        assertFalse(closed.libraryOverStream)
        assertFalse(overHome.libraryOverStream)
        assertTrue(ShellDestination.APP_DRAWER.isLibrary && ShellDestination.SEARCH.isLibrary)
        assertFalse(ShellDestination.STREAM.isLibrary)
    }

    private fun withFeatures(features: FeatureSettings) =
        LauncherShellState(
            launcherSettings = LauncherSettings(features = features),
        )

    @Test
    fun theStreamHasNoWayInWhileItsSwitchIsOff() {
        val state = withFeatures(FeatureSettings(stream = false))
        val after = reducer.navigationActionSelected(state, ShellNavigationAction.OpenStream)
        assertEquals(ShellDestination.HOME, after.destination)
        assertEquals(state, after)
        // The other places still open.
        assertEquals(
            ShellDestination.APP_DRAWER,
            reducer.navigationActionSelected(state, ShellNavigationAction.OpenAppDrawer).destination,
        )
    }

    @Test
    fun theLibrarySheetHasNoWayInWhileItsSwitchIsOffButSearchStays() {
        val state = withFeatures(FeatureSettings(library = false))
        assertEquals(state, reducer.navigationActionSelected(state, ShellNavigationAction.OpenAppDrawer))
        assertEquals(
            ShellDestination.SEARCH,
            reducer.navigationActionSelected(state, ShellNavigationAction.OpenSearch).destination,
        )
        assertEquals(
            ShellDestination.STREAM,
            reducer.navigationActionSelected(state, ShellNavigationAction.OpenStream).destination,
        )
    }

    @Test
    fun simpleModeClosesTheStreamAndTheLibraryButKeepsSearchAndSettings() {
        val state = withFeatures(FeatureSettings(simpleMode = true))
        assertEquals(state, reducer.navigationActionSelected(state, ShellNavigationAction.OpenStream))
        assertEquals(state, reducer.navigationActionSelected(state, ShellNavigationAction.OpenAppDrawer))
        assertEquals(
            ShellDestination.SEARCH,
            reducer.navigationActionSelected(state, ShellNavigationAction.OpenSearch).destination,
        )
        assertEquals(
            ShellDestination.SETTINGS,
            reducer.navigationActionSelected(state, ShellNavigationAction.OpenSettings).destination,
        )
    }
}
