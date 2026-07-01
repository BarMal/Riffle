package com.riffle.app.launcher.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

class AndroidNotificationAccessGateway(
    private val context: Context,
) {
    fun getNotificationAccessStatus(): NotificationAccessStatus =
        notificationAccessStatusFromEnabledListenerPackages(
            appPackageName = context.packageName,
            enabledListenerPackages = NotificationManagerCompat.getEnabledListenerPackages(context),
        )

    fun createNotificationListenerSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}

internal fun notificationAccessStatusFromEnabledListenerPackages(
    appPackageName: String,
    enabledListenerPackages: Set<String>,
): NotificationAccessStatus =
    when {
        appPackageName in enabledListenerPackages -> NotificationAccessStatus.GRANTED
        else -> NotificationAccessStatus.NOT_GRANTED
    }
