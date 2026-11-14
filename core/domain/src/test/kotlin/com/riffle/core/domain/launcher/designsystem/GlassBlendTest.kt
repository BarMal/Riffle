package com.riffle.core.domain.launcher.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassBlendTest {
    @Test
    fun theFlatFillIsAPlainSourceOver() {
        val shown = GlassBlend.flatOver(Triple(0.2f, 0.4f, 0.6f), alpha = 0.5f, background = Triple(1f, 0f, 0f))

        assertEquals(0.6f, shown.first, 1e-6f)
        assertEquals(0.2f, shown.second, 1e-6f)
        assertEquals(0.3f, shown.third, 1e-6f)
    }

    @Test
    fun aClearFillShowsTheBackgroundAndAnOpaqueOneTheTint() {
        val background = Triple(0.9f, 0.1f, 0.3f)
        val tint = Triple(0.2f, 0.2f, 0.2f)

        assertEquals(background, GlassBlend.flatOver(tint, 0f, background))
        assertEquals(tint, GlassBlend.flatOver(tint, 1f, background))
        assertEquals(tint, GlassBlend.flatOver(tint, 3f, background))
    }

    @Test
    fun lightTextOnAnyWeightIsReadableOverADarkWallpaper() {
        // The owner's fills are light and see-through, signed off over a dark wallpaper (luminance 0.02):
        // body text must still pass there.
        val lightText = Triple(0xE6 / 255f, 0xE1 / 255f, 0xE5 / 255f)
        val wallpaper = Triple(0.02f, 0.02f, 0.03f)
        GlassWeight.entries.forEach { weight ->
            val tokens = GlassMaterialTokens.forWeight(weight)
            val base = tokens.darkBaseRgb
            val rgb = Triple((base shr 16 and 0xFF) / 255f, (base shr 8 and 0xFF) / 255f, (base and 0xFF) / 255f)
            val shown = GlassBlend.flatOver(rgb, tokens.fillAlpha, wallpaper)
            val contrast =
                GlassBlend.contrast(
                    GlassBlend.luminance(lightText.first, lightText.second, lightText.third),
                    GlassBlend.luminance(shown.first, shown.second, shown.third),
                )
            assertTrue(contrast >= GlassBlend.MINIMUM_TEXT_CONTRAST, "$weight contrast $contrast")
        }
    }

    @Test
    fun theThickFillKeepsDenseTextReadableOverAMidGreyWallpaperButTheThinOnesDoNot() {
        // This is why dense text sits on Thick: over a mid-grey wallpaper Regular and Thin lose it.
        val lightText = Triple(0xE6 / 255f, 0xE1 / 255f, 0xE5 / 255f)
        val textL = GlassBlend.luminance(lightText.first, lightText.second, lightText.third)

        fun over(weight: GlassWeight): Float {
            val tokens = GlassMaterialTokens.forWeight(weight)
            val base = tokens.darkBaseRgb
            val rgb = Triple((base shr 16 and 0xFF) / 255f, (base shr 8 and 0xFF) / 255f, (base and 0xFF) / 255f)
            val shown = GlassBlend.flatOver(rgb, tokens.fillAlpha, Triple(0.5f, 0.5f, 0.5f))
            return GlassBlend.contrast(textL, GlassBlend.luminance(shown.first, shown.second, shown.third))
        }
        assertTrue(over(GlassWeight.Thick) >= GlassBlend.MINIMUM_TEXT_CONTRAST)
        assertTrue(over(GlassWeight.Regular) < GlassBlend.MINIMUM_TEXT_CONTRAST)
    }

    @Test
    fun aMuchThinnerFillWouldNotBeReadableOverTheWallpaper() {
        val darkText = Triple(0x1C / 255f, 0x1B / 255f, 0x1F / 255f)
        val contrast = GlassBlend.worstCaseTextContrast(Triple(1f, 1f, 1f), darkText, 0.4f)

        assertTrue(contrast < GlassBlend.MINIMUM_TEXT_CONTRAST)
    }
}
