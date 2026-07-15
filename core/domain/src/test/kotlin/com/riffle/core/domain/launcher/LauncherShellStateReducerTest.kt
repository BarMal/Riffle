package com.riffle.core.domain.launcher

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
        assertFalse(state.shouldShowDefaultHomePrompt)
        assertTrue(state.shouldShowEmptyHome)
    }

    @Test
    fun missingDefaultHomeKeepsPromptVisible() {
        val state =
            reducer.homeRoleChanged(
                currentState = LauncherShellState(),
                homeRoleStatus = HomeRoleStatus.NOT_DEFAULT_HOME,
            )

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, state.firstRunStatus)
        assertTrue(state.shouldShowDefaultHomePrompt)
        assertFalse(state.shouldShowEmptyHome)
    }

    @Test
    fun completedFirstRunStaysCompleteWhenRoleCannotBeDetermined() {
        val state =
            reducer.homeRoleChanged(
                currentState = LauncherShellState(firstRunStatus = FirstRunStatus.COMPLETE),
                homeRoleStatus = HomeRoleStatus.UNKNOWN,
            )

        assertEquals(FirstRunStatus.COMPLETE, state.firstRunStatus)
        assertTrue(state.shouldShowEmptyHome)
    }

    @Test
    fun completedDefaultHomeStaysDefaultWhenRoleCannotBeDetermined() {
        val state =
            reducer.homeRoleChanged(
                currentState =
                    LauncherShellState(
                        firstRunStatus = FirstRunStatus.COMPLETE,
                        homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
                    ),
                homeRoleStatus = HomeRoleStatus.UNKNOWN,
            )

        assertEquals(HomeRoleStatus.DEFAULT_HOME, state.homeRoleStatus)
        assertFalse(state.shouldShowDefaultHomePrompt)
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
        assertFalse(state.shouldShowDefaultHomePrompt)
        assertTrue(state.shouldShowEmptyHome)
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
