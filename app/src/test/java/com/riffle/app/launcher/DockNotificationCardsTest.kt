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
                showNotificationCards = true,
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
    fun revokedAccessShowsRevokedPermissionPrompt() {
        val state =
            dockNotificationShelfState(
                showNotificationCards = true,
                groups = listOf(notificationGroup(packageName = "com.example.chat", count = 2)),
                notificationAccessStatus = NotificationAccessStatus.REVOKED,
                apps = emptyList(),
            )

        assertEquals(
            DockNotificationShelfState.PermissionPrompt(
                label = "Notification access was revoked",
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
                showNotificationCards = true,
                groups = emptyList(),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                apps = emptyList(),
            ),
        )
    }

    @Test
    fun hiddenWhenDockNotificationCardsAreDisabled() {
        assertEquals(
            DockNotificationShelfState.Hidden,
            dockNotificationShelfState(
                showNotificationCards = false,
                groups = listOf(notificationGroup(packageName = "com.example.chat")),
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
                showNotificationCards = true,
                groups = listOf(chat, mail, calendar, maps),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                apps = apps,
            ),
        )
    }

    @Test
    fun clearableCardsExposeDismissNotificationsAction() {
        val group =
            notificationGroup(
                packageName = "com.example.chat",
                notifications =
                    listOf(
                        notification(packageName = "com.example.chat", key = "chat:1", canDismiss = true),
                        notification(packageName = "com.example.chat", key = "chat:2", canDismiss = false),
                        notification(packageName = "com.example.chat", key = "chat:3", canDismiss = true),
                    ),
            )

        assertEquals(
            LauncherShellAction.DismissNotifications(
                listOf(
                    LauncherNotificationKey("chat:1"),
                    LauncherNotificationKey("chat:3"),
                ),
            ),
            DockNotificationCardState(app = null, group = group).clearAction,
        )
    }

    @Test
    fun pinnedCardsDoNotExposeClearAction() {
        val group =
            notificationGroup(
                packageName = "com.example.chat",
                notifications =
                    listOf(
                        notification(packageName = "com.example.chat", key = "chat:1", canDismiss = false),
                    ),
            )

        assertEquals(null, DockNotificationCardState(app = null, group = group).clearAction)
    }

    @Test
    fun summaryMatchesNewInlineClearAction() {
        assertEquals(
            "Tap to open or clear notification",
            dockNotificationCardSummary(
                group =
                    notificationGroup(
                        packageName = "com.example.chat",
                        notifications =
                            listOf(
                                notification(packageName = "com.example.chat", key = "chat:1", canDismiss = true),
                            ),
                    ),
                canLaunchApp = true,
            ),
        )
    }

    @Test
    fun summaryUsesOpenCopyForPinnedLaunchableCards() {
        assertEquals(
            "Tap to open",
            dockNotificationCardSummary(
                group =
                    notificationGroup(
                        packageName = "com.example.chat",
                        notifications =
                            listOf(
                                notification(packageName = "com.example.chat", key = "chat:1", canDismiss = false),
                            ),
                    ),
                canLaunchApp = true,
            ),
        )
    }

    @Test
    fun summaryDoesNotPromiseOpenWhenAppIsUnavailable() {
        assertEquals(
            "Clear notification",
            dockNotificationCardSummary(
                group =
                    notificationGroup(
                        packageName = "com.example.chat",
                        notifications =
                            listOf(
                                notification(packageName = "com.example.chat", key = "chat:1", canDismiss = true),
                            ),
                    ),
                canLaunchApp = false,
            ),
        )
    }

    @Test
    fun summaryUsesSingularPinnedCopyWhenAppIsUnavailable() {
        assertEquals(
            "Pinned notification",
            dockNotificationCardSummary(
                group =
                    notificationGroup(
                        packageName = "com.example.chat",
                        notifications =
                            listOf(
                                notification(packageName = "com.example.chat", key = "chat:1", canDismiss = false),
                            ),
                    ),
                canLaunchApp = false,
            ),
        )
    }

    @Test
    fun summaryExplainsPartialClearabilityWithoutLaunchAction() {
        assertEquals(
            "1 clearable of 2 notifications",
            dockNotificationCardSummary(
                group =
                    notificationGroup(
                        packageName = "com.example.chat",
                        notifications =
                            listOf(
                                notification(packageName = "com.example.chat", key = "chat:1", canDismiss = true),
                                notification(packageName = "com.example.chat", key = "chat:2", canDismiss = false),
                            ),
                    ),
                canLaunchApp = false,
            ),
        )
    }

    @Test
    fun summaryExplainsPartialClearabilityWithLaunchAction() {
        assertEquals(
            "Tap to open - 1 clearable of 2 notifications",
            dockNotificationCardSummary(
                group =
                    notificationGroup(
                        packageName = "com.example.chat",
                        notifications =
                            listOf(
                                notification(packageName = "com.example.chat", key = "chat:1", canDismiss = true),
                                notification(packageName = "com.example.chat", key = "chat:2", canDismiss = false),
                            ),
                    ),
                canLaunchApp = true,
            ),
        )
    }

    @Test
    fun cardContentDescriptionIncludesCountStateAndActionHint() {
        assertEquals(
            "Chat. 2 notifications. Message, Recent. Tap to open - 1 clearable of 2 notifications",
            dockNotificationCardContentDescription(
                card =
                    DockNotificationCardState(
                        app = installedApp(label = "Chat", packageName = "com.example.chat"),
                        group =
                            notificationGroup(
                                packageName = "com.example.chat",
                                notifications =
                                    listOf(
                                        notification(
                                            packageName = "com.example.chat",
                                            key = "chat:1",
                                            canDismiss = true,
                                        ),
                                        notification(
                                            packageName = "com.example.chat",
                                            key = "chat:2",
                                            canDismiss = false,
                                        ),
                                    ),
                            ),
                    ),
                label = "Chat",
            ),
        )
    }

    @Test
    fun cardContentDescriptionUsesSingularNotificationCount() {
        assertEquals(
            "Chat. 1 notification. Message, Recent. Tap to open or clear notification",
            dockNotificationCardContentDescription(
                card =
                    DockNotificationCardState(
                        app = installedApp(label = "Chat", packageName = "com.example.chat"),
                        group =
                            notificationGroup(
                                packageName = "com.example.chat",
                                notifications =
                                    listOf(
                                        notification(
                                            packageName = "com.example.chat",
                                            key = "chat:1",
                                            canDismiss = true,
                                        ),
                                    ),
                            ),
                    ),
                label = "Chat",
            ),
        )
    }

    @Test
    fun permissionPromptContentDescriptionIncludesActionLabel() {
        assertEquals(
            "Notifications. Notification access was revoked. Open notification access",
            dockNotificationPermissionPromptContentDescription(
                label = "Notification access was revoked",
                actionLabel = "Open notification access",
            ),
        )
    }

    @Test
    fun clearButtonContentDescriptionUsesSingularCopyForSingleClearableNotification() {
        assertEquals(
            "Clear Chat notification",
            dockNotificationClearContentDescription(
                label = "Chat",
                clearableCount = 1,
            ),
        )
    }

    @Test
    fun clearButtonContentDescriptionUsesPluralCopyForMultipleClearableNotifications() {
        assertEquals(
            "Clear Chat notifications",
            dockNotificationClearContentDescription(
                label = "Chat",
                clearableCount = 2,
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
        count: Int = 1,
        latestCategory: NotificationCategory = NotificationCategory.MESSAGE,
        notifications: List<LauncherNotification> =
            (1..count).map { index ->
                notification(
                    packageName = packageName,
                    key = "$packageName:$index",
                    category = latestCategory,
                )
            },
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = AppProfile.personal().id,
            latestCategory = latestCategory,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications = notifications,
        )

    private fun notification(
        packageName: String,
        key: String,
        category: NotificationCategory = NotificationCategory.MESSAGE,
        canDismiss: Boolean = false,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            category = category,
            canDismiss = canDismiss,
            postedAtEpochMillis = 1L,
        )
}
