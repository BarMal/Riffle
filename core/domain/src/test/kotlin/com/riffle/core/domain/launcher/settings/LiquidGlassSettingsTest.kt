package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidGlassSettingsTest {
    @Test
    fun defaultsAreConservativeNotMaximal() {
        val defaults = LiquidGlassSettings()

        assertTrue(defaults.enabled)
        // Neither strength defaults to the maximum -- see LauncherSettings.kt's doc comment on why
        // (Apple's WWDC25 Liquid Glass had to walk back an over-transparent default twice).
        assertEquals(DEFAULT_LIQUID_GLASS_FROST_STRENGTH_PERCENT, defaults.frostStrengthPercent)
        assertEquals(DEFAULT_LIQUID_GLASS_REFRACTION_STRENGTH_PERCENT, defaults.refractionStrengthPercent)
        assertTrue(defaults.frostStrengthPercent < MAX_LIQUID_GLASS_STRENGTH_PERCENT)
        assertTrue(defaults.refractionStrengthPercent < MAX_LIQUID_GLASS_STRENGTH_PERCENT)
    }

    @Test
    fun coercedClampsBothStrengthsToTheirRange() {
        val outOfRange =
            LiquidGlassSettings(frostStrengthPercent = -20, refractionStrengthPercent = 250).coerced()

        assertEquals(MIN_LIQUID_GLASS_STRENGTH_PERCENT, outOfRange.frostStrengthPercent)
        assertEquals(MAX_LIQUID_GLASS_STRENGTH_PERCENT, outOfRange.refractionStrengthPercent)
    }

    @Test
    fun coercedLeavesInRangeValuesUntouched() {
        val inRange = LiquidGlassSettings(frostStrengthPercent = 60, refractionStrengthPercent = 10)

        assertEquals(inRange, inRange.coerced())
    }

    @Test
    fun resolveLiquidGlassFoldsInGlobalReducedMotion() {
        val settings =
            LauncherSettings(
                liquidGlass = LiquidGlassSettings(enabled = true, frostStrengthPercent = 80),
                motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.ON),
            )

        val resolved = settings.resolveLiquidGlass()

        assertTrue(resolved.enabled)
        assertEquals(0.8f, resolved.frostStrengthFraction)
        assertTrue(resolved.reducedMotion)
    }

    @Test
    fun resolveLiquidGlassRespectsDisabledMasterToggle() {
        val settings = LauncherSettings(liquidGlass = LiquidGlassSettings(enabled = false))

        assertFalse(settings.resolveLiquidGlass().enabled)
    }
}
