package com.riffle.app.launcher

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSpotlightTest {
    @Test
    fun placesEachIconAtItsShareOfTheRunInOrder() {
        val stops =
            glassSpotlightStops(
                centers = listOf(300f to Color.Red, 100f to Color.Blue),
                runLengthPx = 400f,
            )

        assertEquals(listOf(0.25f, 0.75f), stops.map { it.position })
        assertEquals(glassSpotlightGlowColor(Color.Blue), stops.first().color)
    }

    @Test
    fun dropsIconsScrolledOutOfTheRun() {
        val stops =
            glassSpotlightStops(
                centers = listOf(-20f to Color.Red, 200f to Color.Green, 420f to Color.Blue),
                runLengthPx = 400f,
            )

        assertEquals(listOf(0.5f), stops.map { it.position })
    }

    @Test
    fun hasNothingToDrawWithoutARun() {
        assertTrue(glassSpotlightStops(listOf(0f to Color.Red), runLengthPx = 0f).isEmpty())
    }

    @Test
    fun liftsAMutedIconColourTowardLightWithoutChangingItsHue() {
        val muted = Color(red = 0.72f, green = 0.39f, blue = 0.39f)

        val glow = glassSpotlightGlowColor(muted)

        assertTrue(glow.red > muted.red)
        assertTrue(glow.red > glow.green)
        assertEquals(glow.green, glow.blue, 1e-6f)
        assertTrue(glow.red <= 1f)
    }

    @Test
    fun holdsTheEndColoursOutToBothCorners() {
        val stops = listOf(GlassSpotlightStop(0.3f, Color.Red), GlassSpotlightStop(0.7f, Color.Blue))

        val edge = glassSpotlightEdgeStops(stops, alpha = 0.5f)

        assertEquals(listOf(0f, 0.3f, 0.7f, 1f), edge.map { it.first })
        assertEquals(Color.Red.copy(alpha = 0.5f), edge.first().second)
        assertEquals(Color.Blue.copy(alpha = 0.5f), edge.last().second)
    }

    @Test
    fun drawsNoEdgeWhenFadedOutOrUnlit() {
        assertTrue(glassSpotlightEdgeStops(emptyList(), alpha = 1f).isEmpty())
        assertTrue(glassSpotlightEdgeStops(listOf(GlassSpotlightStop(0.5f, Color.Red)), alpha = 0f).isEmpty())
    }

    @Test
    fun anUnlitIconFadesTheGlowInsteadOfCarryingItsNeighboursColour() {
        val stops =
            glassSpotlightStops(
                centers = listOf(100f to Color.Green, 300f to null),
                runLengthPx = 400f,
            )

        assertEquals(listOf(0.25f, 0.75f), stops.map { it.position })
        assertEquals(0f, stops.last().color.alpha, 1e-6f)
        // Same hue as its lit neighbour, so the fade does not pass through black.
        assertEquals(stops.first().color.copy(alpha = 0f), stops.last().color)
        assertEquals(0f, glassSpotlightEdgeStops(stops, alpha = 0.5f).last().second.alpha, 1e-6f)
    }

    @Test
    fun drawsNothingWhenNoIconIsLit() {
        assertTrue(glassSpotlightStops(listOf(100f to null, 200f to null), runLengthPx = 400f).isEmpty())
    }

    @Test
    fun theBakedGlowBlendsBetweenNeighbouringStops() {
        val edge = arrayOf(0f to Color.Red, 0.5f to Color.Blue, 1f to Color.Blue)

        assertEquals(Color.Red, glassSpotlightColorAt(edge, 0f))
        val quarter = glassSpotlightColorAt(edge, 0.25f)
        assertEquals(0.5f, quarter.red, 0.01f)
        assertEquals(0.5f, quarter.blue, 0.01f)
        assertEquals(Color.Blue, glassSpotlightColorAt(edge, 0.75f))
    }

    @Test
    fun theBakedGlowHoldsItsEndColoursPastTheStops() {
        val edge = arrayOf(0.2f to Color.Red, 0.8f to Color.Blue)

        assertEquals(Color.Red, glassSpotlightColorAt(edge, 0.1f))
        assertEquals(Color.Blue, glassSpotlightColorAt(edge, 0.9f))
        assertEquals(Color.Transparent, glassSpotlightColorAt(emptyArray(), 0.5f))
    }

    @Test
    fun aSurfaceRunsAlongItsLongerAxisUnlessTheAxisIsFixed() {
        val wide = Size(400f, 100f)
        val tall = Size(100f, 400f)

        assertTrue(GlassSpotlightAxis.Longest.runsHorizontally(wide))
        assertFalse(GlassSpotlightAxis.Longest.runsHorizontally(tall))
        assertTrue(GlassSpotlightAxis.Longest.runsHorizontally(Size(200f, 200f)))
        assertFalse(GlassSpotlightAxis.Vertical.runsHorizontally(wide))
        assertTrue(GlassSpotlightAxis.Horizontal.runsHorizontally(tall))
    }

    @Test
    fun theGlowFollowsTheShapesOwnCornerRadiusWhateverItsKind() {
        val density = Density(1f)
        val size = Size(300f, 120f)

        assertEquals(24f, glassSpotlightCornerRadiusPx(RoundedCornerShape(24.dp), size, density), 1e-4f)
        assertEquals(24f, glassSpotlightCornerRadiusPx(RiffleShapes.large, size, density), 1e-4f)
        assertEquals(0f, glassSpotlightCornerRadiusPx(RectangleShape, size, density), 1e-4f)
        // A pill, and a radius larger than the surface allows, both settle at half the short side.
        assertEquals(60f, glassSpotlightCornerRadiusPx(RoundedCornerShape(percent = 50), size, density), 1e-4f)
        assertEquals(60f, glassSpotlightCornerRadiusPx(RoundedCornerShape(200.dp), size, density), 1e-4f)
    }
}
