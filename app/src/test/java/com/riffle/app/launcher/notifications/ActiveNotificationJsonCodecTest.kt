package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveNotificationJsonCodecTest {
    @Test
    fun roundTripsActiveNotifications() {
        val notifications =
            listOf(
                LauncherNotification(
                    key = LauncherNotificationKey("camera-1"),
                    packageName = AppPackageName("com.riffle.camera"),
                    postedAtEpochMillis = 1_000L,
                ),
            )

        assertEquals(notifications, decodeActiveNotifications(encodeActiveNotifications(notifications)))
    }

    @Test
    fun decodesEmptyNotificationList() {
        assertEquals(emptyList<LauncherNotification>(), decodeActiveNotifications("[]"))
    }
}
