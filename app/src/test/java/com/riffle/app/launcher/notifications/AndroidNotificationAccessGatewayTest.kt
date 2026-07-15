package com.riffle.app.launcher.notifications

import android.os.Build
import android.provider.Settings
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNotificationAccessGatewayTest {
    @Test
    fun directsSupportedDevicesToTheListenerDetailSettingsWithRifflesComponent() {
        val componentName = "com.riffle.app/.launcher.notifications.RiffleNotificationListenerService"

        assertEquals(
            listOf(
                NotificationListenerSettingsIntentData(
                    action = Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS,
                    listenerComponentName = componentName,
                ),
                NotificationListenerSettingsIntentData(
                    action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
                ),
            ),
            notificationListenerSettingsIntentData(
                sdkInt = Build.VERSION_CODES.R,
                listenerComponentName = componentName,
            ),
        )
    }

    @Test
    fun retainsTheListenerSettingsListOnOlderDevices() {
        assertEquals(
            listOf(NotificationListenerSettingsIntentData(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
            notificationListenerSettingsIntentData(
                sdkInt = Build.VERSION_CODES.Q,
                listenerComponentName = "ignored-on-older-devices",
            ),
        )
    }

    @Test
    fun fallsBackToGenericSettingsWhenTheDetailPageCannotLaunch() {
        val launches = mutableListOf<String>()

        val launched =
            launchNotificationListenerSettings(
                candidates = listOf("detail", "generic"),
                launch = { candidate ->
                    launches += candidate
                    if (candidate == "detail") error("No detail settings activity")
                },
            )

        assertEquals(true, launched)
        assertEquals(listOf("detail", "generic"), launches)
    }

    @Test
    fun reportsSettingsUnavailableWhenEveryCandidateFailsToLaunch() {
        assertEquals(
            false,
            launchNotificationListenerSettings(
                candidates = listOf("detail", "generic"),
                launch = { error("No settings activity") },
            ),
        )
    }

    @Test
    fun reportsGrantedWhenAppPackageHasEnabledListener() {
        assertEquals(
            NotificationAccessStatus.GRANTED,
            notificationAccessStatus(
                appPackageName = "com.riffle",
                enabledListenerPackageReads = successfulPackageRead("com.riffle"),
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
                enabledListenerPackageReads = successfulPackageRead("com.riffle"),
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
                enabledListenerPackageReads = successfulPackageRead("com.riffle.debug"),
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
                enabledListenerPackageReads = successfulPackageRead("com.other"),
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
                enabledListenerPackageReads = successfulPackageRead(),
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
                enabledListenerPackageReads = successfulPackageRead("com.other"),
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
            EnabledNotificationListenerPackageReads(
                packages = setOf("com.riffle.app"),
                hasSuccessfulRead = true,
            ),
            enabledNotificationListenerPackages(
                notificationManagerPackages = { error("temporary platform failure") },
                secureSetting = { "com.riffle.app/.launcher.notifications.RiffleNotificationListenerService" },
            ),
        )
    }

    @Test
    fun retainsNotificationManagerFallbackWhenSecureSettingsReadFails() {
        assertEquals(
            EnabledNotificationListenerPackageReads(
                packages = setOf("com.riffle.app"),
                hasSuccessfulRead = true,
            ),
            enabledNotificationListenerPackages(
                notificationManagerPackages = { setOf("com.riffle.app") },
                secureSetting = { error("temporary platform failure") },
            ),
        )
    }

    @Test
    fun reportsUnknownWhenEveryPlatformSourceFails() {
        assertEquals(
            NotificationAccessStatus.UNKNOWN,
            notificationAccessStatus(
                appPackageName = "com.riffle",
                enabledListenerPackageReads =
                    EnabledNotificationListenerPackageReads(emptySet(), hasSuccessfulRead = false),
                isListenerConnected = false,
                previousStatus = NotificationAccessStatus.GRANTED,
            ),
        )
    }

    @Test
    fun reportsRevokedOnlyAfterAConfirmedGrantAndReliableAbsentRead() {
        assertEquals(
            NotificationAccessStatus.REVOKED,
            notificationAccessStatus(
                appPackageName = "com.riffle",
                enabledListenerPackageReads = successfulPackageRead(),
                isListenerConnected = false,
                previousStatus = NotificationAccessStatus.GRANTED,
            ),
        )
    }

    @Test
    fun preservesGrantedAccessWhenAnEmptyReadConflictsWithAnotherSourceFailure() {
        val reads =
            enabledNotificationListenerPackages(
                notificationManagerPackages = { emptySet() },
                secureSetting = { error("temporary platform failure") },
            )

        assertEquals(
            NotificationAccessStatus.GRANTED,
            notificationAccessStatus(
                appPackageName = "com.riffle",
                enabledListenerPackageReads = reads,
                isListenerConnected = false,
                previousStatus = NotificationAccessStatus.GRANTED,
            ),
        )
    }

    private fun successfulPackageRead(vararg packages: String) =
        EnabledNotificationListenerPackageReads(packages.toSet(), hasSuccessfulRead = true)
}
