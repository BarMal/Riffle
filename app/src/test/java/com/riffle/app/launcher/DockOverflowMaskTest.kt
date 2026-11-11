package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DockOverflowMaskTest {
    @Test
    fun aFadingEdgeIsClearAtTheEdgeAndOpaqueAFadeWidthIn() {
        assertEquals(0f, dockOverflowMaskAlpha(0f, 200f, 20f, fadeStart = true, fadeEnd = false), 1e-6f)
        assertEquals(0.5f, dockOverflowMaskAlpha(10f, 200f, 20f, fadeStart = true, fadeEnd = false), 1e-6f)
        assertEquals(1f, dockOverflowMaskAlpha(20f, 200f, 20f, fadeStart = true, fadeEnd = false), 1e-6f)
    }

    @Test
    fun anEdgeThatDoesNotFadeStaysOpaqueRightUpToIt() {
        assertEquals(1f, dockOverflowMaskAlpha(0f, 200f, 20f, fadeStart = false, fadeEnd = true), 1e-6f)
        assertEquals(0f, dockOverflowMaskAlpha(200f, 200f, 20f, fadeStart = false, fadeEnd = true), 1e-6f)
        assertEquals(0.5f, dockOverflowMaskAlpha(190f, 200f, 20f, fadeStart = false, fadeEnd = true), 1e-6f)
    }

    @Test
    fun theMiddleOfTheRunIsAlwaysFullyVisible() {
        assertEquals(1f, dockOverflowMaskAlpha(100f, 200f, 20f, fadeStart = true, fadeEnd = true), 1e-6f)
    }
}
