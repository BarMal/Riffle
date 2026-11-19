package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.onboarding.OnboardingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tour pictures' motion as maths: each loop starts and ends with nothing under the finger, and stills stand in. */
class TourMotionTest {
    private val samples = (0..100).map { it / 100f }

    @Test
    fun theDockLibraryAndStreamStepsShowAGestureAndHomeShowsNone() {
        assertNull(TourGesture.of(OnboardingStep.HOME))
        assertEquals(TourGesture.PRESS, TourGesture.of(OnboardingStep.DOCK))
        assertEquals(TourGesture.PULL, TourGesture.of(OnboardingStep.LIBRARY))
        assertEquals(TourGesture.HOLD_SLIDE, TourGesture.of(OnboardingStep.STREAM))
    }

    @Test
    fun everyFrameStaysInsideTheUnitSquareAndTheUnitRanges() {
        TourGesture.entries.forEach { gesture ->
            samples.forEach { t ->
                val frame = TourMotion.frame(gesture, t)
                assertTrue("$gesture $t finger", frame.finger.x in 0f..1f && frame.finger.y in 0f..1f)
                assertTrue("$gesture $t alpha", frame.fingerAlpha in 0f..1f)
                assertTrue("$gesture $t dip", frame.dockDip in 0f..1f)
                assertTrue("$gesture $t reveal", frame.reveal in 0f..1f)
                assertTrue("$gesture $t ring", frame.ringIndex in -1..2)
            }
        }
    }

    @Test
    fun aLoopStartsAndEndsWithNoFingerSoTheRestartIsNotSeen() {
        TourGesture.entries.forEach { gesture ->
            assertEquals("$gesture start", 0f, TourMotion.frame(gesture, 0f).fingerAlpha, 0f)
            assertEquals("$gesture end", 0f, TourMotion.frame(gesture, 1f).fingerAlpha, 0f)
        }
    }

    @Test
    fun theLoopsMeetWhereTheyRestart() {
        TourGesture.entries.forEach { gesture ->
            val start = TourMotion.frame(gesture, 0f)
            val end = TourMotion.frame(gesture, 1f)
            assertEquals("$gesture reveal", start.reveal, end.reveal, 0f)
            assertEquals("$gesture dip", start.dockDip, end.dockDip, 0f)
        }
    }

    @Test
    fun pressingDipsTheDockUnderTheFingerThenTheCardsRiseAsItLiftsAndTheDipEnds() {
        val pressed = TourMotion.frame(TourGesture.PRESS, 0.5f)
        assertEquals(1f, pressed.dockDip, 0f)
        assertEquals(0f, pressed.reveal, 0f)
        assertTrue(pressed.finger.y > TourMotion.DOCK_Y)

        val released = TourMotion.frame(TourGesture.PRESS, 0.82f)
        assertEquals(0f, released.dockDip, 0f)
        assertEquals(1f, released.reveal, 0f)
    }

    @Test
    fun pullingTakesTheFingerUpAndTheSheetFollowsIt() {
        val early = TourMotion.frame(TourGesture.PULL, 0.3f)
        val late = TourMotion.frame(TourGesture.PULL, 0.6f)
        assertTrue(late.finger.y < early.finger.y)
        assertTrue(late.reveal > early.reveal)
    }

    @Test
    fun holdingThenSlidingMovesTheRingAcrossTheTilesInOrder() {
        val rings =
            samples.map { t -> TourMotion.frame(TourGesture.HOLD_SLIDE, t).ringIndex }.filter { it >= 0 }.distinct()
        assertEquals(listOf(0, 1, 2), rings)
        // The finger holds still on the first tile before the ring comes.
        assertEquals(TourMotion.frame(TourGesture.HOLD_SLIDE, 0.1f).finger.x, TourMotion.TILE_X[0], 0f)
        assertEquals(-1, TourMotion.frame(TourGesture.HOLD_SLIDE, 0.2f).ringIndex)
    }

    @Test
    fun theStillFramesShowWhereEachGestureEndsWithWhatItRevealed() {
        TourGesture.entries.forEach { gesture ->
            val still = TourMotion.staticFrame(gesture)
            assertEquals("$gesture", 1f, still.fingerAlpha, 0f)
            assertEquals("$gesture", 1f, still.reveal, 0f)
        }
        assertEquals(1f, TourMotion.staticFrame(TourGesture.PRESS).dockDip, 0f)
        assertEquals(2, TourMotion.staticFrame(TourGesture.HOLD_SLIDE).ringIndex)
    }
}
