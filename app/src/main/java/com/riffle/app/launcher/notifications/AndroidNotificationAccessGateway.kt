package com.riffle.app.launcher.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
            enabledListenerPackages =
                enabledNotificationListenerPackages(
                    notificationManagerPackages = {
                        NotificationManagerCompat.getEnabledListenerPackages(context)
                    },
                    secureSetting = {
                        Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS)
                    },
                ),
            isListenerConnected = RiffleNotificationListenerConnection.isConnected(),
        )

    fun createNotificationListenerSettingsIntent(): Intent =
        Intent(notificationListenerSettingsAction(Build.VERSION.SDK_INT)).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, RiffleNotificationListenerService::class.java).flattenToString(),
                )
            }
        }
}

internal fun enabledNotificationListenerPackages(enabledListeners: String?): Set<String> =
    enabledListeners
        ?.split(':')
        .orEmpty()
        .mapNotNull { component ->
            component.substringBefore('/', missingDelimiterValue = "").takeIf(String::isNotEmpty)
        }.toSet()

/**
 * Read both platform sources independently. Some devices can temporarily fail one source while a
 * notification-listener settings change is propagating; the other source is still a valid signal.
 * A failed read never grants access on its own.
 */
internal fun enabledNotificationListenerPackages(
    notificationManagerPackages: () -> Set<String>,
    secureSetting: () -> String?,
): Set<String> =
    runCatching(notificationManagerPackages).getOrDefault(emptySet()) +
        runCatching(secureSetting)
            .getOrNull()
            .let(::enabledNotificationListenerPackages)

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

private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

internal fun notificationListenerSettingsAction(sdkInt: Int): String =
    if (sdkInt >= Build.VERSION_CODES.R) {
        Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
    } else {
        Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
    }
