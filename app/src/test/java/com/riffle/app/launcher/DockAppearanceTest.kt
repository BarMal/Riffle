package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class DockAppearanceTest {
    @Test
    fun mapsEachDockEffectToARestrainedMaterialTreatment() {
        assertEquals(
            DockAppearance(elevationDp = 0, outlineWidthDp = 0),
            dockAppearanceSpec(DockVisualEffect.FLAT),
        )
        assertEquals(
            DockAppearance(elevationDp = 6, outlineWidthDp = 0),
            dockAppearanceSpec(DockVisualEffect.ELEVATED),
        )
        assertEquals(
            DockAppearance(elevationDp = 0, outlineWidthDp = 1),
            dockAppearanceSpec(DockVisualEffect.OUTLINED),
        )
    }

    @Test
    fun roundTripsDockVisualEffect() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(visualEffect = DockVisualEffect.ELEVATED),
            )

        assertEquals(DockVisualEffect.ELEVATED, decodeHomeLayout(encodeHomeLayout(layout)).dock.visualEffect)
    }
}
