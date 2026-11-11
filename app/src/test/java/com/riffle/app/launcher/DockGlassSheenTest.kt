package com.riffle.app.launcher

import androidx.compose.ui.graphics.Color
import com.riffle.core.domain.launcher.home.DockVisualEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockGlassSheenTest {
    @Test
    fun scalesEachLayerByThePullAndItsStrength() {
        assertEquals(0.12f, dockGlassSheenAlpha(layerAlpha = 0.12f, scale = 1f), 1e-6f)
        assertEquals(0.06f, dockGlassSheenAlpha(layerAlpha = 0.12f, scale = 0.5f), 1e-6f)
        assertEquals(0.24f, dockGlassSheenAlpha(layerAlpha = 0.12f, scale = 2f), 1e-6f)
    }

    @Test
    fun neverDrawsPastTheEndsOfTheAlphaRange() {
        assertEquals(0f, dockGlassSheenAlpha(layerAlpha = 0.12f, scale = -0.2f), 1e-6f)
        assertEquals(1f, dockGlassSheenAlpha(layerAlpha = 0.6f, scale = 3f), 1e-6f)
    }

    @Test
    fun aDarkDockGetsTheDesktopValues() {
        val strength = dockGlassSheenStrength(DockVisualEffect.FLAT, Color(0xFF202124))

        assertEquals(DockGlassSheenStrength(highlight = 1f, shadow = 1f, border = 1f), strength)
    }

    @Test
    fun aLightDockGetsStrongerHighlightsAndShadow() {
        val dark = dockGlassSheenStrength(DockVisualEffect.FLAT, Color(0xFF202124))
        val light = dockGlassSheenStrength(DockVisualEffect.FLAT, Color(0xFFE8EAED))

        assertTrue(light.highlight > dark.highlight)
        assertTrue(light.shadow > dark.shadow)
        assertTrue(light.border > dark.border)
    }

    @Test
    fun aClearDockIsTreatedAsDarkGlassWhateverItsColour() {
        val clearLight = dockGlassSheenStrength(DockVisualEffect.FLAT, Color(0xFFE8EAED).copy(alpha = 0f))

        assertEquals(DockGlassSheenStrength(highlight = 1f, shadow = 1f, border = 1f), clearLight)
    }

    @Test
    fun elevatedRaisesHighlightsAndDeepensTheShadow() {
        val flat = dockGlassSheenStrength(DockVisualEffect.FLAT, Color(0xFF202124))
        val elevated = dockGlassSheenStrength(DockVisualEffect.ELEVATED, Color(0xFF202124))

        assertTrue(elevated.highlight > flat.highlight)
        assertTrue(elevated.shadow > flat.shadow)
        assertEquals(flat.border, elevated.border, 1e-6f)
    }

    @Test
    fun outlinedLeavesTheBorderToItsOwnOutline() {
        assertEquals(0f, dockGlassSheenStrength(DockVisualEffect.OUTLINED, Color(0xFF202124)).border, 1e-6f)
    }
}
