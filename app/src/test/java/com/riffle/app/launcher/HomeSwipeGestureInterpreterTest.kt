package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSwipeGestureInterpreterTest {
    private val interpreter = HomeSwipeGestureInterpreter(thresholdPx = 80f)

    @Test
    fun interpretsSwipeUpPastThreshold() {
        assertEquals(HomeSwipeGesture.UP, interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = -80f))
    }

    @Test
    fun interpretsSwipeDownPastThreshold() {
        assertEquals(HomeSwipeGesture.DOWN, interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = 80f))
    }

    @Test
    fun interpretsSwipeLeftPastThreshold() {
        assertEquals(HomeSwipeGesture.LEFT, interpreter.gestureFor(horizontalDragPx = -80f, verticalDragPx = 0f))
    }

    @Test
    fun interpretsSwipeRightPastThreshold() {
        assertEquals(HomeSwipeGesture.RIGHT, interpreter.gestureFor(horizontalDragPx = 80f, verticalDragPx = 0f))
    }

    @Test
    fun usesDominantDragAxis() {
        assertEquals(HomeSwipeGesture.LEFT, interpreter.gestureFor(horizontalDragPx = -120f, verticalDragPx = 90f))
        assertEquals(HomeSwipeGesture.DOWN, interpreter.gestureFor(horizontalDragPx = -90f, verticalDragPx = 120f))
    }

    @Test
    fun ignoresDragBelowThreshold() {
        assertNull(interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = 79f))
        assertNull(interpreter.gestureFor(horizontalDragPx = 0f, verticalDragPx = -79f))
        assertNull(interpreter.gestureFor(horizontalDragPx = 79f, verticalDragPx = 0f))
        assertNull(interpreter.gestureFor(horizontalDragPx = -79f, verticalDragPx = 0f))
    }
}
