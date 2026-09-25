package com.riffle.core.domain.launcher.gestures

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockSwipeUpDecisionTest {
    @Test
    fun swipeUpTriggersAtItsDpThresholdResolvedToPixels() {
        // 30.5dp at 2.625x: the historical 80px.
        assertFalse(dockSwipeUpTriggered(accumulatedVerticalDragPx = -79f))
        assertTrue(dockSwipeUpTriggered(accumulatedVerticalDragPx = -81f))
        // A denser display needs more pixels for the same physical swipe.
        assertFalse(dockSwipeUpTriggered(accumulatedVerticalDragPx = -81f, thresholdPx = 30.5f * 3.5f))
        // Downward travel never triggers a swipe-up.
        assertFalse(dockSwipeUpTriggered(accumulatedVerticalDragPx = 200f))
    }
}
