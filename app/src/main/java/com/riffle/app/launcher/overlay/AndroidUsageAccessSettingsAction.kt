package com.riffle.app.launcher.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings

internal class AndroidUsageAccessSettingsAction(
    private val context: Context,
) {
    fun open(): Boolean {
        val intent =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        return openUsageAccessSettings(
            isAvailable = { intent.resolveActivity(context.packageManager) != null },
            open = { context.startActivity(intent) },
        )
    }
}

/**
 * Opens the OEM-provided Usage Access screen only when it is available.
 *
 * Some devices do not expose a handler for the standard settings action, and others advertise
 * one that still fails when launched. Treat both cases as an unavailable action so the overlay can
 * remain expanded and usable.
 */
internal fun openUsageAccessSettings(
    isAvailable: () -> Boolean,
    open: () -> Unit,
): Boolean {
    if (!runCatching(isAvailable).getOrDefault(false)) return false

    return runCatching(open).isSuccess
}
