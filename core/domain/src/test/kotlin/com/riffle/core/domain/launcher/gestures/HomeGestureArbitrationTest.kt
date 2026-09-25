package com.riffle.core.domain.launcher.gestures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeGestureArbitrationTest {
    @Test
    fun endsWhenNoPointersRemain() {
        assertEquals(HomeGestureDisposition.END, HomeGestureArbiter().dispositionFor(0, false))
    }

    @Test
    fun tracksActiveUnconsumedPointers() {
        val arbiter = HomeGestureArbiter()

        assertFalse(arbiter.shouldClaim(pressedPointerCount = 1))
        assertEquals(HomeGestureDisposition.TRACK, arbiter.dispositionFor(1, false))
        assertTrue(arbiter.mayFire)
    }

    @Test
    fun yieldsForTheRestOfTheTouchOnceAChildConsumed() {
        val arbiter = HomeGestureArbiter()

        assertEquals(HomeGestureDisposition.YIELDED, arbiter.dispositionFor(1, true))
        // The child stops consuming (e.g. the stack reached its end): the touch is still the child's.
        assertEquals(HomeGestureDisposition.YIELDED, arbiter.dispositionFor(1, false))
        assertFalse(arbiter.mayFire)
    }

    @Test
    fun twoFingersOverAConsumingChildStayWithTheChild() {
        val arbiter = HomeGestureArbiter()
        arbiter.dispositionFor(1, true)

        assertFalse(arbiter.shouldClaim(pressedPointerCount = 2))
        assertEquals(HomeGestureDisposition.YIELDED, arbiter.dispositionFor(2, true))
    }

    @Test
    fun aThirdFingerClaimsTheTouchEvenAfterAChildTookIt() {
        val arbiter = HomeGestureArbiter()
        arbiter.dispositionFor(1, true)

        assertTrue(arbiter.shouldClaim(pressedPointerCount = 3))
        // The consumption the home layer just did itself must not read as a child's claim.
        assertEquals(HomeGestureDisposition.TRACK, arbiter.dispositionFor(3, true))
        assertTrue(arbiter.mayFire)
    }

    @Test
    fun aClaimHoldsWhileAnyFingerRemainsDown() {
        val arbiter = HomeGestureArbiter()
        arbiter.shouldClaim(pressedPointerCount = 3)

        assertTrue(arbiter.shouldClaim(pressedPointerCount = 2))
        assertFalse(arbiter.shouldClaim(pressedPointerCount = 0))
    }

    @Test
    fun handOffWaitsForTheThreshold() {
        val tracker = OverscrollHandOffTracker()

        assertNull(tracker.onPostScroll(consumedPx = 10f, availablePx = -30f, thresholdPx = 80f))
        assertNull(tracker.onPostScroll(consumedPx = 0f, availablePx = -40f, thresholdPx = 80f))
        assertEquals(-85f, tracker.onPostScroll(consumedPx = 0f, availablePx = -15f, thresholdPx = 80f))
    }

    @Test
    fun handOffFiresOncePerDrag() {
        val tracker = OverscrollHandOffTracker()

        assertEquals(-90f, tracker.onPostScroll(consumedPx = 0f, availablePx = -90f, thresholdPx = 80f))
        assertNull(tracker.onPostScroll(consumedPx = 0f, availablePx = -90f, thresholdPx = 80f))

        tracker.reset()
        assertEquals(90f, tracker.onPostScroll(consumedPx = 0f, availablePx = 90f, thresholdPx = 80f))
    }

    @Test
    fun aDragThatStartsPinnedGetsTheWithheldSlopBack() {
        val tracker = OverscrollHandOffTracker()

        // 60px reported past a 21px slop is 81px of finger travel: past an 80px threshold.
        assertEquals(
            -81f,
            tracker.onPostScroll(consumedPx = 0f, availablePx = -60f, thresholdPx = 80f, touchSlopPx = 21f),
        )
    }

    @Test
    fun aDragThatMovedTheStackFirstGetsNoSlopCredit() {
        val tracker = OverscrollHandOffTracker()

        assertNull(
            tracker.onPostScroll(consumedPx = -40f, availablePx = -60f, thresholdPx = 80f, touchSlopPx = 21f),
        )
    }

    @Test
    fun nothingLeftOverMeansNoHandOff() {
        val tracker = OverscrollHandOffTracker()

        assertNull(
            tracker.onPostScroll(consumedPx = -500f, availablePx = 0f, thresholdPx = 80f, touchSlopPx = 21f),
        )
    }
}
