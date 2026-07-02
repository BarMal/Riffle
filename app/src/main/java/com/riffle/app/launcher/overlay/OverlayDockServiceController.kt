package com.riffle.app.launcher.overlay

import android.content.Context
import android.content.Intent
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings

class AndroidOverlayDockServiceController(
    private val context: Context,
) {
    fun sync(
        settings: LauncherSettings,
        permissionStatus: OverlayDockPermissionStatus,
    ) {
        when (overlayDockServiceCommand(settings, permissionStatus)) {
            OverlayDockServiceCommand.START ->
                context.startService(Intent(context, OverlayDockService::class.java))

            OverlayDockServiceCommand.STOP ->
                context.stopService(Intent(context, OverlayDockService::class.java))
        }
    }
}

internal enum class OverlayDockServiceCommand {
    START,
    STOP,
}

internal fun overlayDockServiceCommand(
    settings: LauncherSettings,
    permissionStatus: OverlayDockPermissionStatus,
): OverlayDockServiceCommand =
    when {
        settings.overlayDock.enabled && permissionStatus != OverlayDockPermissionStatus.NOT_GRANTED ->
            OverlayDockServiceCommand.START

        else -> OverlayDockServiceCommand.STOP
    }
