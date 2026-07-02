package com.riffle.app.launcher.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus

class AndroidOverlayDockPermissionGateway(
    private val context: Context,
) {
    fun getOverlayDockPermissionStatus(): OverlayDockPermissionStatus =
        overlayDockPermissionStatus(canDrawOverlays = Settings.canDrawOverlays(context))

    fun createOverlayPermissionSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
}

internal fun overlayDockPermissionStatus(canDrawOverlays: Boolean): OverlayDockPermissionStatus =
    when {
        canDrawOverlays -> OverlayDockPermissionStatus.GRANTED
        else -> OverlayDockPermissionStatus.NOT_GRANTED
    }
