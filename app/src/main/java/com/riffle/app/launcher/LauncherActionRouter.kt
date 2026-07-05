package com.riffle.app.launcher

internal class LauncherActionRouter(
    private val activityActionHandler: LauncherActivityActionHandler,
    private val notificationActionHandler: LauncherNotificationActionHandler,
    private val settingsActionHandler: LauncherSettingsActionHandler,
    private val appActionHandler: LauncherAppActionHandler,
) {
    fun handle(action: LauncherShellAction): Boolean =
        when (action.launcherActionDomain()) {
            LauncherActionDomain.ACTIVITY -> activityActionHandler.handle(action)
            LauncherActionDomain.NOTIFICATION -> notificationActionHandler.handle(action)
            LauncherActionDomain.SETTINGS -> settingsActionHandler.handle(action)
            LauncherActionDomain.APP -> appActionHandler.handle(action)
            null -> false
        }
}
