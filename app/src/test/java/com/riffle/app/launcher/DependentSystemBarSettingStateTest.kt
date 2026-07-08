package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DependentSystemBarSettingStateTest {
    @Test
    fun disablesSystemBarSettingWhileFullscreenHomeIsEnabled() {
        val state =
            dependentSystemBarSettingState(
                fullscreenHome = true,
                hidden = true,
                enabledSubtitle = "Hide the top system bar on home",
            )

        assertTrue(state.checked)
        assertFalse(state.enabled)
        assertEquals("Turn off Fullscreen home to choose bars separately", state.subtitle)
    }

    @Test
    fun keepsIndependentSystemBarSettingAvailableOutsideFullscreenHome() {
        val state =
            dependentSystemBarSettingState(
                fullscreenHome = false,
                hidden = false,
                enabledSubtitle = "Hide the bottom system bar on home",
            )

        assertFalse(state.checked)
        assertTrue(state.enabled)
        assertEquals("Hide the bottom system bar on home", state.subtitle)
    }
}
