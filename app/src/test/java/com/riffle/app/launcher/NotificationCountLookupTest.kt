package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCountLookupTest {
    @Test
    fun notificationCountForShortcutMatchesProfile() {
        val groups =
            listOf(
                notificationGroup(profile = AppProfile.personal(), count = 2),
                notificationGroup(profile = AppProfile.work(), count = 1),
            )

        assertEquals(2, groups.notificationCountFor(shortcut(profile = AppProfile.personal())))
        assertEquals(1, groups.notificationCountFor(shortcut(profile = AppProfile.work())))
    }

    @Test
    fun notificationCountForIdentityMatchesProfile() {
        val groups =
            listOf(
                notificationGroup(profile = AppProfile.personal(), count = 2),
                notificationGroup(profile = AppProfile.work(), count = 1),
            )

        assertEquals(2, groups.notificationCountFor(shortcut(profile = AppProfile.personal()).appIdentity))
        assertEquals(1, groups.notificationCountFor(shortcut(profile = AppProfile.work()).appIdentity))
    }

    @Test
    fun notificationCountForFolderSumsMatchingProfiles() {
        val personalShortcut = shortcut(id = "personal", profile = AppProfile.personal())
        val workShortcut = shortcut(id = "work", profile = AppProfile.work())
        val groups =
            listOf(
                notificationGroup(profile = AppProfile.personal(), count = 2),
                notificationGroup(profile = AppProfile.work(), count = 1),
            )

        val folder =
            FolderItem(
                id = LauncherItemId("folder"),
                label = "Camera",
                items = listOf(personalShortcut, workShortcut),
            )

        assertEquals(3, groups.notificationCountFor(folder))
    }

    private fun shortcut(
        id: String = "camera",
        profile: AppProfile,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.camera"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = "Camera",
        )

    private fun notificationGroup(
        profile: AppProfile,
        count: Int,
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName("com.riffle.camera"),
            profileId = profile.id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                (1..count).map { index ->
                    LauncherNotification(
                        key = LauncherNotificationKey("${profile.id.value}:$index"),
                        packageName = AppPackageName("com.riffle.camera"),
                        profileId = profile.id,
                        category = NotificationCategory.MESSAGE,
                        postedAtEpochMillis = index.toLong(),
                    )
                },
        )
}
