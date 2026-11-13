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
            assertTrue(lighter.legibilityFloorAlpha <= heavier.legibilityFloorAlpha)
            assertTrue(lighter.edgeScale < heavier.edgeScale)
        }
    }

    @Test
    fun noWeightFillsBelowItsLegibilityFloor() {
        ordered.forEach { tokens -> assertTrue(tokens.fillAlpha >= tokens.legibilityFloorAlpha, "$tokens") }
    }

    @Test
    fun everyAlphaStaysInUnitRange() {
        ordered.forEach { tokens ->
            assertTrue(tokens.fillAlpha in 0f..1f)
            assertTrue(tokens.legibilityFloorAlpha in 0f..1f)
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
        assertEquals(0.66f, GlassMaterialTokens.thin.fillAlpha)
        assertEquals(0.72f, GlassMaterialTokens.regular.fillAlpha)
        assertEquals(0.80f, GlassMaterialTokens.thick.fillAlpha)
    }

    @Test
    fun reducedTransparencyIsOpaqueWithFaintEdgeLights() {
        assertEquals(1f, GlassMaterialTokens.REDUCED_TRANSPARENCY_FILL_ALPHA)
        assertTrue(GlassMaterialTokens.REDUCED_TRANSPARENCY_EDGE_SCALE in 0f..0.6f)
    }
}
