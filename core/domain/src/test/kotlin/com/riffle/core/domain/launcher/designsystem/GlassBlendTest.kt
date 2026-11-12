package com.riffle.core.domain.launcher.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassBlendTest {
    private val transparent = GlassBlend.Premultiplied(0f, 0f, 0f, 0f)
    private val red = GlassBlend.Premultiplied(1f, 0f, 0f, 1f)

    @Test
    fun overOpaqueContentTheTintIsAPlainMix() {
        val out = GlassBlend.tint(red, 1f, 1f, 1f, 0.4f)
        assertEquals(1f, out.a, 1e-6f)
        assertEquals(1f, out.r, 1e-6f)
        assertEquals(0.4f, out.g, 1e-6f)
        assertEquals(0.4f, out.b, 1e-6f)
    }

    @Test
    fun overATransparentBackdropTheResultIsTheTintAtItsAlpha() {
        val out = GlassBlend.tint(transparent, 0.2f, 0.4f, 0.6f, 0.5f)
        assertEquals(0.5f, out.a, 1e-6f)
        assertEquals(0.1f, out.r, 1e-6f)
        assertEquals(0.2f, out.g, 1e-6f)
        assertEquals(0.3f, out.b, 1e-6f)
        // The screen then shows the wallpaper through it: red under half-opaque (0.2, 0.4, 0.6) tint.
        val shown = out.overOpaque(1f, 0f, 0f)
        assertEquals(0.6f, shown.first, 1e-6f)
        assertEquals(0.2f, shown.second, 1e-6f)
        assertEquals(0.3f, shown.third, 1e-6f)
    }

    @Test
    fun zeroTintAlphaOverTheWallpaperIsFullyClear() {
        val out = GlassBlend.shade(transparent, 1f, 1f, 1f, 0.4f, 0f, 0f)
        assertEquals(0f, out.a, 1e-6f)
        assertEquals(0f, out.r, 1e-6f)
    }

    @Test
    fun effectiveAlphaInterpolatesByBackdropAlphaAndIsClamped() {
        assertEquals(0.9f, GlassBlend.effectiveTintAlpha(0f, 0.4f, 0.9f), 1e-6f)
        assertEquals(0.4f, GlassBlend.effectiveTintAlpha(1f, 0.4f, 0.9f), 1e-6f)
        assertEquals(0.65f, GlassBlend.effectiveTintAlpha(0.5f, 0.4f, 0.9f), 1e-6f)
        assertEquals(0.4f, GlassBlend.effectiveTintAlpha(7f, 0.4f, 0.9f), 1e-6f)
        assertEquals(1f, GlassBlend.effectiveTintAlpha(0f, 0.4f, 3f), 1e-6f)
    }

    @Test
    fun lightKeepsAlphaOverOpaqueContentAndRaisesItOverTransparent() {
        val opaque = GlassBlend.addLight(GlassBlend.Premultiplied(0.5f, 0.5f, 0.5f, 1f), 0.2f)
        assertEquals(1f, opaque.a, 1e-6f)
        assertEquals(0.7f, opaque.r, 1e-6f)
        val clear = GlassBlend.addLight(transparent, 0.2f)
        assertEquals(0.2f, clear.a, 1e-6f)
        assertEquals(0.2f, clear.r, 1e-6f)
    }

    @Test
    fun theResultIsAlwaysValidPremultiplied() {
        listOf(transparent, red, GlassBlend.Premultiplied(0.1f, 0.1f, 0.1f, 0.2f)).forEach { backdrop ->
            listOf(0f, 0.3f, 1f).forEach { light ->
                val out = GlassBlend.shade(backdrop, 1f, 1f, 1f, 0.5f, 0.7f, light)
                assertTrue(out.r <= out.a + 1e-6f && out.g <= out.a + 1e-6f && out.b <= out.a + 1e-6f, "$out")
                assertTrue(out.a in 0f..1f)
            }
        }
    }

    @Test
    fun everyWeightKeepsTextReadableOverTheWorstWallpaper() {
        val lightSurface = Triple(1f, 1f, 1f)
        val darkText = Triple(0x1C / 255f, 0x1B / 255f, 0x1F / 255f)
        val darkSurface = Triple(0x14 / 255f, 0x12 / 255f, 0x18 / 255f)
        val lightText = Triple(0xE6 / 255f, 0xE1 / 255f, 0xE5 / 255f)
        GlassWeight.entries.forEach { weight ->
            val tokens = GlassMaterialTokens.forWeight(weight)
            // Across the whole frost range the user can pick, over a transparent backdrop.
            listOf(0f, 0.5f, 1f).forEach { frost ->
                val alpha = tokens.shaderTintAlphaOverWallpaper(frost)
                assertTrue(
                    GlassBlend.worstCaseTextContrast(lightSurface, darkText, alpha) >= GlassBlend.MINIMUM_TEXT_CONTRAST,
                    "$weight light at frost $frost, alpha $alpha",
                )
                assertTrue(
                    GlassBlend.worstCaseTextContrast(darkSurface, lightText, alpha) >= GlassBlend.MINIMUM_TEXT_CONTRAST,
                    "$weight dark at frost $frost, alpha $alpha",
                )
            }
        }
    }

    @Test
    fun withoutTheFloorThinGlassWouldNotBeReadableOverTheWallpaper() {
        val thin = GlassMaterialTokens.thin
        val raw = thin.shaderTintAlpha(thin.effectiveFrost(0.45f))
        val darkText = Triple(0x1C / 255f, 0x1B / 255f, 0x1F / 255f)
        val contrast = GlassBlend.worstCaseTextContrast(Triple(1f, 1f, 1f), darkText, raw)
        assertTrue(contrast < GlassBlend.MINIMUM_TEXT_CONTRAST)
    }
}
