package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.TimeScapeAppearancePreset
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeScapeAppearancePageContentTest {
    @Test
    fun presetActionReplacesTheCompleteProfileWithTheSelectedPreset() {
        val action = timeScapeAppearancePresetAction(TimeScapeAppearancePreset.FLAT_REDUCED_DEPTH)

        assertEquals(TimeScapeAppearancePreset.FLAT_REDUCED_DEPTH, action.appearance.preset)
        assertEquals(2, action.appearance.geometry.visibleDepth)
        assertEquals(0, action.appearance.surface.blurStrengthPercent)
        assertEquals(0, action.appearance.motion.travelIntensityPercent)
    }
}
