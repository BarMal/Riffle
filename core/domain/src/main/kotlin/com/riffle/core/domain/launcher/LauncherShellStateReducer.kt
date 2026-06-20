package com.riffle.core.domain.launcher

class LauncherShellStateReducer {
    fun homeRoleChanged(
        currentState: LauncherShellState,
        homeRoleStatus: HomeRoleStatus,
    ): LauncherShellState =
        currentState.copy(
            firstRunStatus =
                firstRunStatusFor(
                    currentState = currentState,
                    homeRoleStatus = homeRoleStatus,
                ),
            homeRoleStatus = homeRoleStatus,
        )

    fun defaultHomeRequestStarted(currentState: LauncherShellState): LauncherShellState =
        currentState.copy(firstRunStatus = FirstRunStatus.REQUESTING_HOME_ROLE)

    fun firstRunCompleted(currentState: LauncherShellState): LauncherShellState =
        currentState.copy(firstRunStatus = FirstRunStatus.COMPLETE)

    private fun firstRunStatusFor(
        currentState: LauncherShellState,
        homeRoleStatus: HomeRoleStatus,
    ): FirstRunStatus =
        when {
            currentState.firstRunStatus == FirstRunStatus.COMPLETE -> FirstRunStatus.COMPLETE
            homeRoleStatus == HomeRoleStatus.DEFAULT_HOME -> FirstRunStatus.COMPLETE
            else -> FirstRunStatus.NEEDS_HOME_ROLE
        }
}
