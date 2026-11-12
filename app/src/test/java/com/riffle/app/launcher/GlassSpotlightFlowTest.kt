package com.riffle.app.launcher

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSpotlightFlowTest {
    private val frame = 1f / 60f

    @Test
    fun nothingLitIsAnUnlitProfile() {
        val target = glassSpotlightTargetProfile(emptyList())

        assertFalse(glassSpotlightProfileIsLit(target))
        assertTrue(target.all { it == 0f })
    }

    @Test
    fun aLitIconFillsTheProfilePremultiplied() {
        val target = glassSpotlightTargetProfile(listOf(GlassSpotlightStop(0.5f, Color.Red.copy(alpha = 0.5f))))

        assertTrue(glassSpotlightProfileIsLit(target))
        // Held out to both ends, as the edge stops are. Within one 8-bit step: sRGB colours are
        // stored at 8 bits a channel, so 0.5 alpha reads back as 128/255.
        assertEquals(0.5f, target[0], EIGHT_BIT_STEP)
        assertEquals(0f, target[1], EIGHT_BIT_STEP)
        assertEquals(0.5f, target[3], EIGHT_BIT_STEP)
    }

    @Test
    fun settlesExactlyOnTheTargetWithinAFewSeconds() {
        val shown = glassSpotlightTargetProfile(listOf(GlassSpotlightStop(0.2f, Color.Red)))
        val target = glassSpotlightTargetProfile(listOf(GlassSpotlightStop(0.8f, Color.Blue)))
        val scratch = emptyGlassSpotlightProfile()

        var frames = 0
        while (!stepGlassSpotlightProfile(shown, target, frame, FLOW, scratch)) {
            frames++
            assertTrue("still flowing after $frames frames", frames < glassSpotlightMaxSettleSeconds(FLOW) / frame)
        }

        assertArrayEquals(target, shown, 0f)
    }

    @Test
    fun followsGraduallyRatherThanJumping() {
        val shown = emptyGlassSpotlightProfile()
        val target = glassSpotlightTargetProfile(listOf(GlassSpotlightStop(0.5f, Color.Green)))

        val settled = stepGlassSpotlightProfile(shown, target, frame, FLOW, emptyGlassSpotlightProfile())

        assertFalse(settled)
        val alpha = shown[3]
        assertTrue("moved some way toward the target, was $alpha", alpha > 0f && alpha < 0.2f)
    }

    @Test
    fun aChangeSpreadsToItsNeighboursAsItFlows() {
        // Only the middle sample's target changes; the samples beside it already match theirs.
        val shown = emptyGlassSpotlightProfile()
        val target = emptyGlassSpotlightProfile()
        val middle = DOCK_SPOTLIGHT_PROFILE_SAMPLES / 2
        target[middle * 4 + 3] = 1f

        stepGlassSpotlightProfile(shown, target, frame, FLOW, emptyGlassSpotlightProfile())

        val neighbourAlpha = shown[(middle + 1) * 4 + 3]
        assertTrue("the neighbour picked some of it up, was $neighbourAlpha", neighbourAlpha > 0f)
    }

    @Test
    fun neverLetsAColourOutshineItsOwnCoverage() {
        val shown = glassSpotlightTargetProfile(listOf(GlassSpotlightStop(0.5f, Color.White)))
        val target = emptyGlassSpotlightProfile()
        val scratch = emptyGlassSpotlightProfile()

        repeat(10) { stepGlassSpotlightProfile(shown, target, frame, FLOW, scratch) }

        for (sample in 0 until DOCK_SPOTLIGHT_PROFILE_SAMPLES) {
            val alpha = shown[sample * 4 + 3]
            for (channel in 0 until 3) assertTrue(shown[sample * 4 + channel] <= alpha)
        }
    }

    @Test
    fun aSlowerFlowMovesLessPerFrame() {
        val target = glassSpotlightTargetProfile(listOf(GlassSpotlightStop(0.5f, Color.Green)))
        val quick = emptyGlassSpotlightProfile()
        val slow = emptyGlassSpotlightProfile()

        stepGlassSpotlightProfile(quick, target, frame, 0.2f, emptyGlassSpotlightProfile())
        stepGlassSpotlightProfile(slow, target, frame, 1f, emptyGlassSpotlightProfile())

        assertTrue(slow[3] < quick[3])
    }

    private companion object {
        const val EIGHT_BIT_STEP = 1f / 255f
        const val FLOW = 0.35f
    }
}
