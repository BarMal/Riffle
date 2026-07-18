package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockVisualEffect
import org.junit.Assert.assertEquals
import org.junit.Test

class DockAppearanceTest {
    @Test
    fun mapsEachDockEffectToARestrainedMaterialTreatment() {
        assertEquals(DockAppearanceSpec(elevationDp = 0, outlineWidthDp = 0), dockAppearanceSpec(DockVisualEffect.FLAT))
        assertEquals(DockAppearanceSpec(elevationDp = 6, outlineWidthDp = 0), dockAppearanceSpec(DockVisualEffect.ELEVATED))
        assertEquals(DockAppearanceSpec(elevationDp = 0, outlineWidthDp = 1), dockAppearanceSpec(DockVisualEffect.OUTLINED))
    }
}
