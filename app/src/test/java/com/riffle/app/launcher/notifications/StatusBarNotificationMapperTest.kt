package com.riffle.app.launcher.notifications

import android.app.Notification
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

@Suppress("DEPRECATION")
class StatusBarNotificationMapperTest {
    private val mapper = StatusBarNotificationMapper(profileIdForUser = { AppProfile.personal().id })

    @Test
    fun mapsPlatformNotificationSnapshotToLauncherNotification() {
        val notification =
            mapper.map(
                StatusBarNotificationSnapshot(
                    key = "work-mail-key",
                    packageName = "com.riffle.mail",
                    profileId = AppProfileId("user:10"),
                    category = Notification.CATEGORY_EMAIL,
                    priority = Notification.PRIORITY_HIGH,
                    canDismiss = true,
                    title = "Work mail",
                    text = "Inbox zero is gone",
                    largeIconPngBase64 = "png-data",
                    postedAtEpochMillis = 42L,
                ),
            )

        assertEquals(LauncherNotificationKey("work-mail-key"), notification.key)
        assertEquals(AppPackageName("com.riffle.mail"), notification.packageName)
        assertEquals(AppProfileId("user:10"), notification.profileId)
        assertNotEquals(AppProfile.personal().id, notification.profileId)
        assertEquals(NotificationCategory.EMAIL, notification.category)
        assertEquals(NotificationPriority.HIGH, notification.priority)
        assertEquals(true, notification.canDismiss)
        assertEquals("Work mail", notification.title)
        assertEquals("Inbox zero is gone", notification.text)
        assertEquals("png-data", notification.largeIconPngBase64)
        assertEquals(42L, notification.postedAtEpochMillis)
    }

    @Test
    fun defaultsLegacyNotificationSnapshotsToPersonalProfile() {
        val notification =
            mapper.map(
                StatusBarNotificationSnapshot(
                    key = "legacy-key",
                    packageName = "com.riffle.camera",
                    postedAtEpochMillis = 1_000L,
                ),
            )

        assertEquals(AppProfile.personal().id, notification.profileId)
    }

    @Test
    fun preservesPrivateProfileIdsFromPlatformData() {
        val notification =
            mapper.map(
                StatusBarNotificationSnapshot(
                    key = "private-key",
                    packageName = "com.riffle.vault",
                    profileId = AppProfileId("user:20"),
                    postedAtEpochMillis = 1_000L,
                ),
            )

        assertEquals(AppProfileId("user:20"), notification.profileId)
        assertNotEquals(AppProfile.personal().id, notification.profileId)
    }
}
