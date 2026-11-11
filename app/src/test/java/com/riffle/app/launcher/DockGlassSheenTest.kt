package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DockGlassSheenTest {
    @Test
    fun drawsEachLayerAtItsOwnAlphaAtRest() {
        assertEquals(0.12f, dockGlassSheenAlpha(layerAlpha = 0.12f, fade = 1f), 1e-6f)
    }

    @Test
    fun fadesWithTheDockPull() {
        assertEquals(0.06f, dockGlassSheenAlpha(layerAlpha = 0.12f, fade = 0.5f), 1e-6f)
        assertEquals(0f, dockGlassSheenAlpha(layerAlpha = 0.12f, fade = 0f), 1e-6f)
    }

    @Test
    fun clampsAnOvershootingPull() {
        assertEquals(0.12f, dockGlassSheenAlpha(layerAlpha = 0.12f, fade = 1.4f), 1e-6f)
        assertEquals(0f, dockGlassSheenAlpha(layerAlpha = 0.12f, fade = -0.2f), 1e-6f)
    }
}
