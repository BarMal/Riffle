package com.riffle.app.launcher

import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherNotificationActionRouteTest {
    @Test
    fun routesDismissNotificationActions() {
        val action = LauncherShellAction.DismissNotifications(listOf(LauncherNotificationKey("one")))

        assertEquals(
            LauncherNotificationActionRoute.DismissNotifications(action),
            action.launcherNotificationActionRoute(),
        )
    }

    @Test
    fun ignoresNonNotificationActions() {
        assertNull(LauncherShellAction.OpenNotifications.launcherNotificationActionRoute())
        assertNull(LauncherShellAction.RequestNotificationAccess.launcherNotificationActionRoute())
        assertNull(LauncherShellAction.RefreshInstalledApps.launcherNotificationActionRoute())
    }
}
