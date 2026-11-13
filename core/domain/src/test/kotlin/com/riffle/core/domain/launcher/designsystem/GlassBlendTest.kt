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
    fun everyWeightKeepsTextReadableOverTheWorstWallpaper() {
        val lightSurface = Triple(1f, 1f, 1f)
        val darkText = Triple(0x1C / 255f, 0x1B / 255f, 0x1F / 255f)
        val darkSurface = Triple(0x14 / 255f, 0x12 / 255f, 0x18 / 255f)
        val lightText = Triple(0xE6 / 255f, 0xE1 / 255f, 0xE5 / 255f)
        GlassWeight.entries.forEach { weight ->
            val alpha = GlassMaterialTokens.forWeight(weight).fillAlpha
            assertTrue(
                GlassBlend.worstCaseTextContrast(lightSurface, darkText, alpha) >= GlassBlend.MINIMUM_TEXT_CONTRAST,
                "$weight light, alpha $alpha",
            )
            assertTrue(
                GlassBlend.worstCaseTextContrast(darkSurface, lightText, alpha) >= GlassBlend.MINIMUM_TEXT_CONTRAST,
                "$weight dark, alpha $alpha",
            )
        }
    }

    @Test
    fun theLegibilityFloorIsTheLeastAlphaThatStillPasses() {
        val lightSurface = Triple(1f, 1f, 1f)
        val darkText = Triple(0x1C / 255f, 0x1B / 255f, 0x1F / 255f)
        GlassWeight.entries.forEach { weight ->
            val floor = GlassMaterialTokens.forWeight(weight).legibilityFloorAlpha
            assertTrue(
                GlassBlend.worstCaseTextContrast(lightSurface, darkText, floor) >= GlassBlend.MINIMUM_TEXT_CONTRAST,
                "$weight floor $floor",
            )
        }
    }

    @Test
    fun aMuchThinnerFillWouldNotBeReadableOverTheWallpaper() {
        val darkText = Triple(0x1C / 255f, 0x1B / 255f, 0x1F / 255f)
        val contrast = GlassBlend.worstCaseTextContrast(Triple(1f, 1f, 1f), darkText, 0.4f)

        assertTrue(contrast < GlassBlend.MINIMUM_TEXT_CONTRAST)
    }
}
