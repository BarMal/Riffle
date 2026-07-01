package com.riffle.app.launcher

internal class LauncherActionRouter(
    private val activityActionHandler: LauncherActivityActionHandler,
    private val notificationActionHandler: LauncherNotificationActionHandler,
    private val settingsActionHandler: LauncherSettingsActionHandler,
    private val appActionHandler: LauncherAppActionHandler,
) {
    fun handle(action: LauncherShellAction): Boolean =
        activityActionHandler.handle(action) ||
            notificationActionHandler.handle(action) ||
            settingsActionHandler.handle(action) ||
            appActionHandler.handle(action)
}
