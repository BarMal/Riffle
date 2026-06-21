package com.riffle.app.launcher.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

class AndroidNotificationAccessGateway(
    private val context: Context,
) {
    fun getNotificationAccessStatus(): NotificationAccessStatus =
        if (enabledNotificationListenerComponents().any { component -> component.packageName == context.packageName }) {
            NotificationAccessStatus.GRANTED
        } else {
            NotificationAccessStatus.NOT_GRANTED
        }

    fun createNotificationListenerSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    private fun enabledNotificationListenerComponents(): List<ComponentName> =
        Settings.Secure.getString(
            context.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS,
        )
            ?.split(':')
            .orEmpty()
            .mapNotNull(ComponentName::unflattenFromString)

    private companion object {
        const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    }
}
