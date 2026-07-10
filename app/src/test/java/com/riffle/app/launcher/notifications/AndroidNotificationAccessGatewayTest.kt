package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNotificationAccessGatewayTest {
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
}
