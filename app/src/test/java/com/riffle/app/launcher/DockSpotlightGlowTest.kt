package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DockSpotlightGlowTest {
    // A pill: 400 x 100 with fully rounded ends (radius 50).
    private val run = 400f
    private val cross = 100f
    private val radius = 50f
    private val uniform =
        DockSpotlightGlowEdges(
            innerDepthPx = 20f,
            outerDepthPx = 20f,
            endDepthPx = 20f,
            innerAlpha = 1f,
            outerAlpha = 1f,
            endAlpha = 1f,
        )

    private fun glow(
        along: Float,
        across: Float,
        edges: DockSpotlightGlowEdges = uniform,
    ) = dockSpotlightGlowAlpha(along, across, run, cross, radius, innerAtStart = true, edges = edges)

    @Test
    fun followsTheRoundedEndRatherThanAStraightBand() {
        // Halfway into the glow, measured along the round end's own normal at 45 degrees...
        val diagonal = 1f / sqrt(2f)
        val onCurve = radius - 10f
        val atCap = glow(along = radius - onCurve * diagonal, across = radius - onCurve * diagonal)
        // ...is exactly as lit as halfway into the glow along a straight edge.
        val atEdge = glow(along = run / 2f, across = 10f)

        assertEquals(0.5f, atEdge, 1e-4f)
        assertEquals(atEdge, atCap, 1e-4f)
    }

    @Test
    fun theTipOfTheRoundEndIsLitLikeAnyEdge() {
        assertEquals(0.5f, glow(along = 10f, across = cross / 2f), 1e-4f)
    }

    @Test
    fun deeperThanTheGlowIsDark() {
        assertEquals(0f, glow(along = run / 2f, across = cross / 2f), 1e-6f)
        // Inside the round end, further from its curve than the glow reaches.
        assertEquals(0f, glow(along = 25f, across = cross / 2f), 1e-6f)
    }

    @Test
    fun theInnerEdgeOutshinesTheOuterOne() {
        val edges = uniform.copy(innerAlpha = 0.55f, outerAlpha = 0.30f, endAlpha = 0.42f)

        val inner = glow(along = run / 2f, across = 0f, edges = edges)
        val outer = glow(along = run / 2f, across = cross, edges = edges)
        val end = glow(along = 0f, across = cross / 2f, edges = edges)

        assertEquals(0.55f, inner, 1e-3f)
        assertEquals(0.30f, outer, 1e-3f)
        assertEquals(0.42f, end, 1e-3f)
    }

    @Test
    fun theRoundEndBlendsSmoothlyBetweenEdgeKinds() {
        val edges = uniform.copy(innerAlpha = 1f, endAlpha = 0f)
        val diagonal = 1f / sqrt(2f)
        // On the curve at 45 degrees towards the inner edge: half end, half inner.
        val blended = glow(along = radius - radius * diagonal, across = radius - radius * diagonal, edges = edges)

        assertTrue("expected a blend, was $blended", blended in 0.4f..0.6f)
    }

    @Test
    fun aSquareCorneredDockStillLightsItsStraightEnds() {
        val square = dockSpotlightGlowAlpha(0f, cross / 2f, run, cross, 0f, innerAtStart = true, edges = uniform)

        assertEquals(1f, square, 1e-4f)
    }
}
