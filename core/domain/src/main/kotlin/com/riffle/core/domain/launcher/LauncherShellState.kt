package com.riffle.core.domain.launcher

data class LauncherShellState(
    val firstRunStatus: FirstRunStatus = FirstRunStatus.NEEDS_HOME_ROLE,
    val homeRoleStatus: HomeRoleStatus = HomeRoleStatus.UNKNOWN,
) {
    val shouldShowDefaultHomePrompt: Boolean =
        firstRunStatus != FirstRunStatus.COMPLETE && homeRoleStatus != HomeRoleStatus.DEFAULT_HOME

    val shouldShowEmptyHome: Boolean =
        firstRunStatus == FirstRunStatus.COMPLETE || homeRoleStatus == HomeRoleStatus.DEFAULT_HOME
}

enum class FirstRunStatus {
    NEEDS_HOME_ROLE,
    REQUESTING_HOME_ROLE,
    COMPLETE,
}
