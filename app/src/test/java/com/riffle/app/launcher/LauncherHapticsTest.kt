package com.riffle.app.launcher

import android.os.Build
import android.view.HapticFeedbackConstants
import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherHapticsTest {
    @Test
    fun mapsConfiguredStrengthsToLongPressFeedbackConstants() {
        assertNull(HapticFeedbackStrength.OFF.longPressHapticFeedbackConstant())
        assertEquals(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackStrength.LIGHT.longPressHapticFeedbackConstant(),
        )
        assertEquals(
            HapticFeedbackConstants.CONTEXT_CLICK,
            HapticFeedbackStrength.MEDIUM.longPressHapticFeedbackConstant(),
        )
        assertEquals(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackStrength.STRONG.longPressHapticFeedbackConstant(),
        )
    }

    @Test
    fun mapsSemanticEventsToModernConstantsOnApi34() {
        val sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        assertEquals(
            HapticFeedbackConstants.GESTURE_START,
            LauncherHapticEvent.GESTURE_START.hapticFeedbackConstant(sdk),
        )
        assertEquals(HapticFeedbackConstants.GESTURE_END, LauncherHapticEvent.GESTURE_END.hapticFeedbackConstant(sdk))
        assertEquals(
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK,
            LauncherHapticEvent.DETENT.hapticFeedbackConstant(sdk),
        )
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, LauncherHapticEvent.BOUNDARY.hapticFeedbackConstant(sdk))
        assertEquals(HapticFeedbackConstants.CONFIRM, LauncherHapticEvent.COMMIT.hapticFeedbackConstant(sdk))
        assertEquals(HapticFeedbackConstants.REJECT, LauncherHapticEvent.CANCEL.hapticFeedbackConstant(sdk))
    }

    @Test
    fun fallsBackToApi28ConstantsBeforeTheGestureAndSegmentConstantsExist() {
        val sdk = Build.VERSION_CODES.P

        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, LauncherHapticEvent.GESTURE_START.hapticFeedbackConstant(sdk))
        assertEquals(
            HapticFeedbackConstants.VIRTUAL_KEY_RELEASE,
            LauncherHapticEvent.GESTURE_END.hapticFeedbackConstant(sdk),
        )
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, LauncherHapticEvent.DETENT.hapticFeedbackConstant(sdk))
        assertEquals(HapticFeedbackConstants.CONTEXT_CLICK, LauncherHapticEvent.BOUNDARY.hapticFeedbackConstant(sdk))
        assertEquals(HapticFeedbackConstants.CONTEXT_CLICK, LauncherHapticEvent.COMMIT.hapticFeedbackConstant(sdk))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, LauncherHapticEvent.CANCEL.hapticFeedbackConstant(sdk))
    }

    @Test
    fun usesGestureConstantsButSegmentFallbacksOnApi30() {
        val sdk = Build.VERSION_CODES.R

        assertEquals(HapticFeedbackConstants.CONFIRM, LauncherHapticEvent.COMMIT.hapticFeedbackConstant(sdk))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, LauncherHapticEvent.DETENT.hapticFeedbackConstant(sdk))
    }
}
