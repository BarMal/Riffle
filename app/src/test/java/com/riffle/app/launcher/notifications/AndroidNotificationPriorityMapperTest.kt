package com.riffle.app.launcher.notifications

import android.app.Notification
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("DEPRECATION")
class AndroidNotificationPriorityMapperTest {
    @Test
    fun mapsKnownAndroidPriorities() {
        assertEquals(NotificationPriority.MIN, Notification.PRIORITY_MIN.toLauncherNotificationPriority())
        assertEquals(NotificationPriority.LOW, Notification.PRIORITY_LOW.toLauncherNotificationPriority())
        assertEquals(NotificationPriority.DEFAULT, Notification.PRIORITY_DEFAULT.toLauncherNotificationPriority())
        assertEquals(NotificationPriority.HIGH, Notification.PRIORITY_HIGH.toLauncherNotificationPriority())
        assertEquals(NotificationPriority.MAX, Notification.PRIORITY_MAX.toLauncherNotificationPriority())
    }

    @Test
    fun mapsUnknownAndroidPrioritiesToUnknown() {
        assertEquals(NotificationPriority.UNKNOWN, Int.MIN_VALUE.toLauncherNotificationPriority())
    }
}
