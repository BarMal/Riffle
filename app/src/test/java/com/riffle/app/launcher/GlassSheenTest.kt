package com.riffle.app.launcher

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSheenTest {
    @Test
    fun scalesEachLayerByThePullAndItsStrength() {
        assertEquals(0.12f, glassSheenAlpha(layerAlpha = 0.12f, scale = 1f), 1e-6f)
        assertEquals(0.06f, glassSheenAlpha(layerAlpha = 0.12f, scale = 0.5f), 1e-6f)
        assertEquals(0.24f, glassSheenAlpha(layerAlpha = 0.12f, scale = 2f), 1e-6f)
    }

    @Test
    fun neverDrawsPastTheEndsOfTheAlphaRange() {
        assertEquals(0f, glassSheenAlpha(layerAlpha = 0.12f, scale = -0.2f), 1e-6f)
        assertEquals(1f, glassSheenAlpha(layerAlpha = 0.6f, scale = 3f), 1e-6f)
    }

    @Test
    fun aDarkDockGetsTheDesktopValues() {
        val strength = glassSheenStrength(Color(0xFF202124))

        assertEquals(GlassSheenStrength(highlight = 1f, shadow = 1f, border = 1f), strength)
    }

    @Test
    fun aLightDockGetsStrongerHighlightsAndShadow() {
        val dark = glassSheenStrength(Color(0xFF202124))
        val light = glassSheenStrength(Color(0xFFE8EAED))

        assertTrue(light.highlight > dark.highlight)
        assertTrue(light.shadow > dark.shadow)
        assertTrue(light.border > dark.border)
    }

    @Test
    fun aClearDockIsTreatedAsDarkGlassWhateverItsColour() {
        val clearLight = glassSheenStrength(Color(0xFFE8EAED).copy(alpha = 0f))

        assertEquals(GlassSheenStrength(highlight = 1f, shadow = 1f, border = 1f), clearLight)
    }

    @Test
    fun scalingByAFactorScalesEveryGroupAndZeroSilencesTheSheen() {
        val strength = GlassSheenStrength(highlight = 2f, shadow = 1f, border = 1.5f)

        assertEquals(GlassSheenStrength(1f, 0.5f, 0.75f), strength.scaled(0.5f))
        assertEquals(GlassSheenStrength(0f, 0f, 0f), strength.scaled(0f))
        assertEquals(GlassSheenStrength(4f, 3f, 1.5f), strength.scaled(highlight = 2f, shadow = 3f, border = 1f))
    }
}
