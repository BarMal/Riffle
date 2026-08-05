package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearancePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveStageAppearancePageContentTest {
    @Test
    fun presetActionReplacesTheCompleteProfileWithTheSelectedPreset() {
        val action = adaptiveStageAppearancePresetAction(AdaptiveStageAppearancePreset.FLAT_REDUCED_DEPTH)

        assertEquals(AdaptiveStageAppearancePreset.FLAT_REDUCED_DEPTH, action.appearance.preset)
        assertEquals(2, action.appearance.geometry.visibleDepth)
        assertEquals(0, action.appearance.surface.blurStrengthPercent)
        assertEquals(0, action.appearance.motion.travelIntensityPercent)
    }

    @Test
    fun presetActionAppliesTheWarmGlassProfile() {
        val action = adaptiveStageAppearancePresetAction(AdaptiveStageAppearancePreset.WARM_GLASS)

        assertEquals(AdaptiveStageAppearancePreset.WARM_GLASS, action.appearance.preset)
    }
}
