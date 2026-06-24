package com.riffle.app.launcher

import android.view.HapticFeedbackConstants
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherHapticsTest {
    @Test
    fun mapsConfiguredStrengthsToLongPressFeedbackConstants() {
        assertNull(HapticFeedbackStrength.OFF.longPressHapticFeedbackConstant())
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackStrength.LIGHT.longPressHapticFeedbackConstant())
        assertEquals(HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackStrength.MEDIUM.longPressHapticFeedbackConstant())
        assertEquals(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackStrength.STRONG.longPressHapticFeedbackConstant())
    }
}
