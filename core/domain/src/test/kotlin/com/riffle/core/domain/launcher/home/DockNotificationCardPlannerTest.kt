package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class DockNotificationCardPlannerTest {
    private val planner = DockNotificationCardPlanner()

    @Test
    fun unknownAccessReturnsNotCheckedFallback() {
        assertEquals(
            DockNotificationCardDeckState.PermissionFallback(DockNotificationPermissionFallbackReason.NOT_CHECKED),
            planner.plan(
                groups = listOf(group("com.example.chat")),
                notificationAccessStatus = NotificationAccessStatus.UNKNOWN,
            ),
        )
    }

    @Test
    fun deniedAccessReturnsDeniedFallbackEvenWhenGroupsExist() {
        assertEquals(
            DockNotificationCardDeckState.PermissionFallback(DockNotificationPermissionFallbackReason.DENIED),
            planner.plan(
                groups = listOf(group("com.example.chat")),
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
            ),
        )
    }

    @Test
    fun revokedAccessReturnsRevokedFallback() {
        assertEquals(
            DockNotificationCardDeckState.PermissionFallback(DockNotificationPermissionFallbackReason.REVOKED),
            planner.plan(
                groups = listOf(group("com.example.chat")),
                notificationAccessStatus = NotificationAccessStatus.REVOKED,
            ),
        )
    }

    @Test
    fun grantedAccessWithoutGroupsHidesCards() {
        assertEquals(
            DockNotificationCardDeckState.Hidden,
            planner.plan(groups = emptyList(), notificationAccessStatus = NotificationAccessStatus.GRANTED),
        )
    }

    @Test
    fun grantedAccessProjectsCompactCardModels() {
        val chat = group("com.example.chat", count = 2, clearableCount = 1, category = NotificationCategory.MESSAGE)
        val mail = group("com.example.mail", count = 1, clearableCount = 0, category = NotificationCategory.EMAIL)
        val calendar =
            group("com.example.calendar", count = 1, clearableCount = 1, category = NotificationCategory.EVENT)
        val maps = group("com.example.maps", count = 1, clearableCount = 1, category = NotificationCategory.NAVIGATION)

        assertEquals(
            DockNotificationCardDeckState.Content(
                cards =
                    listOf(
                        card(
                            "com.example.chat",
                            count = 2,
                            clearableCount = 1,
                            category = NotificationCategory.MESSAGE,
                        ),
                        card(
                            "com.example.mail",
                            count = 1,
                            clearableCount = 0,
                            category = NotificationCategory.EMAIL,
                        ),
                        card(
                            "com.example.calendar",
                            count = 1,
                            clearableCount = 1,
                            category = NotificationCategory.EVENT,
                        ),
                    ),
            ),
            planner.plan(
                groups = listOf(chat, mail, calendar, maps),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
            ),
        )
    }

    @Test
    fun nonPositiveCardLimitHidesCards() {
        assertEquals(
            DockNotificationCardDeckState.Hidden,
            planner.plan(
                groups = listOf(group("com.example.chat")),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                maxCards = 0,
            ),
        )
    }

    private fun group(
        packageName: String,
        count: Int = 1,
        clearableCount: Int = count,
        category: NotificationCategory = NotificationCategory.MESSAGE,
    ): AppNotificationGroup {
        val notifications =
            (1..count).map { index ->
                LauncherNotification(
                    key = LauncherNotificationKey("$packageName:$index"),
                    packageName = AppPackageName(packageName),
                    category = category,
                    canDismiss = index <= clearableCount,
                    postedAtEpochMillis = index.toLong(),
                )
            }

        return AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = AppProfile.personal().id,
            latestCategory = category,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications = notifications,
        )
    }

    private fun card(
        packageName: String,
        count: Int,
        clearableCount: Int,
        category: NotificationCategory,
    ): DockNotificationCardModel =
        DockNotificationCardModel(
            key =
                AppNotificationGroupKey(
                    packageName = AppPackageName(packageName),
                    profileId = AppProfile.personal().id,
                ),
            count = count,
            clearableCount = clearableCount,
            latestCategory = category,
            latestAgeBucket = NotificationAgeBucket.RECENT,
        )
}
