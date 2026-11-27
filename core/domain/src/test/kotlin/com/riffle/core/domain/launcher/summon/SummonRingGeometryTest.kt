package com.riffle.core.domain.launcher.summon

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummonRingGeometryTest {
    private val bounds = RingBounds(left = 40f, top = 100f, right = 1040f, bottom = 2200f)
    private val radius = 300f
    private val clearance = 90f

    private fun layoutAt(
        x: Float,
        y: Float,
        count: Int = 8,
    ) = SummonRingGeometry.layout(RingPoint(x, y), count, bounds, radius, clearance)

    @Test
    fun baseAnglesRunClockwiseFromTheTopEvenlySpaced() {
        assertEquals(listOf(-90f, 0f, 90f, 180f), SummonRingGeometry.baseAngles(4))
        assertEquals(listOf(-90f, -45f, 0f, 45f, 90f, 135f, 180f, 225f), SummonRingGeometry.baseAngles(8))
    }

    @Test
    fun inTheMiddleTheRingIsAFullCircleOnItsBaseAngles() {
        val layout = layoutAt(540f, 1100f)
        assertTrue(layout.isFullCircle)
        assertEquals(radius, layout.radius)
        assertEquals(SummonRingGeometry.baseAngles(8), layout.angles)
        val top = layout.position(0)
        assertEquals(540f, top.x, 0.01f)
        assertEquals(800f, top.y, 0.01f)
    }

    @Test
    fun nearAnEdgeTheRingOpensIntoAnArcThatStaysOnScreenInOrder() {
        listOf(RingPoint(1000f, 1100f), RingPoint(80f, 1100f), RingPoint(540f, 2150f), RingPoint(1000f, 150f)).forEach {
                centre ->
            val layout = SummonRingGeometry.layout(centre, 8, bounds, radius, clearance)
            assertTrue(!layout.isFullCircle, "expected an arc at $centre")
            (0 until 8).forEach { index ->
                assertTrue(bounds.contains(layout.position(index)), "slot $index off screen at $centre")
            }
            // Clockwise order is kept along the arc, with at least the arc's gap between neighbours.
            val sorted = layout.angles.sorted()
            sorted.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= layout.gap - 0.01f) }
        }
    }

    @Test
    fun slotsThatAlreadyFitKeepTheirDirection() {
        // Four slots against the right edge: only the right-hand one has to move; up and left stay where muscle
        // memory expects them.
        val layout = layoutAt(1000f, 1100f, count = 4)
        assertEquals(270f, normalise(layout.angles[0]), 0.5f)
        assertEquals(180f, normalise(layout.angles[3]), 0.5f)
        assertTrue(bounds.contains(layout.position(1)))
    }

    @Test
    fun aCrampedCornerWidensTheRingBeforeSqueezingTheGaps() {
        val layout = layoutAt(60f, 2180f)
        assertTrue(layout.radius > radius)
        (0 until 8).forEach { assertTrue(bounds.contains(layout.position(it))) }
    }

    @Test
    fun pickingFindsTheNearestSlotByDirection() {
        val layout = layoutAt(540f, 1100f)
        assertEquals(0, layout.pick(RingPoint(540f, 900f), current = null))
        assertEquals(2, layout.pick(RingPoint(740f, 1100f), current = null))
        assertEquals(4, layout.pick(RingPoint(540f, 1300f), current = null))
    }

    @Test
    fun hysteresisHoldsTheCurrentSlotOnABoundary() {
        val layout = layoutAt(540f, 1100f)
        // Slot 0 is at -90 and slot 1 at -45, so -67 sits just past their boundary, a hair nearer slot 1.
        val nearBoundary = pointAt(layout, -67f)
        assertEquals(0, layout.pick(nearBoundary, current = 0))
        assertEquals(1, layout.pick(nearBoundary, current = 1))
        // Clearly past the boundary, the neighbour wins.
        assertEquals(1, layout.pick(pointAt(layout, -55f), current = 0))
    }

    @Test
    fun onAnArcDirectionsBeyondItsEndsPickNothing() {
        val layout = layoutAt(1000f, 1100f)
        // Straight right points off screen, past the arc.
        assertNull(layout.pick(RingPoint(1300f, 1100f), current = null))
    }

    private fun pointAt(
        layout: RingLayout,
        degrees: Float,
    ): RingPoint {
        val r = Math.toRadians(degrees.toDouble())
        return RingPoint(
            layout.centre.x + (200 * kotlin.math.cos(r)).toFloat(),
            layout.centre.y + (200 * kotlin.math.sin(r)).toFloat(),
        )
    }

    private fun normalise(degrees: Float): Float {
        var a = degrees % 360f
        if (a < 0) a += 360f
        return if (abs(a - 360f) < 0.01f) 0f else a
    }
}
