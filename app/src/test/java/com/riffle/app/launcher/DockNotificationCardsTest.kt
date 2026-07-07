package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class DockNotificationCardsTest {
    @Test
    fun permissionPromptWinsWhenNotificationAccessIsUnavailable() {
        val state =
            dockNotificationShelfState(
                groups = listOf(notificationGroup(packageName = "com.example.chat", count = 2)),
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                apps = emptyList(),
            )

        assertEquals(
            DockNotificationShelfState.PermissionPrompt(
                label = "Notification access is not allowed",
                actionLabel = "Open notification access",
            ),
            state,
        )
    }

    @Test
    fun grantedAccessWithoutNotificationsHidesShelfCards() {
        assertEquals(
            DockNotificationShelfState.Hidden,
            dockNotificationShelfState(
                groups = emptyList(),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                apps = emptyList(),
            ),
        )
    }

    @Test
    fun grantedAccessProjectsTopThreeNotificationCardsWithMatchedApps() {
        val chat =
            notificationGroup(
                packageName = "com.example.chat",
                count = 2,
                latestCategory = NotificationCategory.MESSAGE,
            )
        val mail =
            notificationGroup(
                packageName = "com.example.mail",
                count = 1,
                latestCategory = NotificationCategory.EMAIL,
            )
        val calendar =
            notificationGroup(
                packageName = "com.example.calendar",
                count = 1,
                latestCategory = NotificationCategory.EVENT,
            )
        val maps =
            notificationGroup(
                packageName = "com.example.maps",
                count = 1,
                latestCategory = NotificationCategory.NAVIGATION,
            )
        val apps =
            listOf(
                installedApp(label = "Chat", packageName = "com.example.chat"),
                installedApp(label = "Mail", packageName = "com.example.mail"),
            )

        assertEquals(
            DockNotificationShelfState.Content(
                cards =
                    listOf(
                        DockNotificationCardState(app = apps[0], group = chat),
                        DockNotificationCardState(app = apps[1], group = mail),
                        DockNotificationCardState(app = null, group = calendar),
                    ),
            ),
            dockNotificationShelfState(
                groups = listOf(chat, mail, calendar, maps),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                apps = apps,
            ),
        )
    }

    private fun installedApp(
        label: String,
        packageName: String,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private fun notificationGroup(
        packageName: String,
        count: Int,
        latestCategory: NotificationCategory = NotificationCategory.MESSAGE,
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = AppProfile.personal().id,
            latestCategory = latestCategory,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                (1..count).map { index ->
                    LauncherNotification(
                        key = LauncherNotificationKey("$packageName:$index"),
                        packageName = AppPackageName(packageName),
                        category = latestCategory,
                        postedAtEpochMillis = index.toLong(),
                    )
                },
        )
}
