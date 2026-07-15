package com.riffle.app.launcher.overlay

import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidOverlayDockPermissionGatewayTest {
    @Test
    fun reportsGrantedWhenCanDrawOverlays() {
        assertEquals(
            OverlayDockPermissionStatus.GRANTED,
            overlayDockPermissionStatus(canDrawOverlays = true),
        )
    }

    @Test
    fun reportsNotGrantedWhenCannotDrawOverlays() {
        assertEquals(
            OverlayDockPermissionStatus.NOT_GRANTED,
            overlayDockPermissionStatus(canDrawOverlays = false),
        )
    }

    @Test
    fun reportsUnknownWhenPlatformOverlayReadFails() {
        assertEquals(
            OverlayDockPermissionStatus.UNKNOWN,
            overlayDockPermissionStatus { error("temporary platform failure") },
        )
    }
}
