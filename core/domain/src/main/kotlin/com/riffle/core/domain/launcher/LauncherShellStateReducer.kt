package com.riffle.core.domain.launcher

class LauncherShellStateReducer {
    fun homeRoleChanged(
        currentState: LauncherShellState,
        homeRoleStatus: HomeRoleStatus,
    ): LauncherShellState {
        return currentState.copy(
            firstRunStatus =
                firstRunStatusFor(
                    currentState = currentState,
                    homeRoleStatus = homeRoleStatus,
                ),
            hasRecoveredHomeRoleRequest = false,
            homeRoleStatus = homeRoleStatus,
        )
    }

    fun defaultHomeRequestStarted(currentState: LauncherShellState): LauncherShellState =
        currentState.copy(
            firstRunStatus = FirstRunStatus.REQUESTING_HOME_ROLE,
            hasRecoveredHomeRoleRequest = false,
        )

    fun defaultHomeRequestLaunchFailed(currentState: LauncherShellState): LauncherShellState =
        currentState.copy(
            firstRunStatus = FirstRunStatus.NEEDS_HOME_ROLE,
            hasRecoveredHomeRoleRequest = false,
        )

    fun defaultHomeRequestReturned(currentState: LauncherShellState): LauncherShellState =
        if (currentState.firstRunStatus == FirstRunStatus.REQUESTING_HOME_ROLE) {
            currentState.copy(
                firstRunStatus = FirstRunStatus.NEEDS_HOME_ROLE,
                hasRecoveredHomeRoleRequest = false,
            )
        } else {
            currentState.copy(hasRecoveredHomeRoleRequest = false)
        }

    fun setupCardDismissed(currentState: LauncherShellState): LauncherShellState {
        return currentState.copy(setupCardDismissed = true)
    }

    fun firstRunCompleted(currentState: LauncherShellState): LauncherShellState =
        currentState.copy(firstRunStatus = FirstRunStatus.COMPLETE)

    fun navigationActionSelected(
        currentState: LauncherShellState,
        action: ShellNavigationAction,
    ): LauncherShellState = currentState.copy(destination = action.destination)

    private fun firstRunStatusFor(
        currentState: LauncherShellState,
        homeRoleStatus: HomeRoleStatus,
    ): FirstRunStatus =
        when {
            homeRoleStatus == HomeRoleStatus.DEFAULT_HOME -> FirstRunStatus.COMPLETE
            currentState.hasRecoveredHomeRoleRequest && homeRoleStatus == HomeRoleStatus.UNKNOWN ->
                FirstRunStatus.NEEDS_HOME_ROLE
            currentState.firstRunStatus == FirstRunStatus.REQUESTING_HOME_ROLE &&
                homeRoleStatus == HomeRoleStatus.UNKNOWN -> FirstRunStatus.REQUESTING_HOME_ROLE
            else -> FirstRunStatus.NEEDS_HOME_ROLE
        }

    private val ShellNavigationAction.destination: ShellDestination
        get() =
            when (this) {
                ShellNavigationAction.OpenHome -> ShellDestination.HOME
                ShellNavigationAction.OpenAppDrawer -> ShellDestination.APP_DRAWER
                ShellNavigationAction.OpenSearch -> ShellDestination.SEARCH
                ShellNavigationAction.OpenNotifications -> ShellDestination.NOTIFICATIONS
                ShellNavigationAction.OpenSettings -> ShellDestination.SETTINGS
            }
}
