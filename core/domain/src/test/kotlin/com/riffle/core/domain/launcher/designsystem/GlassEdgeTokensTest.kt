package com.riffle.core.domain.launcher.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassEdgeTokensTest {
    private val serein = GlassEdgeTokens

    @Test
    fun theLightIsBrightestAtTheTopAndFadesToTheBottom() {
        assertTrue(serein.BORDER_TOP_ALPHA > serein.BORDER_SIDE_ALPHA)
        assertTrue(serein.BORDER_SIDE_ALPHA > serein.BORDER_BOTTOM_ALPHA)
        assertTrue(serein.SPECULAR_PEAK_ALPHA > serein.SPECULAR_SHOULDER_ALPHA)
    }

    @Test
    fun theTintStripsAreSereinsSevenAndFivePixelsAtThirtyEightAndTwentyPercent() {
        assertEquals(7f, serein.TINT_TOP_STRIP_DP)
        assertEquals(0.38f, serein.TINT_TOP_ALPHA)
        assertEquals(5f, serein.TINT_BOTTOM_STRIP_DP)
        assertEquals(0.20f, serein.TINT_BOTTOM_ALPHA)
    }

    @Test
    fun theBorderRampHasTheSameDpAtTheTopOfAPillAndOfATallCard() {
        listOf(60f, 168f, 400f, 900f).forEach { height ->
            val stops = serein.borderStops(height)
            assertEquals(serein.BORDER_TOP_ALPHA, stops.first().alpha)
            assertEquals(serein.BORDER_TOP_FALLOFF_DP, stops[1].fraction * height, 1e-3f)
            assertEquals(serein.BORDER_SIDE_ALPHA, stops[1].alpha)
            assertEquals(serein.BORDER_BOTTOM_RISE_DP, (1f - stops[stops.size - 2].fraction) * height, 1e-3f)
            assertEquals(serein.BORDER_BOTTOM_ALPHA, stops.last().alpha)
        }
    }

    @Test
    fun theBorderRampStaysOrderedEvenOnASurfaceShorterThanItsRamps() {
        listOf(1f, 10f, 24f, 39f, 40f, 48f).forEach { height ->
            val fractions = serein.borderStops(height).map { it.fraction }
            assertEquals(0f, fractions.first())
            assertEquals(1f, fractions.last())
            fractions.zipWithNext().forEach { (a, b) -> assertTrue(a < b, "$height: $fractions") }
        }
    }

    @Test
    fun theScaleMultipliesEveryAlphaAndIsClamped() {
        val stops = serein.borderStops(200f, scale = 0.5f)
        assertEquals(serein.BORDER_TOP_ALPHA * 0.5f, stops.first().alpha, 1e-6f)
        assertEquals(1f, serein.borderStops(200f, scale = 100f).first().alpha)
    }

    @Test
    fun aTintStripIsFixedExceptOnAVeryShortSurface() {
        assertEquals(7f, serein.tintStripHeightDp(7f, 48f))
        assertEquals(7f, serein.tintStripHeightDp(7f, 400f))
        assertEquals(2.2f, serein.tintStripHeightDp(7f, 10f), 1e-6f)
    }

    @Test
    fun lightGlassBoostsTheEdgeLightsAndDarkGlassDoesNot() {
        assertEquals(1f, serein.lightSurfaceBoost(0.1f))
        assertEquals(serein.LIGHT_SURFACE_BOOST, serein.lightSurfaceBoost(0.9f))
        assertTrue(serein.lightSurfaceBoost(0.5f) in 1f..serein.LIGHT_SURFACE_BOOST)
    }

    @Test
    fun lightnessRunsFromDarkToFullyLight() {
        assertEquals(0f, serein.lightness(0.1f))
        assertEquals(1f, serein.lightness(0.95f))
        assertEquals(0.5f, serein.lightness(0.5f), 1e-6f)
    }

    @Test
    fun aClearFillIsSeenAsTheWallpaperAndAnOpaqueOneAsItself() {
        assertEquals(serein.ASSUMED_WALLPAPER_LUMINANCE, serein.seenLuminance(0.9f, 0f))
        assertEquals(0.9f, serein.seenLuminance(0.9f, 1f))
    }
}
