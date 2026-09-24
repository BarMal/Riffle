package com.riffle.core.domain.launcher.designsystem

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContinuousCornerGeometryTest {
    @Test
    fun smoothingExtendsTheCornerAlongEachEdge() {
        val params = ContinuousCornerGeometry.cornerParams(radius = 24f, smoothing = 0.6f, budget = 100f)

        assertClose(38.4f, params.extent)
        assertClose(0.6f, params.smoothing)
    }

    @Test
    fun zeroSmoothingIsACircularCorner() {
        val params = ContinuousCornerGeometry.cornerParams(radius = 24f, smoothing = 0f, budget = 100f)
        val cubics = ContinuousCornerGeometry.localCorner(params)
        val arc = cubics[1]

        assertClose(24f, params.extent)
        // The arc spans the whole quarter circle and its midpoint sits on the circle.
        assertClose(0f, arc.end.x)
        assertClose(24f, arc.end.y)
        val mid = cubicPoint(ContinuousCornerPoint(-24f, 0f), arc, 0.5f)
        assertClose(24f, hypot(mid.x + 24f, mid.y - 24f), tolerance = 0.05f)
    }

    @Test
    fun smallShapesGiveUpSmoothingBeforeRadius() {
        val params = ContinuousCornerGeometry.cornerParams(radius = 24f, smoothing = 0.6f, budget = 30f)

        assertClose(24f, params.radius)
        assertClose(30f, params.extent)
        assertClose(0.25f, params.smoothing)
    }

    @Test
    fun radiusIsClampedToHalfTheShorterSide() {
        val params = ContinuousCornerGeometry.cornerParams(radius = 80f, smoothing = 0.6f, budget = 20f)

        assertClose(20f, params.radius)
        assertClose(20f, params.extent)
        assertClose(0f, params.smoothing)
    }

    @Test
    fun cornerIsTangentContinuousAndSymmetric() {
        val params = ContinuousCornerGeometry.cornerParams(radius = 24f, smoothing = 0.6f, budget = 100f)
        val (leadIn, arc, leadOut) = ContinuousCornerGeometry.localCorner(params)

        // Starts flush with the incoming edge and ends flush with the outgoing edge.
        assertClose(0f, leadIn.control1.y)
        assertClose(0f, leadOut.control2.x)
        assertClose(params.extent, leadOut.end.y)
        // Lead-in handle and arc handle are collinear at the join (G1 continuity).
        val incoming = vector(leadIn.control2, leadIn.end)
        val outgoing = vector(leadIn.end, arc.control1)
        assertClose(0f, incoming.x * outgoing.y - incoming.y * outgoing.x, tolerance = 1e-3f)
        // The lead-out mirrors the lead-in across the corner diagonal.
        assertClose(-leadIn.control1.x, leadOut.control2.y)
        assertClose(-leadIn.end.y, arc.end.x)
    }

    @Test
    fun sharpCornersProduceNoCurves() {
        val outline =
            ContinuousCornerGeometry.rectOutline(
                width = 100f,
                height = 60f,
                topLeft = 0f,
                topRight = 0f,
                bottomRight = 0f,
                bottomLeft = 0f,
            )

        assertTrue(outline.corners.all { corner -> corner.cubics.isEmpty() })
        assertEquals(
            listOf(
                ContinuousCornerPoint(0f, 0f),
                ContinuousCornerPoint(100f, 0f),
                ContinuousCornerPoint(100f, 60f),
                ContinuousCornerPoint(0f, 60f),
            ),
            outline.corners.map { corner -> corner.start },
        )
    }

    @Test
    fun rectOutlineCornersStartAndEndOnTheirEdges() {
        val outline =
            ContinuousCornerGeometry.rectOutline(
                width = 200f,
                height = 120f,
                topLeft = 16f,
                topRight = 16f,
                bottomRight = 16f,
                bottomLeft = 16f,
            )
        val extent = 16f * 1.6f
        val expectedStarts =
            listOf(
                ContinuousCornerPoint(0f, extent),
                ContinuousCornerPoint(200f - extent, 0f),
                ContinuousCornerPoint(200f, 120f - extent),
                ContinuousCornerPoint(extent, 120f),
            )
        val expectedEnds =
            listOf(
                ContinuousCornerPoint(extent, 0f),
                ContinuousCornerPoint(200f, extent),
                ContinuousCornerPoint(200f - extent, 120f),
                ContinuousCornerPoint(0f, 120f - extent),
            )

        outline.corners.forEachIndexed { index, corner ->
            assertPointClose(expectedStarts[index], corner.start)
            assertPointClose(expectedEnds[index], corner.cubics.last().end)
        }
    }

    @Test
    fun outlineStaysInsideItsBounds() {
        val outline =
            ContinuousCornerGeometry.rectOutline(
                width = 90f,
                height = 40f,
                topLeft = 32f,
                topRight = 8f,
                bottomRight = 24f,
                bottomLeft = 0f,
            )
        val points =
            outline.corners.flatMap { corner ->
                listOf(corner.start) +
                    corner.cubics.flatMap { cubic -> listOf(cubic.control1, cubic.control2, cubic.end) }
            }

        assertTrue(points.all { point -> point.x in -EPSILON..90f + EPSILON && point.y in -EPSILON..40f + EPSILON })
    }

    private fun vector(
        from: ContinuousCornerPoint,
        to: ContinuousCornerPoint,
    ) = ContinuousCornerPoint(to.x - from.x, to.y - from.y)

    private fun cubicPoint(
        start: ContinuousCornerPoint,
        cubic: ContinuousCornerCubic,
        t: Float,
    ): ContinuousCornerPoint {
        val u = 1f - t

        fun axis(
            p0: Float,
            p1: Float,
            p2: Float,
            p3: Float,
        ) = u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
        return ContinuousCornerPoint(
            axis(start.x, cubic.control1.x, cubic.control2.x, cubic.end.x),
            axis(start.y, cubic.control1.y, cubic.control2.y, cubic.end.y),
        )
    }

    private fun assertPointClose(
        expected: ContinuousCornerPoint,
        actual: ContinuousCornerPoint,
    ) {
        assertClose(expected.x, actual.x)
        assertClose(expected.y, actual.y)
    }

    private fun assertClose(
        expected: Float,
        actual: Float,
        tolerance: Float = EPSILON,
    ) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }

    private companion object {
        const val EPSILON = 1e-3f
    }
}
