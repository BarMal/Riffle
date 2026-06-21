package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSwipeGestureInterpreterTest {
    private val interpreter = HomeSwipeGestureInterpreter(thresholdPx = 80f)

    @Test
    fun interpretsSwipeUpPastThreshold() {
        assertEquals(HomeSwipeGesture.UP, interpreter.gestureFor(verticalDragPx = -80f))
    }

    @Test
    fun interpretsSwipeDownPastThreshold() {
        assertEquals(HomeSwipeGesture.DOWN, interpreter.gestureFor(verticalDragPx = 80f))
    }

    @Test
    fun ignoresDragBelowThreshold() {
        assertNull(interpreter.gestureFor(verticalDragPx = 79f))
        assertNull(interpreter.gestureFor(verticalDragPx = -79f))
    }
}
