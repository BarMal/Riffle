package com.riffle.app.launcher.overlay

import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayDockServiceControllerTest {
    @Test
    fun startsOverlayDockWhenEnabledAndPermissionGranted() {
        assertEquals(
            OverlayDockServiceCommand.START,
            overlayDockServiceCommand(
                settings = LauncherSettings(overlayDock = OverlayDockSettings(enabled = true)),
                permissionStatus = OverlayDockPermissionStatus.GRANTED,
            ),
        )
    }

    @Test
    fun stopsOverlayDockWhenDisabled() {
        assertEquals(
            OverlayDockServiceCommand.STOP,
            overlayDockServiceCommand(
                settings = LauncherSettings(overlayDock = OverlayDockSettings(enabled = false)),
                permissionStatus = OverlayDockPermissionStatus.GRANTED,
            ),
        )
    }

    @Test
    fun stopsOverlayDockWhenPermissionIsMissing() {
        assertEquals(
            OverlayDockServiceCommand.STOP,
            overlayDockServiceCommand(
                settings = LauncherSettings(overlayDock = OverlayDockSettings(enabled = true)),
                permissionStatus = OverlayDockPermissionStatus.NOT_GRANTED,
            ),
        )
    }

    @Test
    fun stopsOverlayDockWhenPermissionStatusIsUnknown() {
        assertEquals(
            OverlayDockServiceCommand.STOP,
            overlayDockServiceCommand(
                settings = LauncherSettings(overlayDock = OverlayDockSettings(enabled = true)),
                permissionStatus = OverlayDockPermissionStatus.UNKNOWN,
            ),
        )
    }
}
