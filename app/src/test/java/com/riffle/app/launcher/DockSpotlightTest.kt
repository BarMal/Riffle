package com.riffle.app.launcher

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockSpotlightTest {
    @Test
    fun placesEachIconAtItsShareOfTheRunInOrder() {
        val stops =
            dockSpotlightStops(
                centers = listOf(300f to Color.Red, 100f to Color.Blue),
                runLengthPx = 400f,
            )

        assertEquals(listOf(0.25f, 0.75f), stops.map { it.position })
        assertEquals(dockSpotlightGlowColor(Color.Blue), stops.first().color)
    }

    @Test
    fun dropsIconsScrolledOutOfTheRun() {
        val stops =
            dockSpotlightStops(
                centers = listOf(-20f to Color.Red, 200f to Color.Green, 420f to Color.Blue),
                runLengthPx = 400f,
            )

        assertEquals(listOf(0.5f), stops.map { it.position })
    }

    @Test
    fun hasNothingToDrawWithoutARun() {
        assertTrue(dockSpotlightStops(listOf(0f to Color.Red), runLengthPx = 0f).isEmpty())
    }

    @Test
    fun liftsAMutedIconColourTowardLightWithoutChangingItsHue() {
        val muted = Color(red = 0.72f, green = 0.39f, blue = 0.39f)

        val glow = dockSpotlightGlowColor(muted)

        assertTrue(glow.red > muted.red)
        assertTrue(glow.red > glow.green)
        assertEquals(glow.green, glow.blue, 1e-6f)
        assertTrue(glow.red <= 1f)
    }

    @Test
    fun holdsTheEndColoursOutToBothCorners() {
        val stops = listOf(DockSpotlightStop(0.3f, Color.Red), DockSpotlightStop(0.7f, Color.Blue))

        val edge = dockSpotlightEdgeStops(stops, alpha = 0.5f)

        assertEquals(listOf(0f, 0.3f, 0.7f, 1f), edge.map { it.first })
        assertEquals(Color.Red.copy(alpha = 0.5f), edge.first().second)
        assertEquals(Color.Blue.copy(alpha = 0.5f), edge.last().second)
    }

    @Test
    fun drawsNoEdgeWhenFadedOutOrUnlit() {
        assertTrue(dockSpotlightEdgeStops(emptyList(), alpha = 1f).isEmpty())
        assertTrue(dockSpotlightEdgeStops(listOf(DockSpotlightStop(0.5f, Color.Red)), alpha = 0f).isEmpty())
    }
}
