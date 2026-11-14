package com.riffle.core.domain.launcher.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassMaterialTokensTest {
    private val ordered = GlassWeight.entries.map(GlassMaterialTokens::forWeight)

    @Test
    fun everyWeightHasItsOwnTokens() {
        assertEquals(3, ordered.distinct().size)
    }

    @Test
    fun heavierWeightsAreMoreOpaqueAndLitMoreStrongly() {
        ordered.zipWithNext().forEach { (lighter, heavier) ->
            assertTrue(lighter.fillAlpha < heavier.fillAlpha)
            assertTrue(lighter.lightFillAlpha < heavier.lightFillAlpha)
            assertTrue(lighter.edgeScale < heavier.edgeScale)
        }
    }

    @Test
    fun everyAlphaStaysInUnitRange() {
        ordered.forEach { tokens ->
            assertTrue(tokens.fillAlpha in 0f..1f)
            assertTrue(tokens.lightFillAlpha in 0f..1f)
        }
        listOf(
            GlassEdgeTokens.BORDER_TOP_ALPHA,
            GlassEdgeTokens.BORDER_SIDE_ALPHA,
            GlassEdgeTokens.BORDER_BOTTOM_ALPHA,
            GlassEdgeTokens.SPECULAR_PEAK_ALPHA,
            GlassEdgeTokens.SPECULAR_SHOULDER_ALPHA,
            GlassEdgeTokens.TINT_TOP_ALPHA,
            GlassEdgeTokens.TINT_BOTTOM_ALPHA,
        ).forEach { alpha -> assertTrue(alpha in 0f..1f, "alpha $alpha") }
    }

    @Test
    fun theShippedFillAlphas() {
        assertEquals(0.12f, GlassMaterialTokens.thin.fillAlpha)
        assertEquals(0.16f, GlassMaterialTokens.regular.fillAlpha)
        assertEquals(0.56f, GlassMaterialTokens.thick.fillAlpha)
    }

    @Test
    fun theShippedDarkThemeBases() {
        assertEquals(0xC4CAEC, GlassMaterialTokens.regular.darkBaseRgb)
        assertEquals(0xC4CAEC, GlassMaterialTokens.thin.darkBaseRgb)
        assertEquals(0x3A3E60, GlassMaterialTokens.thick.darkBaseRgb)
    }

    @Test
    fun theEdgeLightsAreTheSignedOffOnes() {
        assertEquals(0.55f, GlassEdgeTokens.BORDER_TOP_ALPHA)
        assertEquals(0.14f, GlassEdgeTokens.BORDER_SIDE_ALPHA)
        assertEquals(0.18f, GlassEdgeTokens.SPECULAR_PEAK_ALPHA)
    }

    @Test
    fun aFillMixesTheCallersTintIntoTheWeightsBase() {
        val black = GlassMaterialTokens.fill(GlassWeight.Regular, tintRgb = 0x000000, darkTheme = true)
        // 20 percent of black, 80 percent of C4CAEC: each channel is 0.8 of the base's.
        assertTrue(kotlin.math.abs(0xC4 * 8 / 10 - ((black.rgb shr 16) and 0xFF)) <= 1)
        assertEquals(0.16f, black.alpha)
        val light = GlassMaterialTokens.fill(GlassWeight.Regular, tintRgb = 0xFFFFFF, darkTheme = false)
        assertEquals(GlassMaterialTokens.regular.lightFillAlpha, light.alpha)
    }

    @Test
    fun reducedTransparencyTakesTheThickFillInEveryWeight() {
        GlassWeight.entries.forEach { weight ->
            val reduced = GlassMaterialTokens.fill(weight, 0x101010, darkTheme = true, reducedTransparency = true)
            val thick = GlassMaterialTokens.fill(GlassWeight.Thick, 0x101010, darkTheme = true)
            assertEquals(thick.rgb, reduced.rgb)
            assertEquals(GlassMaterialTokens.REDUCED_TRANSPARENCY_FILL_ALPHA, reduced.alpha)
        }
    }

    @Test
    fun theDocksFillIsClearAtZeroTheThinFillAtItsDefaultAndOpaqueAtAHundred() {
        val thin = GlassMaterialTokens.thin.fillAlpha
        assertEquals(0f to 0f, GlassMaterialTokens.dockFill(0, 72, thin))
        assertEquals(thin to 0f, GlassMaterialTokens.dockFill(72, 72, thin))
        assertEquals(1f to 1f, GlassMaterialTokens.dockFill(100, 72, thin))
        val half = GlassMaterialTokens.dockFill(36, 72, thin)
        assertEquals(thin / 2, half.first, 1e-6f)
        assertTrue(GlassMaterialTokens.dockFill(86, 72, thin).first in thin..1f)
    }

    @Test
    fun mixingToleratesNanAndOutOfRangeShares() {
        assertEquals(0x102030, GlassMaterialTokens.mixRgb(0x102030, 0xFFFFFF, Float.NaN))
        assertEquals(0xFFFFFF, GlassMaterialTokens.mixRgb(0x102030, 0xFFFFFF, 7f))
    }

    @Test
    fun reducedTransparencyIsNearlyOpaqueWithFaintEdgeLights() {
        assertEquals(0.92f, GlassMaterialTokens.REDUCED_TRANSPARENCY_FILL_ALPHA)
        assertTrue(GlassMaterialTokens.REDUCED_TRANSPARENCY_FILL_ALPHA > GlassMaterialTokens.thick.fillAlpha)
        assertTrue(GlassMaterialTokens.REDUCED_TRANSPARENCY_EDGE_SCALE in 0f..0.6f)
    }
}
