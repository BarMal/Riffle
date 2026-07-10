package com.riffle.app.launcher.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.riffle.app.launcher.systemPermissionPackageCandidates
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

class AndroidNotificationAccessGateway(
    private val context: Context,
) {
    fun getNotificationAccessStatus(): NotificationAccessStatus =
        notificationAccessStatus(
            appPackageName = context.packageName,
            enabledListenerPackages = NotificationManagerCompat.getEnabledListenerPackages(context),
            isListenerConnected = RiffleNotificationListenerConnection.isConnected(),
        )

    fun createNotificationListenerSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}

internal fun notificationAccessStatus(
    appPackageName: String,
    enabledListenerPackages: Set<String>,
    isListenerConnected: Boolean,
): NotificationAccessStatus =
    when {
        isListenerConnected -> NotificationAccessStatus.GRANTED
        enabledListenerPackages.any(systemPermissionPackageCandidates(appPackageName)::contains) ->
            NotificationAccessStatus.GRANTED
        else -> NotificationAccessStatus.NOT_GRANTED
    }
