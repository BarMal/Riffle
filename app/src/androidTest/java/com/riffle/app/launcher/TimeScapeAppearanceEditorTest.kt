package com.riffle.app.launcher

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.TimeScapeAppearancePreset
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.TimeScapeEasing
import com.riffle.core.domain.launcher.settings.TimeScapeHapticStrength
import com.riffle.core.domain.launcher.settings.TimeScapeRendererCapabilities
import com.riffle.core.domain.launcher.settings.TimeScapeSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeScapeAppearanceEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exposesTheLivePreviewAndEveryEditorSection() {
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("TimeScape live preview").assertExists()
        listOf(
            "Preset and reset",
            "Card geometry",
            "Stack and spline",
            "Surface and glass",
            "Colour and content",
            "Motion",
            "Accessibility fallbacks",
        ).forEach { title -> composeRule.onNodeWithText(title).assertExists() }
        listOf(
            "Settle duration",
            "Enter duration",
            "Exit duration",
            "Expand duration",
            "Easing",
            "Spring bounciness",
            "Haptic strength",
        ).forEach { label -> composeRule.onNodeWithText(label).assertExists() }
    }

    @Test
    fun exposesPresetControlsAndConfirmsResetThroughAnAtomicAction() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithTag("timescape-preset-FLAT_REDUCED_DEPTH").assertExists()
        composeRule.onNodeWithText("Reset TimeScape appearance").performScrollTo().performClick()
        composeRule.onNodeWithText("Reset TimeScape appearance?").assertExists()
        composeRule.onNodeWithContentDescription("Confirm TimeScape reset").performClick()
        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateTimeScapeAppearance
            assertEquals(TimeScapeAppearancePreset.MODERN_TIMESCAPE, action.appearance.preset)
        }
    }

    @Test
    fun previewUsesTheLauncherWideReducedMotionPreference() {
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppearancePageContent(
                    state =
                        LauncherShellState(
                            launcherSettings = LauncherSettings(motion = MotionSettings(reducedMotion = true)),
                        ).settingsSurfaceState(),
                    onAction = {},
                )
            }
        }

        composeRule
            .onNode(SemanticsMatcher.expectValue(CardStackMotionModeKey, CardStackMotionMode.SNAP))
            .assertExists()
    }

    @Test
    fun mapsAccessibleSliderValuesToThePersistedProfileBoundary() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Card aspect ratio")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(100f))
            }

        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateTimeScapeAppearance
            assertEquals(100, action.appearance.geometry.cardAspectRatioPercent)
        }
    }

    @Test
    fun mapsAccessibleMotionControlsToThePersistedProfile() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Settle duration")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(600f))
            }
        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateTimeScapeAppearance
            assertEquals(600, action.appearance.motion.settleDurationMillis)
        }
        composeRule.onNodeWithContentDescription("Easing: Standard").performClick()
        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateTimeScapeAppearance
            assertEquals(TimeScapeEasing.STANDARD, action.appearance.motion.easing)
        }
        composeRule.onNodeWithContentDescription("Haptic strength: Strong").performClick()

        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateTimeScapeAppearance
            assertEquals(TimeScapeHapticStrength.STRONG, action.appearance.motion.hapticStrength)
        }
    }

    @Test
    fun displaysTheActualUnavailableBlurFallbackInThePreview() {
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = {},
                    rendererCapabilities = TimeScapeRendererCapabilities(supportsBlur = false),
                )
            }
        }

        composeRule
            .onNodeWithText("Blur is unavailable on this device; the preview shows the opaque fallback.")
            .assertExists()
    }

    @Test
    fun previewAppliesItsInjectedBlurFallbackToEveryRenderedCard() {
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppearancePreview(
                    appearance = TimeScapeAppearanceSettings(surface = TimeScapeSurface(blurStrengthPercent = 72)),
                    globalReducedMotion = false,
                    rendererCapabilities = TimeScapeRendererCapabilities(supportsBlur = false),
                    modifier = Modifier.requiredSize(360.dp),
                )
            }
        }

        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(TimeScapeCardBlurStrengthKey, 0))
            .assertCountEquals(3)
    }
}
