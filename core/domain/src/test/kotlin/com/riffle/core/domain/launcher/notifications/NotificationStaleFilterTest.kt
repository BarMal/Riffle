package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationStaleFilterTest {
    private val filter = NotificationStaleFilter()
    private val nowEpochMillis = 10 * 24 * 60 * 60 * 1_000L

    @Test
    fun keepsRecentClearableNotifications() {
        val notification =
            notification(
                key = "recent",
                canDismiss = true,
                postedAtEpochMillis = nowEpochMillis - 60_000L,
            )

        assertEquals(
            listOf(notification),
            filter.activeForLauncherState(listOf(notification), nowEpochMillis = nowEpochMillis),
        )
    }

    @Test
    fun removesStaleClearableNotifications() {
        val notification =
            notification(
                key = "old",
                canDismiss = true,
                postedAtEpochMillis = nowEpochMillis - 8 * 24 * 60 * 60 * 1_000L,
            )

        assertEquals(emptyList(), filter.activeForLauncherState(listOf(notification), nowEpochMillis = nowEpochMillis))
    }

    @Test
    fun keepsStalePinnedNotifications() {
        val notification =
            notification(
                key = "ongoing",
                canDismiss = false,
                postedAtEpochMillis = nowEpochMillis - 8 * 24 * 60 * 60 * 1_000L,
            )

        assertEquals(
            listOf(notification),
            filter.activeForLauncherState(listOf(notification), nowEpochMillis = nowEpochMillis),
        )
    }

    private fun notification(
        key: String,
        canDismiss: Boolean,
        postedAtEpochMillis: Long,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName("com.riffle.app"),
            canDismiss = canDismiss,
            postedAtEpochMillis = postedAtEpochMillis,
        )
}
