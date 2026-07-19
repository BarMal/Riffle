package com.riffle.app.launcher

import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.app.launcher.notifications.NotificationStageActionGateway
import com.riffle.app.launcher.notifications.NotificationStageActionResult
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationShellActionsTest {
    @Test
    fun `refreshes only after focused stage action succeeds`() {
        var refreshes = 0
        var receivedKey: LauncherNotificationKey? = null
        val handler =
            DefaultLauncherNotificationActionHandler(
                notificationDismissalGateway = { true },
                refreshNotifications = { refreshes++ },
                stageActionGateway =
                    NotificationStageActionGateway { key, _ ->
                        receivedKey = key
                        NotificationStageActionResult.Performed
                    },
            )

        val handled =
            handler.handle(
                LauncherShellAction.PerformNotificationStageAction(
                    LauncherNotificationKey("focused"),
                    NotificationStageAction.Open,
                ),
            )

        assertTrue(handled)
        assertEquals(LauncherNotificationKey("focused"), receivedKey)
        assertEquals(1, refreshes)
    }

    @Test
    fun `unavailable stage action does not refresh`() {
        var refreshes = 0
        val handler =
            DefaultLauncherNotificationActionHandler(
                notificationDismissalGateway = { true },
                refreshNotifications = { refreshes++ },
                stageActionGateway =
                    NotificationStageActionGateway { _, _ -> NotificationStageActionResult.Unavailable },
            )

        val handled =
            handler.handle(
                LauncherShellAction.PerformNotificationStageAction(
                    LauncherNotificationKey("removed"),
                    NotificationStageAction.Open,
                ),
            )

        assertFalse(handled)
        assertEquals(0, refreshes)
    }

    @Test
    fun `unauthorized stage action does not reach the gateway`() {
        var invoked = false
        val handler =
            DefaultLauncherNotificationActionHandler(
                notificationDismissalGateway = { true },
                refreshNotifications = {},
                canPerformStageAction = { false },
                stageActionGateway =
                    NotificationStageActionGateway { _, _ ->
                        invoked = true
                        NotificationStageActionResult.Performed
                    },
            )

        assertFalse(
            handler.handle(
                LauncherShellAction.PerformNotificationStageAction(
                    LauncherNotificationKey("quiet"),
                    NotificationStageAction.Open,
                ),
            ),
        )
        assertFalse(invoked)
    }
}
