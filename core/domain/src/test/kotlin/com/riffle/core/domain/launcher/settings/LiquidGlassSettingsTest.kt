package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidGlassSettingsTest {
    @Test
    fun resolveLiquidGlassFoldsInGlobalReducedMotion() {
        val settings =
            LauncherSettings(
                motion = MotionSettings(reducedMotionPreference = ReducedMotionPreference.ON),
            )

        assertTrue(settings.resolveLiquidGlass().reducedMotion)
    }

    @Test
    fun reducedTransparencyIsOffByDefaultAndResolvesFromTheTopLevelAppearanceSetting() {
        assertFalse(LauncherSettings().resolveLiquidGlass().reducedTransparency)

        val reduced = LauncherSettings().withReducedTransparency(true)

        assertTrue(reduced.appearance.reducedTransparency)
        assertTrue(reduced.resolveLiquidGlass().reducedTransparency)
    }

    @Test
    fun withReducedTransparencyCanBeTurnedBackOff() {
        val reduced = LauncherSettings().withReducedTransparency(true)

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
