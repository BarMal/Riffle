package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNotificationAccessGatewayTest {
    @Test
    fun reportsGrantedWhenAppPackageHasEnabledListener() {
        assertEquals(
            NotificationAccessStatus.GRANTED,
            notificationAccessStatusFromEnabledListenerPackages(
                appPackageName = "com.riffle",
                enabledListenerPackages = setOf("com.riffle"),
            ),
        )
    }

    @Test
    fun reportsNotGrantedWhenAppPackageDoesNotHaveEnabledListener() {
        assertEquals(
            NotificationAccessStatus.NOT_GRANTED,
            notificationAccessStatusFromEnabledListenerPackages(
                appPackageName = "com.riffle",
                enabledListenerPackages = setOf("com.other"),
            ),
        )
    }
}
