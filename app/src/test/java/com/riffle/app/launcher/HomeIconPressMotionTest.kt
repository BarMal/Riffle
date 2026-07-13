package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeIconPressMotionTest {
    @Test
    fun usesSubtleShrinkForStandardMotion() {
        assertEquals(HomeIconPressMotionPolicy.SHRINK, homeIconPressMotionPolicy(reducedMotion = false))
        assertEquals(0.94f, HomeIconPressMotionPolicy.SHRINK.pressedScale)
    }

    @Test
    fun keepsHomeIconsStaticForReducedMotion() {
        assertEquals(HomeIconPressMotionPolicy.NONE, homeIconPressMotionPolicy(reducedMotion = true))
        assertEquals(1f, HomeIconPressMotionPolicy.NONE.pressedScale)
    }
}
