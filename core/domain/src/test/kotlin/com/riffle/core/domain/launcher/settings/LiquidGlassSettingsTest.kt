package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidGlassSettingsTest {
    @Test
    fun defaultsAreConservativeNotMaximal() {
        val defaults = LiquidGlassSettings()

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
                liquidGlass = LiquidGlassSettings(frostStrengthPercent = 80),
                motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.ON),
            )

        val resolved = settings.resolveLiquidGlass()

        assertEquals(0.8f, resolved.frostStrengthFraction)
        assertTrue(resolved.reducedMotion)
    }

    @Test
    fun glassSamplesTheBackdropUnlessTransparencyIsReduced() {
        assertTrue(LauncherSettings().resolveLiquidGlass().samplesBackdrop)
        val reduced = LauncherSettings().withReducedTransparency(true)
        assertFalse(reduced.resolveLiquidGlass().samplesBackdrop)
    }

    @Test
    fun reducedTransparencyIsOffByDefaultAndResolvesFromTheTopLevelAppearanceSetting() {
        assertFalse(LauncherSettings().resolveLiquidGlass().reducedTransparency)

        val reduced = LauncherSettings().withReducedTransparency(true)

        assertTrue(reduced.appearance.reducedTransparency)
        assertTrue(reduced.resolveLiquidGlass().reducedTransparency)
    }

    @Test
    fun withReducedTransparencyKeepsTheAdaptiveStageCopiesInStep() {
        val reduced = LauncherSettings().withReducedTransparency(true)

        assertTrue(reduced.cards.adaptiveStageAppearance.motion.reducedTransparency)
        assertTrue(reduced.cards.unfoldedAppearance.motion.reducedTransparency)
        assertFalse(reduced.withReducedTransparency(false).cards.adaptiveStageAppearance.motion.reducedTransparency)
        assertFalse(reduced.withReducedTransparency(false).appearance.reducedTransparency)
    }

    @Test
    fun spotlightFlowDefaultsToAGentleFollowAndClampsToItsRange() {
        assertEquals(DEFAULT_SPOTLIGHT_FLOW_MILLIS, LiquidGlassSettings().spotlightFlowMillis)
        val tooFast = LiquidGlassSettings(spotlightFlowMillis = -100).coerced()
        val tooSlow = LiquidGlassSettings(spotlightFlowMillis = 9000).coerced()

        assertEquals(MIN_SPOTLIGHT_FLOW_MILLIS, tooFast.spotlightFlowMillis)
        assertEquals(MAX_SPOTLIGHT_FLOW_MILLIS, tooSlow.spotlightFlowMillis)
    }

    @Test
    fun resolveLiquidGlassCarriesTheSpotlightFlowInSeconds() {
        val settings = LauncherSettings(liquidGlass = LiquidGlassSettings(spotlightFlowMillis = 600))

        assertEquals(0.6f, settings.resolveLiquidGlass().spotlightFlowSeconds, 1e-6f)
    }
}
