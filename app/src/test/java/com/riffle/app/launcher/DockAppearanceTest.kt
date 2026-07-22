package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class DockAppearanceTest {
    @Test
    fun mapsEachDockEffectToARestrainedMaterialTreatment() {
        assertEquals(
            DockAppearance(elevationDp = 0, outlineWidthDp = 0, cornerRadiusDp = 24),
            dockAppearanceSpec(DockVisualEffect.FLAT, cornerRadiusDp = 24),
        )
        assertEquals(
            DockAppearance(elevationDp = 6, outlineWidthDp = 0, cornerRadiusDp = 24),
            dockAppearanceSpec(DockVisualEffect.ELEVATED, cornerRadiusDp = 24),
        )
        assertEquals(
            DockAppearance(elevationDp = 0, outlineWidthDp = 1, cornerRadiusDp = 24),
            dockAppearanceSpec(DockVisualEffect.OUTLINED, cornerRadiusDp = 24),
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

    @Test
    fun usesConfiguredCornerRadiusForEveryDockEffect() {
        assertEquals(
            DockAppearance(elevationDp = 0, outlineWidthDp = 1, cornerRadiusDp = 12),
            dockAppearanceSpec(DockVisualEffect.OUTLINED, cornerRadiusDp = 12),
        )
    }
}
