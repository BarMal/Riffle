package com.riffle.app.launcher.notifications

import android.os.Build
import android.provider.Settings
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNotificationAccessGatewayTest {
    @Test
    fun directsSupportedDevicesToTheListenerDetailSettings() {
        assertEquals(
            Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS,
            notificationListenerSettingsAction(Build.VERSION_CODES.R),
        )
    }

    @Test
    fun retainsTheListenerSettingsListOnOlderDevices() {
        assertEquals(
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            notificationListenerSettingsAction(Build.VERSION_CODES.Q),
        )
    }

    @Test
    fun reportsGrantedWhenAppPackageHasEnabledListener() {
        assertEquals(
            NotificationAccessStatus.GRANTED,
            notificationAccessStatus(
                appPackageName = "com.riffle",
                enabledListenerPackages = setOf("com.riffle"),
                isListenerConnected = false,
            ),
        )
    }

    @Test
    fun debugBuildAcceptsReleasePackageAsEnabledListener() {
        assertEquals(
            NotificationAccessStatus.GRANTED,
            notificationAccessStatus(
                appPackageName = "com.riffle.debug",
                enabledListenerPackages = setOf("com.riffle"),
                isListenerConnected = false,
            ),
        )
    }

    @Test
    fun releaseBuildDoesNotAcceptDebugPackageAsEnabledListener() {
        assertEquals(
            NotificationAccessStatus.NOT_GRANTED,
            notificationAccessStatus(
                appPackageName = "com.riffle",
                enabledListenerPackages = setOf("com.riffle.debug"),
                isListenerConnected = false,
            ),
        )
    }

    @Test
    fun reportsNotGrantedWhenAppPackageDoesNotHaveEnabledListener() {
        assertEquals(
            NotificationAccessStatus.NOT_GRANTED,
            notificationAccessStatus(
                appPackageName = "com.riffle",
                enabledListenerPackages = setOf("com.other"),
                isListenerConnected = false,
            ),
        )
    }

    @Test
    fun reportsGrantedWhenListenerServiceIsConnected() {
        assertEquals(
            NotificationAccessStatus.GRANTED,
            notificationAccessStatus(
                appPackageName = "com.riffle",
                enabledListenerPackages = emptySet(),
                isListenerConnected = true,
            ),
        )
    }

    @Test
    fun prefersConnectedListenerOverMissingPackageEntry() {
        assertEquals(
            NotificationAccessStatus.GRANTED,
            notificationAccessStatus(
                appPackageName = "com.riffle",
                enabledListenerPackages = setOf("com.other"),
                isListenerConnected = true,
            ),
        )
    }

    @Test
    fun readsPackagesFromEnabledListenerComponents() {
        assertEquals(
            setOf("com.riffle.app", "com.riffle.app.debug"),
            enabledNotificationListenerPackages(
                "com.riffle.app/.launcher.notifications.RiffleNotificationListenerService:" +
                    "com.riffle.app.debug/.launcher.notifications.RiffleNotificationListenerService",
            ),
        )
    }

    @Test
    fun retainsSecureSettingsFallbackWhenNotificationManagerReadFails() {
        assertEquals(
            setOf("com.riffle.app"),
            enabledNotificationListenerPackages(
                notificationManagerPackages = { error("temporary platform failure") },
                secureSetting = { "com.riffle.app/.launcher.notifications.RiffleNotificationListenerService" },
            ),
        )
    }

    @Test
    fun retainsNotificationManagerFallbackWhenSecureSettingsReadFails() {
        assertEquals(
            setOf("com.riffle.app"),
            enabledNotificationListenerPackages(
                notificationManagerPackages = { setOf("com.riffle.app") },
                secureSetting = { error("temporary platform failure") },
            ),
        )
    }
}
