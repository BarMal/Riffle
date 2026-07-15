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
    private var lastKnownStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN

    fun getNotificationAccessStatus(): NotificationAccessStatus =
        notificationAccessStatus(
            appPackageName = context.packageName,
            enabledListenerPackageReads =
                enabledNotificationListenerPackages(
                    notificationManagerPackages = {
                        NotificationManagerCompat.getEnabledListenerPackages(context)
                    },
                    secureSetting = {
                        Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS)
                    },
                ),
            isListenerConnected = RiffleNotificationListenerConnection.isConnected(),
            previousStatus = lastKnownStatus,
        ).also { status ->
            lastKnownStatus = status
        }

    fun createNotificationListenerSettingsIntents(): List<Intent> =
        notificationListenerSettingsIntentData(
            sdkInt = Build.VERSION.SDK_INT,
            listenerComponentName =
                ComponentName(context, RiffleNotificationListenerService::class.java).flattenToString(),
        ).map(NotificationListenerSettingsIntentData::toIntent)
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
internal data class EnabledNotificationListenerPackageReads(
    val packages: Set<String>,
    val hasSuccessfulRead: Boolean,
    val hasFailedRead: Boolean = false,
)

internal fun enabledNotificationListenerPackages(
    notificationManagerPackages: () -> Set<String>,
    secureSetting: () -> String?,
): EnabledNotificationListenerPackageReads {
    val notificationManagerResult = runCatching(notificationManagerPackages)
    val secureSettingResult = runCatching(secureSetting)
    return EnabledNotificationListenerPackageReads(
        packages =
            notificationManagerResult.getOrDefault(emptySet()) +
                secureSettingResult.getOrNull().let(::enabledNotificationListenerPackages),
        hasSuccessfulRead = notificationManagerResult.isSuccess || secureSettingResult.isSuccess,
        hasFailedRead = notificationManagerResult.isFailure || secureSettingResult.isFailure,
    )
}

internal fun notificationAccessStatus(
    appPackageName: String,
    enabledListenerPackageReads: EnabledNotificationListenerPackageReads,
    isListenerConnected: Boolean,
    previousStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
): NotificationAccessStatus =
    when {
        isListenerConnected -> NotificationAccessStatus.GRANTED
        enabledListenerPackageReads.packages.any(systemPermissionPackageCandidates(appPackageName)::contains) ->
            NotificationAccessStatus.GRANTED
        !enabledListenerPackageReads.hasSuccessfulRead -> NotificationAccessStatus.UNKNOWN
        enabledListenerPackageReads.hasFailedRead &&
            previousStatus == NotificationAccessStatus.GRANTED ->
            NotificationAccessStatus.GRANTED
        enabledListenerPackageReads.hasFailedRead -> NotificationAccessStatus.UNKNOWN
        previousStatus == NotificationAccessStatus.GRANTED || previousStatus == NotificationAccessStatus.REVOKED ->
            NotificationAccessStatus.REVOKED
        else -> NotificationAccessStatus.NOT_GRANTED
    }

private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

internal data class NotificationListenerSettingsIntentData(
    val action: String,
    val listenerComponentName: String? = null,
) {
    fun toIntent(): Intent =
        Intent(action).apply {
            listenerComponentName?.let { componentName ->
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName)
            }
        }
}

internal fun notificationListenerSettingsIntentData(
    sdkInt: Int,
    listenerComponentName: String,
): List<NotificationListenerSettingsIntentData> =
    if (sdkInt >= Build.VERSION_CODES.R) {
        listOf(
            NotificationListenerSettingsIntentData(
                action = Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS,
                listenerComponentName = listenerComponentName,
            ),
            NotificationListenerSettingsIntentData(
                action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            ),
        )
    } else {
        listOf(NotificationListenerSettingsIntentData(action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

internal fun <Candidate> launchNotificationListenerSettings(
    candidates: List<Candidate>,
    launch: (Candidate) -> Unit,
): Boolean = candidates.any { candidate -> runCatching { launch(candidate) }.isSuccess }
