package com.riffle.core.domain.launcher.designsystem

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A point in pixel space, y pointing down. */
data class ContinuousCornerPoint(
    val x: Float,
    val y: Float,
)

/** A cubic Bézier segment starting where the previous segment (or the outline's start) ended. */
data class ContinuousCornerCubic(
    val control1: ContinuousCornerPoint,
    val control2: ContinuousCornerPoint,
    val end: ContinuousCornerPoint,
)

/** One corner: a straight line reaches [start], then [cubics] trace the curve. Empty for a sharp corner. */
data class ContinuousCornerCurve(
    val start: ContinuousCornerPoint,
    val cubics: List<ContinuousCornerCubic>,
)

/**
 * A closed rounded-rectangle outline: for each corner, clockwise from the top-left, draw a line to
 * its [ContinuousCornerCurve.start] and then its cubics; close back to the first corner's start.
 */
data class ContinuousRectOutline(
    val corners: List<ContinuousCornerCurve>,
)

/**
 * Continuous ("squircle"-like) corner geometry, after Figma's corner-smoothing construction: each
 * corner is a Bézier lead-in, a shorter circular arc and a mirrored Bézier lead-out, so curvature
 * ramps up from the straight edge instead of jumping to 1/r as a circular corner does.
 *
 * A [smoothing] of 0 reproduces a plain circular corner; 0.6 approximates Apple's continuous corners.
 * The corner extends `(1 + smoothing) * radius` along each edge and is limited to half the shorter
 * side, reducing smoothing first so small shapes degrade to plain circular corners.
 */
object ContinuousCornerGeometry {
    const val DEFAULT_SMOOTHING = 0.6f

    /** Parameters of one corner, in the corner's own frame. */
    data class CornerParams(
        val radius: Float,
        val smoothing: Float,
        /** Distance from the corner point along each edge where the curve begins. */
        val extent: Float,
        internal val a: Float,
        internal val b: Float,
        internal val c: Float,
        internal val d: Float,
        internal val arcRadians: Float,
    )

    fun cornerParams(
        radius: Float,
        smoothing: Float,
        budget: Float,
    ): CornerParams {
        val r = radius.coerceIn(0f, max(budget, 0f))
        var s = smoothing.coerceIn(0f, 1f)
        if (r <= 0f) return CornerParams(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        var extent = (1f + s) * r
        if (extent > budget) {
            s = min(s, budget / r - 1f).coerceAtLeast(0f)
            extent = min(extent, budget)
        }
        val arc = (PI / 2.0 * (1.0 - s))
        val arcSection = sin(arc / 2.0) * r * sqrt(2.0)
        val alpha = (PI / 2.0 - arc) / 2.0
        val p3ToP4 = r * tan(alpha / 2.0)
        val beta = PI / 4.0 * s
        val c = p3ToP4 * cos(beta)
        val d = c * tan(beta)
        val b = ((extent - arcSection - c - d) / 3.0).coerceAtLeast(0.0)
        return CornerParams(
            radius = r,
            smoothing = s,
            extent = extent,
            a = (2.0 * b).toFloat(),
            b = b.toFloat(),
            c = c.toFloat(),
            d = d.toFloat(),
            arcRadians = arc.toFloat(),
        )
    }

    /**
     * Cubics for one corner in a local frame whose origin is the sharp corner point, where +x is the
     * direction of travel along the incoming edge and +y the direction of the outgoing edge. The
     * curve starts at (-extent, 0) and ends at (0, extent).
     */
    fun localCorner(params: CornerParams): List<ContinuousCornerCubic> {
        if (params.radius <= 0f) return emptyList()
        val p = params.extent
        val r = params.radius
        val start = ContinuousCornerPoint(-p, 0f)
        val leadIn1 = ContinuousCornerPoint(-p + params.a, 0f)
        val leadIn2 = ContinuousCornerPoint(-p + params.a + params.b, 0f)
        val arcStart = ContinuousCornerPoint(-p + params.a + params.b + params.c, params.d)
        val arcEnd = mirror(arcStart)

        // Circular arc of radius r centred at (-r, r), symmetric about the corner diagonal,
        // approximated by one cubic whose handles lie along the arc's end tangents.
        val phi0 = -PI / 4.0 - params.arcRadians / 2.0
        val phi1 = -PI / 4.0 + params.arcRadians / 2.0
        val handle = (4.0 / 3.0 * tan(params.arcRadians / 4.0) * r).toFloat()
        val arcControl1 =
            ContinuousCornerPoint(
                arcStart.x + handle * (-sin(phi0)).toFloat(),
                arcStart.y + handle * cos(phi0).toFloat(),
            )
        val arcControl2 =
            ContinuousCornerPoint(
                arcEnd.x - handle * (-sin(phi1)).toFloat(),
                arcEnd.y - handle * cos(phi1).toFloat(),
            )
        return listOf(
            ContinuousCornerCubic(leadIn1, leadIn2, arcStart),
            ContinuousCornerCubic(arcControl1, arcControl2, arcEnd),
            ContinuousCornerCubic(mirror(leadIn2), mirror(leadIn1), mirror(start)),
        )
    }

    /**
     * Full outline for a [width] x [height] rectangle with independent corner radii.
     * Radii are in the same pixel unit as the size.
     */
    fun rectOutline(
        width: Float,
        height: Float,
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float,
        smoothing: Float = DEFAULT_SMOOTHING,
    ): ContinuousRectOutline {
        val budget = min(width, height) / 2f
        val tl = cornerParams(topLeft, smoothing, budget)
        val tr = cornerParams(topRight, smoothing, budget)
        val br = cornerParams(bottomRight, smoothing, budget)
        val bl = cornerParams(bottomLeft, smoothing, budget)
        // Frames: (corner point, incoming unit vector, outgoing unit vector), clockwise.
        val corners =
            listOf(
                place(tl, originX = 0f, originY = 0f, inX = 0f, inY = -1f, outX = 1f, outY = 0f),
                place(tr, originX = width, originY = 0f, inX = 1f, inY = 0f, outX = 0f, outY = 1f),
                place(br, originX = width, originY = height, inX = 0f, inY = 1f, outX = -1f, outY = 0f),
                place(bl, originX = 0f, originY = height, inX = -1f, inY = 0f, outX = 0f, outY = -1f),
            )
        return ContinuousRectOutline(corners = corners)
    }

    private fun mirror(point: ContinuousCornerPoint): ContinuousCornerPoint = ContinuousCornerPoint(-point.y, -point.x)

    @Suppress("LongParameterList")
    private fun place(
        params: CornerParams,
        originX: Float,
        originY: Float,
        inX: Float,
        inY: Float,
        outX: Float,
        outY: Float,
    ): ContinuousCornerCurve {
        fun transform(point: ContinuousCornerPoint) =
            ContinuousCornerPoint(
                originX + point.x * inX + point.y * outX,
                originY + point.x * inY + point.y * outY,
            )
        val cubics =
            localCorner(params).map { cubic ->
                ContinuousCornerCubic(transform(cubic.control1), transform(cubic.control2), transform(cubic.end))
            }
        return ContinuousCornerCurve(start = transform(ContinuousCornerPoint(-params.extent, 0f)), cubics = cubics)
    }
}
