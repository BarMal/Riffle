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
    fun heavierWeightsAreFrostierAndMoreOpaque() {
        ordered.zipWithNext().forEach { (lighter, heavier) ->
            assertTrue(lighter.frostScale < heavier.frostScale)
            assertTrue(lighter.tintAlpha < heavier.tintAlpha)
            assertTrue(lighter.noBlurTintAlpha < heavier.noBlurTintAlpha)
            assertTrue(lighter.shaderTintBaseAlpha < heavier.shaderTintBaseAlpha)
            assertTrue(lighter.blurRadiusDp < heavier.blurRadiusDp)
            assertTrue(lighter.hairlineScale < heavier.hairlineScale)
        }
    }

    @Test
    fun onlyThickCarriesAContrastScrimAndOnlyRegularCastsAShadow() {
        assertEquals(0f, GlassMaterialTokens.thin.contentScrimAlpha)
        assertEquals(0f, GlassMaterialTokens.regular.contentScrimAlpha)
        assertTrue(GlassMaterialTokens.thick.contentScrimAlpha > 0f)
        assertEquals(0, GlassMaterialTokens.thin.shadowElevationDp)
        assertTrue(GlassMaterialTokens.regular.shadowElevationDp > 0)
        assertEquals(0, GlassMaterialTokens.thick.shadowElevationDp)
    }

    @Test
    fun everyAlphaStaysInUnitRange() {
        ordered.forEach { tokens ->
            listOf(
                tokens.tintAlpha,
                tokens.noBlurTintAlpha,
                tokens.shaderTintBaseAlpha,
                tokens.contentScrimAlpha,
                tokens.shaderTintAlpha(frost = 1f),
                tokens.shaderTintAlpha(frost = 0f),
            ).forEach { alpha -> assertTrue(alpha in 0f..1f, "alpha $alpha") }
        }
    }

    @Test
    fun regularKeepsTheShippedMaterialValues() {
        val regular = GlassMaterialTokens.regular
        assertEquals(0.55f, regular.tintAlpha)
        assertEquals(0.75f, regular.noBlurTintAlpha)
        assertEquals(24, regular.blurRadiusDp)
        assertEquals(0.35f + 0.45f * 0.45f, regular.shaderTintAlpha(frost = 0.45f), absoluteTolerance = 1e-6f)
    }

    @Test
    fun effectiveFrostIsScaledAndClamped() {
        assertEquals(1f, GlassMaterialTokens.thick.effectiveFrost(1f))
        assertEquals(0.7f, GlassMaterialTokens.thin.effectiveFrost(1f), absoluteTolerance = 1e-6f)
        assertEquals(0f, GlassMaterialTokens.regular.effectiveFrost(-1f))
    }

    @Test
    fun softwareTintFallsBackToTheStrongerScrimWithoutBlur() {
        ordered.forEach { tokens ->
            assertTrue(tokens.softwareTintAlpha(supportsBlur = false) > tokens.softwareTintAlpha(supportsBlur = true))
        }
    }

    @Test
    fun reducedTransparencyIsOpaqueWithAFaintSheen() {
        assertEquals(1f, GlassMaterialTokens.REDUCED_TRANSPARENCY_TINT_ALPHA)
        assertTrue(GlassMaterialTokens.REDUCED_TRANSPARENCY_SHEEN_SCALE in 0f..0.5f)
    }

    @Test
    fun overTheWallpaperNoWeightFallsBelowItsFloorAndHeavierIsNeverLighter() {
        ordered.forEach { tokens ->
            listOf(0f, 0.5f, 1f).forEach { frost ->
                assertTrue(tokens.shaderTintAlphaOverWallpaper(frost) >= tokens.shaderTintAlpha(frost))
                assertTrue(tokens.shaderTintAlphaOverWallpaper(frost) >= tokens.wallpaperTintAlpha)
            }
        }
        ordered.zipWithNext().forEach { (lighter, heavier) ->
            assertTrue(lighter.wallpaperTintAlpha <= heavier.wallpaperTintAlpha)
        }
        assertEquals(0.66f, GlassMaterialTokens.regular.shaderTintAlphaOverWallpaper(frost = 0.45f))
    }
}
