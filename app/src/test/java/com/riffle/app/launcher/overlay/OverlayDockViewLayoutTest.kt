package com.riffle.app.launcher.overlay

import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayDockViewLayoutTest {
    @Test
    fun keepsCollapsedHandleTouchTargetAtLeastFortyEightDp() {
        assertEquals(
            48,
            OverlayDockSettings(handleThicknessDp = 18).collapsedHandleTouchTargetWidthDp(),
        )
    }

    @Test
    fun letsCollapsedHandleTouchTargetGrowWithVeryThickHandle() {
        assertEquals(
            72,
            OverlayDockSettings(handleThicknessDp = 72).collapsedHandleTouchTargetWidthDp(),
        )
    }
}
