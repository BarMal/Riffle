package com.riffle.core.domain.launcher.icons

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconShapeGeometryTest {
    @Test
    fun theSystemShapeHasNoOutlineOfOurs() {
        assertNull(IconShapeGeometry.outline(IconShape.SYSTEM))
        assertEquals(1f, IconShapeGeometry.inscribedSquareFraction(IconShape.SYSTEM))
    }

    @Test
    fun everyOtherShapeIsAClosedOutlineInsideTheViewport() {
        IconShape.entries.filter { it != IconShape.SYSTEM }.forEach { shape ->
            val outline = assertNotNull(IconShapeGeometry.outline(shape), shape.name)
            assertTrue(outline.first() is MaskCommand.MoveTo, shape.name)
            assertEquals(MaskCommand.Close, outline.last(), shape.name)
            flatten(outline).forEach { (x, y) ->
                assertTrue(x in -0.01f..100.01f && y in -0.01f..100.01f, "$shape point $x,$y")
            }
        }
    }

    @Test
    fun theCircleOutlineIsAtRadiusFiftyFromTheCentre() {
        flatten(IconShapeGeometry.outline(IconShape.CIRCLE)!!).forEach { (x, y) ->
            val radius = kotlin.math.hypot(x - 50f, y - 50f)
            assertTrue(abs(radius - 50f) < 0.3f, "radius $radius at $x,$y")
        }
    }

    @Test
    fun theSquircleBulgesBetweenTheCircleAndTheSquare() {
        val diagonal = flatten(IconShapeGeometry.outline(IconShape.SQUIRCLE)!!).minOf { (x, y) -> abs(x - y) }
        val point = flatten(IconShapeGeometry.outline(IconShape.SQUIRCLE)!!).first { (x, y) -> abs(x - y) == diagonal }
        // On the diagonal the n = 4 superellipse is 2^(-1/4) from the centre per axis: 50 + 50 * 0.8409 or 50 - 42.
        val offset = abs(point.first - 50f)
        assertTrue(abs(offset - 42.05f) < 1.5f, "offset $offset")
    }

    @Test
    fun theInscribedSquareOfEachShapeStaysInsideItsOutline() {
        IconShape.entries.filter { it != IconShape.SYSTEM }.forEach { shape ->
            val fraction = IconShapeGeometry.inscribedSquareFraction(shape)
            val half = fraction * 50f
            val polygon = flatten(IconShapeGeometry.outline(shape)!!)
            listOf(
                50f - half to 50f - half,
                50f + half to 50f - half,
                50f + half to 50f + half,
                50f - half to 50f + half,
            )
                .forEach { (x, y) ->
                    // Allow the flattening error of the polygon.
                    assertTrue(inside(polygon, x, y) || nearEdge(polygon, x, y), "$shape corner $x,$y of $fraction")
                }
            assertTrue(fraction in 0.69f..1f, "$shape $fraction")
        }
    }

    @Test
    fun theInscribedSquareIsTheLargestPossibleForTheCircle() {
        assertTrue(abs(IconShapeGeometry.inscribedSquareFraction(IconShape.CIRCLE) - 0.7071f) < 0.001f)
        assertTrue(
            IconShapeGeometry.inscribedSquareFraction(IconShape.SQUIRCLE) >
                IconShapeGeometry.inscribedSquareFraction(IconShape.CIRCLE),
        )
    }

    @Test
    fun aLegacyIconIsNeverDrawnLargerThanTheCap() {
        IconShape.entries.forEach { shape ->
            val fraction = IconShapeGeometry.legacyContentFraction(shape)
            assertTrue(fraction <= IconShapeGeometry.MAX_LEGACY_CONTENT_FRACTION, shape.name)
            assertTrue(fraction <= IconShapeGeometry.inscribedSquareFraction(shape), shape.name)
        }
    }

    @Test
    fun anAdaptiveLayerIsOneAndAHalfTimesTheIcon() {
        assertEquals(1.5f, IconShapeGeometry.ADAPTIVE_LAYER_SCALE)
    }

    @Test
    fun aStoredShapeReadsBackAndAnUnknownOneIsTheDefault() {
        IconShape.entries.forEach { assertEquals(it, IconShape.decode(it.storedId)) }
        assertEquals(IconShape.SYSTEM, IconShape.decode("hexagon"))
        assertEquals(IconShape.SYSTEM, IconShape.decode(null))
    }

    private fun flatten(commands: List<MaskCommand>): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        var cx = 0f
        var cy = 0f
        commands.forEach { command ->
            when (command) {
                is MaskCommand.MoveTo -> {
                    cx = command.x
                    cy = command.y
                    points += cx to cy
                }
                is MaskCommand.LineTo -> {
                    cx = command.x
                    cy = command.y
                    points += cx to cy
                }
                is MaskCommand.CubicTo -> {
                    for (step in 1..STEPS) {
                        val t = step / STEPS.toFloat()
                        val u = 1 - t
                        points += bezier(t, u, cx, command.x1, command.x2, command.x) to
                            bezier(t, u, cy, command.y1, command.y2, command.y)
                    }
                    cx = command.x
                    cy = command.y
                }
                MaskCommand.Close -> Unit
            }
        }
        return points
    }

    private fun bezier(
        t: Float,
        u: Float,
        p0: Float,
        p1: Float,
        p2: Float,
        p3: Float,
    ): Float = u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3

    private fun inside(
        polygon: List<Pair<Float, Float>>,
        x: Float,
        y: Float,
    ): Boolean {
        var result = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) result = !result
            j = i
        }
        return result
    }

    private fun nearEdge(
        polygon: List<Pair<Float, Float>>,
        x: Float,
        y: Float,
    ): Boolean = polygon.any { (px, py) -> kotlin.math.hypot(px - x, py - y) < 0.6f }

    private companion object {
        const val STEPS = 24
    }
}
