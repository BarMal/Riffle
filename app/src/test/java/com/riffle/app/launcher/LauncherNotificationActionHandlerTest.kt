package com.riffle.app.launcher

import com.riffle.app.launcher.notifications.NotificationDismissalGateway
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherNotificationActionHandlerTest {
    @Test
    fun refreshesNotificationsAfterSuccessfulDismissal() {
        val dismissedKeys = mutableListOf<List<LauncherNotificationKey>>()
        var refreshCount = 0
        val handler =
            DefaultLauncherNotificationActionHandler(
                notificationDismissalGateway =
                    NotificationDismissalGateway { keys ->
                        dismissedKeys += keys
                        true
                    },
                refreshNotifications = { refreshCount += 1 },
            )
        val keys = listOf(LauncherNotificationKey("one"), LauncherNotificationKey("two"))

        assertTrue(handler.handle(LauncherShellAction.DismissNotifications(keys)))

        assertEquals(listOf(keys), dismissedKeys)
        assertEquals(1, refreshCount)
    }

    @Test
    fun skipsRefreshAfterFailedDismissal() {
        var refreshCount = 0
        val handler =
            DefaultLauncherNotificationActionHandler(
                notificationDismissalGateway = NotificationDismissalGateway { false },
                refreshNotifications = { refreshCount += 1 },
            )

        assertTrue(handler.handle(LauncherShellAction.DismissNotifications(listOf(LauncherNotificationKey("one")))))

        assertEquals(0, refreshCount)
    }

    @Test
    fun ignoresNonNotificationActions() {
        val handler =
            DefaultLauncherNotificationActionHandler(
                notificationDismissalGateway = NotificationDismissalGateway { true },
                refreshNotifications = {},
            )

        assertFalse(handler.handle(LauncherShellAction.OpenSettings))
    }
}
