@file:Suppress("TooManyFunctions")

package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.HomeDestination
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageFanDirection
import com.riffle.core.domain.launcher.settings.AdaptiveStageGeometry
import com.riffle.core.domain.launcher.settings.AdaptiveStageMotion
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A/B screenshot pairs isolating specific settings-audit hypotheses raised while investigating
 * whether AdaptiveStage's card-geometry/motion fields are all independently visible, or whether
 * some duplicate another field's effect closely enough that a viewer can't tell them apart. Each
 * pair holds every other field at a fixed, non-default value so the one axis under test is the
 * only thing that can explain a visual difference between the two renders. These were reasoned
 * out from the resolution math (`resolveCardStack`), not from an actual rendered comparison --
 * that's what this test exists to check.
 *
 * Compare each pair's two captures by eye (or with an image diff) once Roborazzi has run:
 * - `arcWidthZero...` vs `arcWidthMax...`: if near-identical, arcWidthPercent is inert once
 *   horizontalOffsetPercent is already high, as the resolution math predicts.
 * - `curveMaxZeroSpacing...` vs `spacingMaxZeroCurve...`: if near-identical, curvePercent and
 *   verticalSpacingPercent are not perceptually distinguishable axes.
 * - `rotationViaDegrees...` vs `rotationViaIntensity...`: these are constructed so the resolved
 *   angle (`rotationDegrees * rotationIntensityPercent / 100`) is identical (20 degrees) via two
 *   different (degrees, intensity) pairs -- if the captures match, the two fields are one dial.
 * - `overlapZero...` vs `overlapMax...`: if the outer rings look similarly faded in both, that
 *   fade is coming from the always-on edge-fade curve, not from `overlapPercent`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class AdaptiveStageAppearanceSweepScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun arcWidthZeroAtHighHorizontalOffsetCompact() {
        render(
            geometry(
                horizontalOffsetPercent = 100,
                arcWidthPercent = 0,
                fanDirection = AdaptiveStageFanDirection.END,
            ),
        )
    }

    @Test
    fun arcWidthMaxAtHighHorizontalOffsetCompact() {
        render(
            geometry(
                horizontalOffsetPercent = 100,
                arcWidthPercent = 100,
                fanDirection = AdaptiveStageFanDirection.END,
            ),
        )
    }

    @Test
    fun curveMaxAtZeroSpacingCompact() {
        render(geometry(curvePercent = 100, verticalSpacingPercent = 0, visibleDepth = 4))
    }

    @Test
    fun spacingMaxAtZeroCurveCompact() {
        render(geometry(curvePercent = 0, verticalSpacingPercent = 100, visibleDepth = 4))
    }

    @Test
    fun rotationViaDegreesCompact() {
        // Resolved tilt: 20 * 100 / 100 = 20 degrees.
        render(geometry(rotationDegrees = 20), motion = motion(rotationIntensityPercent = 100))
    }

    @Test
    fun rotationViaIntensityCompact() {
        // Resolved tilt: 40 * 50 / 100 = 20 degrees -- same as above via a different (degrees, intensity) split.
        render(geometry(rotationDegrees = 40), motion = motion(rotationIntensityPercent = 50))
    }

    @Test
    fun overlapZeroAtDeepStackCompact() {
        render(geometry(overlapPercent = 0, visibleDepth = 6))
    }

    @Test
    fun overlapMaxAtDeepStackCompact() {
        render(geometry(overlapPercent = 100, visibleDepth = 6))
    }

    @Test
    fun pageVerticalOffsetPositiveCompact() {
        render(pageVerticalOffsetDp = 120)
    }

    @Test
    fun pageVerticalOffsetNegativeCompact() {
        render(pageVerticalOffsetDp = -120)
    }

    /**
     * The closest reachable approximation of the reference "Calm" launcher's own default
     * timescape look, mapped field-by-field from Calm's `applyTimescapeStackPreset()` values (see
     * the settings-audit report this test accompanies) -- a single reference capture, not an A/B
     * pair, to gut-check against Calm's own screenshots.
     */
    @Test
    fun calmTimescapeMatchCompact() {
        render(
            geometry(
                arcWidthPercent = 78,
                aboveFocusDepth = 3,
                verticalSpacingPercent = 35,
                visibleDepth = 4,
                focusedGapDp = 30,
                focusedScalePercent = 105,
                rotationDegrees = 6,
                horizontalOffsetPercent = 90,
                fanDirection = AdaptiveStageFanDirection.END,
                overlapPercent = 85,
                stackPeakPercent = 20,
            ),
            motion = motion(magnetStrengthPercent = 82, rotationIntensityPercent = 100),
        )
    }

    @Suppress("LongParameterList")
    private fun geometry(
        overlapPercent: Int = AdaptiveStageGeometry().overlapPercent,
        verticalSpacingPercent: Int = AdaptiveStageGeometry().verticalSpacingPercent,
        horizontalOffsetPercent: Int = AdaptiveStageGeometry().horizontalOffsetPercent,
        arcWidthPercent: Int = AdaptiveStageGeometry().arcWidthPercent,
        curvePercent: Int = AdaptiveStageGeometry().curvePercent,
        rotationDegrees: Int = AdaptiveStageGeometry().rotationDegrees,
        visibleDepth: Int = AdaptiveStageGeometry().visibleDepth,
        aboveFocusDepth: Int = AdaptiveStageGeometry().aboveFocusDepth,
        focusedGapDp: Int = AdaptiveStageGeometry().focusedGapDp,
        focusedScalePercent: Int = AdaptiveStageGeometry().focusedScalePercent,
        stackPeakPercent: Int = AdaptiveStageGeometry().stackPeakPercent,
        fanDirection: AdaptiveStageFanDirection = AdaptiveStageGeometry().fanDirection,
    ): AdaptiveStageGeometry =
        AdaptiveStageGeometry(
            overlapPercent = overlapPercent,
            verticalSpacingPercent = verticalSpacingPercent,
            horizontalOffsetPercent = horizontalOffsetPercent,
            arcWidthPercent = arcWidthPercent,
            curvePercent = curvePercent,
            rotationDegrees = rotationDegrees,
            visibleDepth = visibleDepth,
            aboveFocusDepth = aboveFocusDepth,
            focusedGapDp = focusedGapDp,
            focusedScalePercent = focusedScalePercent,
            stackPeakPercent = stackPeakPercent,
            fanDirection = fanDirection,
        )

    private fun motion(
        rotationIntensityPercent: Int = AdaptiveStageMotion().rotationIntensityPercent,
        magnetStrengthPercent: Int = AdaptiveStageMotion().magnetStrengthPercent,
    ): AdaptiveStageMotion =
        AdaptiveStageMotion(
            rotationIntensityPercent = rotationIntensityPercent,
            magnetStrengthPercent = magnetStrengthPercent,
        )

    private fun render(
        geometry: AdaptiveStageGeometry = AdaptiveStageGeometry(),
        motion: AdaptiveStageMotion = AdaptiveStageMotion(),
        pageVerticalOffsetDp: Int = 0,
    ) {
        val launcherSettings =
            LauncherSettings(
                cards =
                    CardsSettings(
                        adaptiveStageAppearance =
                            AdaptiveStageAppearanceSettings(geometry = geometry, motion = motion),
                        pageVerticalOffsetDp = pageVerticalOffsetDp,
                    ),
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = ScreenshotFixtures.cardsState(launcherSettings = launcherSettings),
                    appIconLoader = SolidColorAppIconLoader(),
                    adaptiveStageWindowLayout = screenshotWindowLayout(AdaptiveStagePosture.UNKNOWN),
                    adaptiveStageContext = AdaptiveStageInteractionContext(),
                    onAdaptiveStageContextChanged = {},
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
    }
}
