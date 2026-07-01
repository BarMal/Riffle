package com.riffle.app.launcher

internal fun interface LauncherSettingsActionHandler {
    fun handle(action: LauncherShellAction): Boolean
}

internal class DefaultLauncherSettingsActionHandler(
    private val callbacks: LauncherSettingsActionCallbacks,
) : LauncherSettingsActionHandler {
    override fun handle(action: LauncherShellAction): Boolean =
        when (val route = action.launcherSettingsActionRoute()) {
            is LauncherSettingsActionRoute.SettingsState -> {
                callbacks.applySettingsState(route.action)
                true
            }

            LauncherSettingsActionRoute.RequestNotificationAccess -> {
                callbacks.requestNotificationAccess()
                true
            }

            LauncherSettingsActionRoute.ExportBackup -> {
                callbacks.exportBackup()
                true
            }

            LauncherSettingsActionRoute.RequestImportBackup -> {
                callbacks.importBackup()
                true
            }

            null -> false
        }
}

internal data class LauncherSettingsActionCallbacks(
    val applySettingsState: (LauncherShellAction) -> Unit,
    val requestNotificationAccess: () -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
)

internal sealed interface LauncherSettingsActionRoute {
    data class SettingsState(
        val action: LauncherShellAction,
    ) : LauncherSettingsActionRoute

    data object RequestNotificationAccess : LauncherSettingsActionRoute

    data object ExportBackup : LauncherSettingsActionRoute

    data object RequestImportBackup : LauncherSettingsActionRoute
}

internal fun LauncherShellAction.launcherSettingsActionRoute(): LauncherSettingsActionRoute? =
    when (this) {
        is LauncherShellAction.SelectWallpaperSource,
        is LauncherShellAction.SelectHomeSwipeGestureAction,
        LauncherShellAction.ResetHomeSwipeGestureActions,
        is LauncherShellAction.SelectHapticFeedbackStrength,
        is LauncherShellAction.SelectSettingsLayoutDeviceClass,
        is LauncherShellAction.ImportLauncherBackup,
        -> LauncherSettingsActionRoute.SettingsState(this)

        LauncherShellAction.RequestNotificationAccess -> LauncherSettingsActionRoute.RequestNotificationAccess
        LauncherShellAction.ExportLauncherBackup -> LauncherSettingsActionRoute.ExportBackup
        LauncherShellAction.RequestImportLauncherBackup -> LauncherSettingsActionRoute.RequestImportBackup
        else -> null
    }
