package com.riffle.app.launcher.overlay

import android.content.Context
import android.content.Intent
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings

class AndroidOverlayDockServiceController(
    private val context: Context,
) {
    private var serviceShouldBeRunning: Boolean? = null

    fun sync(
        settings: LauncherSettings,
        permissionStatus: OverlayDockPermissionStatus,
    ) {
        val command = overlayDockServiceCommand(settings, permissionStatus)
        val shouldBeRunning = command == OverlayDockServiceCommand.START
        if (serviceShouldBeRunning == shouldBeRunning) return

        val synchronized =
            when (command) {
                OverlayDockServiceCommand.START ->
                    runCatching { context.startService(Intent(context, OverlayDockService::class.java)) }.isSuccess

                OverlayDockServiceCommand.STOP ->
                    runCatching { context.stopService(Intent(context, OverlayDockService::class.java)) }.isSuccess
            }
        if (synchronized) {
            serviceShouldBeRunning = shouldBeRunning
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
        settings.overlayDock.enabled && permissionStatus == OverlayDockPermissionStatus.GRANTED ->
            OverlayDockServiceCommand.START

        else -> OverlayDockServiceCommand.STOP
    }
