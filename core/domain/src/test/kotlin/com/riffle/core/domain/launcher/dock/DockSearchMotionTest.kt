package com.riffle.core.domain.launcher.dock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockSearchMotionTest {
    private val omega = DockMotionTuning().omega

    private fun samples(
        from: Float,
        to: Float,
        omega: Float = this.omega,
    ): List<Float> {
        val frames = mutableListOf<Float>()
        var t = 0f
        while (t < 2f) {
            frames += DockSearchMotion.progress(from, to, t, omega)
            t += 1f / 60f
        }
        return frames
    }

    @Test
    fun itStartsWhereItWasAndEndsAtTheTarget() {
        assertEquals(0f, DockSearchMotion.progress(0f, 1f, 0f, omega))
        assertEquals(1f, DockSearchMotion.progress(0f, 1f, 5f, omega), 1e-4f)
        assertEquals(0f, DockSearchMotion.progress(1f, 0f, 5f, omega), 1e-4f)
    }

    @Test
    fun openingNeverOvershootsAndOnlyRises() {
        val frames = samples(0f, 1f)
        assertTrue(frames.all { it in 0f..1f })
        assertTrue(frames.zipWithNext().all { (a, b) -> b >= a - 1e-6f })
    }

    @Test
    fun closingIsTheOpeningTheOtherWay() {
        // The same spring from the other end is the mirror image, frame for frame.
        val opening = samples(0f, 1f)
        val closing = samples(1f, 0f)
        opening.zip(closing).forEach { (o, c) -> assertEquals(1f, o + c, 1e-5f) }
    }

    @Test
    fun aCloseThatCatchesTheOpeningHalfWayStartsFromThere() {
        val caught = DockSearchMotion.progress(0f, 1f, 0.08f, omega)
        assertEquals(caught, DockSearchMotion.progress(caught, 0f, 0f, omega))
        val frames = samples(caught, 0f)
        assertTrue(frames.all { it in 0f..caught + 1e-6f })
        assertTrue(frames.zipWithNext().all { (a, b) -> b <= a + 1e-6f })
    }

    @Test
    fun itArrivesAtTheDocksOpeningSpeed() {
        // At the default speed it has arrived within about twice the Dock's opening speed.
        var t = 0f
        while (!DockSearchMotion.isArrived(0f, 1f, t, omega)) t += 0.001f
        assertTrue(t in 0.2f..0.5f, "arrived at $t s")
    }

    @Test
    fun aSlowerSpeedTakesLonger() {
        val slow = DockMotionTuning(speedMillis = 400).omega
        assertTrue(DockSearchMotion.progress(0f, 1f, 0.2f, slow) < DockSearchMotion.progress(0f, 1f, 0.2f, omega))
        assertFalse(DockSearchMotion.isArrived(0f, 1f, 0.4f, slow))
    }

    @Test
    fun theFrameRisesFromTheDocksEdgeAndFadesIn() {
        val closed = DockSearchMotion.frame(0f, reducedMotion = false)
        val open = DockSearchMotion.frame(1f, reducedMotion = false)
        assertEquals(0f, closed.alpha)
        assertEquals(DockSearchMotion.RISE_DP, closed.riseDp)
        assertEquals(DockSearchMotion.START_SCALE, closed.scale)
        assertEquals(1f, open.alpha)
        assertEquals(0f, open.riseDp)
        assertEquals(1f, open.scale)
        // Fully opaque before it has finished rising.
        assertEquals(1f, DockSearchMotion.frame(DockSearchMotion.OPAQUE_AT, false).alpha)
    }

    @Test
    fun underReducedMotionNothingMovesAndOnlyTheAlphaFollows() {
        val half = DockSearchMotion.frame(0.5f, reducedMotion = true)
        assertEquals(0.5f, half.alpha)
        assertEquals(0f, half.riseDp)
        assertEquals(1f, half.scale)
    }

    @Test
    fun theReducedFadeIsTheEightyMillisecondTimeline() {
        assertEquals(0f, DockSearchMotion.reducedProgress(0f, 1f, 0f))
        assertEquals(1f, DockSearchMotion.reducedProgress(0f, 1f, 0.08f), 1e-4f)
        assertFalse(DockSearchMotion.reducedIsArrived(0.04f))
        assertTrue(DockSearchMotion.reducedIsArrived(0.1f))
    }
}
