package com.riffle.core.domain.launcher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeGesturePagerTest {
    @Test
    fun theOneFingerSwipesAlongThePagesBelongToThePager() {
        assertEquals(PageScrollDirection.HORIZONTAL, HomeGesture.ONE_FINGER_LEFT.pagerScrollDirection)
        assertEquals(PageScrollDirection.HORIZONTAL, HomeGesture.ONE_FINGER_RIGHT.pagerScrollDirection)
        assertEquals(PageScrollDirection.VERTICAL, HomeGesture.ONE_FINGER_UP.pagerScrollDirection)
        assertEquals(PageScrollDirection.VERTICAL, HomeGesture.ONE_FINGER_DOWN.pagerScrollDirection)
    }

    @Test
    fun multiFingerSwipesAndPinchesAreNeverTheirs() {
        val others =
            HomeGesture.entries -
                setOf(
                    HomeGesture.ONE_FINGER_LEFT,
                    HomeGesture.ONE_FINGER_RIGHT,
                    HomeGesture.ONE_FINGER_UP,
                    HomeGesture.ONE_FINGER_DOWN,
                )
        others.forEach { gesture -> assertNull("$gesture", gesture.pagerScrollDirection) }
    }
}
