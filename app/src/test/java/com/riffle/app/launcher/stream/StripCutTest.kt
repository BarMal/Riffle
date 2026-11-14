package com.riffle.app.launcher.stream

import com.riffle.core.domain.launcher.stream.StackGeometry
import com.riffle.core.domain.launcher.stream.StreamCardStackSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the card in front stands in the coordinates of the card behind it (device fixes, round 6, item 5): what is cut
 * out of a strip so that it tucks under the card in front. Pure geometry over the stack's frames.
 */
class StripCutTest {
    private val geometry = StackGeometry(heightsDp = listOf(88f, 168f, 120f, 168f))
    private val width = 300f
    private val density = 2f
    private val eps = 0.01f

    private fun cut(
        index: Int,
        position: Float,
    ) = StripCut.inFrontOf(index, position, geometry, width, density)

    @Test
    fun theFrontCardAtRestHasNoCardInFrontAndIsNotCut() {
        assertNull(cut(0, 0f))
        // The card in front of card 1 has sunk away when card 1 is the front card.
        assertNull(cut(1, 1f))
    }

    @Test
    fun aStripIsCutByTheCardInFrontExactlyAPitchDownItsOwnCard() {
        val first = cut(1, 0f)!!
        // The strip is drawn a step smaller, so in its own coordinates the front card's top is a pitch over its scale.
        val scale = 1f - StreamCardStackSpec.SCALE_STEP
        assertEquals(StreamCardStackSpec.PITCH_DP * density / scale, first.top, eps)
        // No gap: the cut starts where the strip's slot ends.
        assertEquals(geometry.frame(1, 0f).clipHeightDp * density, first.top, eps)
        // The card in front is larger than the strip and centred on it: it sticks out on both sides alike.
        assertEquals(1f / scale, first.scale, eps)
        assertTrue(first.left < 0f)
        assertEquals(width, first.left * 2 + width * first.scale, eps)
        assertEquals(88f * density, first.heightPx, eps)
    }

    @Test
    fun everyStripIsCutByItsOwnNeighbourNotByTheFrontCard() {
        val second = cut(2, 0f)!!
        val scaleOfSecond = 1f - 2 * StreamCardStackSpec.SCALE_STEP
        assertEquals(StreamCardStackSpec.PITCH_DP * density / scaleOfSecond, second.top, eps)
        assertEquals(168f * density, second.heightPx, eps)
    }

    @Test
    fun theCutFollowsTheNeighbourWhileTheStackMoves() {
        var last = cut(1, 0f)!!.top
        for (step in 1..99) {
            val now = cut(1, step / 100f)
            assertNotNull("card 1 is cut by the sinking card at ${step / 100f}", now)
            assertTrue("the cut moves down as the card in front sinks", now!!.top >= last - eps)
            last = now.top
        }
        // The card in front shrinks as it sinks, and the cut with it.
        assertTrue(cut(1, 0.9f)!!.scale < cut(1, 0.1f)!!.scale + eps)
    }
}
