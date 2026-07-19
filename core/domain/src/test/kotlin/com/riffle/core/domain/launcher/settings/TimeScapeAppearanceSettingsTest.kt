package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeScapeAppearanceSettingsTest {
    @Test
    fun modernDefaultIsCoherentAndResetIsDeterministic() {
        val modern = TimeScapeAppearanceSettings.modern()

        assertEquals(TimeScapeAppearancePreset.MODERN_TIMESCAPE, modern.preset)
        assertEquals(modern, modern.copy(geometry = modern.geometry.copy(visibleDepth = 1)).reset())
        assertTrue(modern.geometry.visibleDepth >= 1)
        assertTrue(modern.geometry.cardAspectRatioPercent in 55..100)
    }

    @Test
    fun flatPresetIsAppliedAtomically() {
        val flat = TimeScapeAppearanceSettings().applyPreset(TimeScapeAppearancePreset.FLAT_REDUCED_DEPTH)

        assertEquals(TimeScapeAppearancePreset.FLAT_REDUCED_DEPTH, flat.preset)
        assertEquals(0, flat.geometry.rotationDegrees)
        assertEquals(0, flat.surface.blurStrengthPercent)
        assertEquals(0, flat.motion.travelIntensityPercent)
    }

    @Test
    fun coercionKeepsEveryImportedValueInItsSafeRange() {
        val coerced =
            TimeScapeAppearanceSettings(
                version = 99,
                geometry = TimeScapeGeometry(cardAspectRatioPercent = -1, visibleDepth = 99, contentPaddingDp = -1),
                surface = TimeScapeSurface(blurStrengthPercent = 999, customBackgroundArgb = -1),
                typography = TimeScapeTypography(textScalePercent = 999, customAccentArgb = -1),
                motion = TimeScapeMotion(settleDurationMillis = -1, parallaxIntensityPercent = 999),
            ).coerce()

        assertEquals(CURRENT_TIMESCAPE_APPEARANCE_VERSION, coerced.version)
        assertEquals(55, coerced.geometry.cardAspectRatioPercent)
        assertEquals(6, coerced.geometry.visibleDepth)
        assertEquals(0, coerced.geometry.contentPaddingDp)
        assertEquals(100, coerced.surface.blurStrengthPercent)
        assertEquals(0, coerced.surface.customBackgroundArgb)
        assertEquals(130, coerced.typography.textScalePercent)
        assertEquals(80, coerced.motion.settleDurationMillis)
        assertEquals(50, coerced.motion.parallaxIntensityPercent)
    }

    @Test
    fun capabilityAndAccessibilityFallbacksDoNotRewriteIntent() {
        val stored =
            TimeScapeAppearanceSettings(
                surface = TimeScapeSurface(blurStrengthPercent = 60, textureIntensityPercent = 20),
                motion = TimeScapeMotion(reducedMotion = true, reducedTransparency = true),
            )

        val effective =
            stored.effectiveFor(
                TimeScapeRendererCapabilities(
                    supportsBlur = false,
                    supportsTexture = false,
                ),
            )

        assertEquals(60, stored.surface.blurStrengthPercent)
        assertEquals(0, effective.surface.blurStrengthPercent)
        assertEquals(0, effective.surface.textureIntensityPercent)
        assertEquals(0, effective.motion.enterDurationMillis)
        assertEquals(TimeScapeEasing.STANDARD, effective.motion.easing)
        assertEquals(0, effective.motion.springBouncinessPercent)
        assertEquals(0, effective.motion.travelIntensityPercent)
        assertFalse(effective.surface.glassTransparencyPercent > 0)
    }

    @Test
    fun resolvesAppearanceIntoBoundedCardStackTokens() {
        val viewport =
            TimeScapeViewportDp(
                widthDp = 800,
                heightDp = 1200,
                insets = TimeScapeInsetsDp(startDp = 24, topDp = 48, endDp = 24, bottomDp = 48),
            )
        val resolution = TimeScapeAppearanceSettings().resolveCardStack(viewport)
        val entries = resolution.layoutPolicy.entries(cardCount = 9, activeIndex = 4)
        val horizontalTravel = (viewport.safeWidthDp - resolution.cardWidthDp) / 2f
        val verticalTravel = (viewport.safeHeightDp - resolution.cardHeightDp) / 2f

        assertTrue(resolution.isUsable)
        assertTrue(resolution.cardWidthDp <= viewport.safeWidthDp)
        assertTrue(resolution.cardHeightDp <= viewport.safeHeightDp)
        assertTrue(entries.all { entry -> kotlin.math.abs(entry.offset) <= horizontalTravel })
        assertTrue(entries.all { entry -> kotlin.math.abs(entry.verticalOffset) <= verticalTravel })
        assertTrue(resolution.animation.reflowsStack)
    }

    @Test
    fun reducedMotionResolutionUsesStaticStackTokens() {
        val resolution =
            TimeScapeAppearanceSettings(motion = TimeScapeMotion(reducedMotion = true))
                .resolveCardStack(TimeScapeViewportDp(widthDp = 800, heightDp = 1200))
        val entries =
            resolution.layoutPolicy.entries(
                cardCount = 3,
                activeIndex = 1,
                reducedMotion = resolution.reducedMotion,
            )

        assertFalse(resolution.animation.animatesScale)
        assertFalse(resolution.animation.animatesRotation)
        assertEquals(0f, entries.maxOf { kotlin.math.abs(it.offset) })
        assertEquals(0f, entries.maxOf { kotlin.math.abs(it.rotationDegrees) })
    }
}
