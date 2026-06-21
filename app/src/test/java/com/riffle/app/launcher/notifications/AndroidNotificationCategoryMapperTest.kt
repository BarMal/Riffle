package com.riffle.app.launcher.notifications

import android.app.Notification
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNotificationCategoryMapperTest {
    @Test
    fun mapsKnownAndroidCategories() {
        assertEquals(NotificationCategory.MESSAGE, Notification.CATEGORY_MESSAGE.toLauncherNotificationCategory())
        assertEquals(NotificationCategory.EMAIL, Notification.CATEGORY_EMAIL.toLauncherNotificationCategory())
        assertEquals(NotificationCategory.CALL, Notification.CATEGORY_CALL.toLauncherNotificationCategory())
        assertEquals(NotificationCategory.ALARM, Notification.CATEGORY_ALARM.toLauncherNotificationCategory())
    }

    @Test
    fun mapsNullOrUnknownAndroidCategoriesToUnknown() {
        assertEquals(NotificationCategory.UNKNOWN, null.toLauncherNotificationCategory())
        assertEquals(NotificationCategory.UNKNOWN, "custom-category".toLauncherNotificationCategory())
    }
}
