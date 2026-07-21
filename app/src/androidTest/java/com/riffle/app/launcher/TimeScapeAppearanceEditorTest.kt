package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.TimeScapeAppearancePreset
import com.riffle.core.domain.launcher.settings.TimeScapeRendererCapabilities
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
            "Motion and haptics",
            "Accessibility fallbacks",
        ).forEach { title -> composeRule.onNodeWithText(title).assertExists() }
    }

    @Test
    fun appliesPresetsAndConfirmsResetThroughAtomicActions() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                TimeScapeAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Appearance preset: Flat").performClick()
        composeRule.runOnIdle {
            val action = actions.single() as LauncherShellAction.UpdateTimeScapeAppearance
            assertEquals(TimeScapeAppearancePreset.FLAT_REDUCED_DEPTH, action.appearance.preset)
        }
        composeRule.onNodeWithText("Reset TimeScape appearance").performClick()
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
}
