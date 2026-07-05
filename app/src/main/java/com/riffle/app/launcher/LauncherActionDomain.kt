package com.riffle.app.launcher

internal enum class LauncherActionDomain {
    ACTIVITY,
    NOTIFICATION,
    SETTINGS,
    APP,
}

internal fun LauncherShellAction.launcherActionDomain(): LauncherActionDomain? =
    when {
        launcherActivityRoute() != null -> LauncherActionDomain.ACTIVITY
        launcherNotificationActionRoute() != null -> LauncherActionDomain.NOTIFICATION
        launcherSettingsActionRoute() != null -> LauncherActionDomain.SETTINGS
        launcherAppActionRoute() != null -> LauncherActionDomain.APP
        else -> null
    }
