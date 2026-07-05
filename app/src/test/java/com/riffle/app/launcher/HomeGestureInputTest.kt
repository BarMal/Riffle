package com.riffle.app.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGestureInputTest {
    @Test
    fun cancelsHomeGestureRecognitionWhenNoPointersRemain() {
        assertTrue(
            shouldCancelHomeGestureRecognition(
                activePointerCount = 0,
                hasConsumedActivePointer = false,
            ),
        )
    }

    @Test
    fun cancelsHomeGestureRecognitionWhenChildGestureConsumedPointer() {
        assertTrue(
            shouldCancelHomeGestureRecognition(
                activePointerCount = 1,
                hasConsumedActivePointer = true,
            ),
        )
    }

    @Test
    fun continuesHomeGestureRecognitionForActiveUnconsumedPointers() {
        assertFalse(
            shouldCancelHomeGestureRecognition(
                activePointerCount = 1,
                hasConsumedActivePointer = false,
            ),
        )
    }
}
