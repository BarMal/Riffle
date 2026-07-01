package com.riffle.app.launcher

internal class LauncherActionRouter(
    private val activityActionHandler: LauncherActivityActionHandler,
    private val handleNotificationAction: (LauncherShellAction) -> Boolean,
    private val handleSettingsAction: (LauncherShellAction) -> Boolean,
    private val appActionHandler: LauncherAppActionHandler,
) {
    fun handle(action: LauncherShellAction): Boolean =
        activityActionHandler.handle(action) ||
            handleNotificationAction(action) ||
            handleSettingsAction(action) ||
            appActionHandler.handle(action)
}
