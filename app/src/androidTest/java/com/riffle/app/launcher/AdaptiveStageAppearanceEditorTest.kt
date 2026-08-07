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
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneArrangement
import com.riffle.core.domain.launcher.cards.AdaptiveStageRailSide
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageEasing
import com.riffle.core.domain.launcher.settings.AdaptiveStageHapticStrength
import com.riffle.core.domain.launcher.settings.AdaptiveStageRendererCapabilities
import com.riffle.core.domain.launcher.settings.AdaptiveStageSurface
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.MotionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveStageAppearanceEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun selectTab(tab: AdaptiveStageAppearanceTab) {
        composeRule.onNodeWithTag("adaptive-stage-appearance-tab-${tab.name}").performScrollTo().performClick()
    }

    @Test
    fun exposesAPersistentPreviewAndEveryTabbedSection() {
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = {},
                )
            }
        }

        // The preview, target chooser, and reset row stay outside the tabs entirely.
        composeRule.onNodeWithContentDescription("Cards appearance preview").assertExists()
        composeRule.onNodeWithText("Appearance target").assertExists()
        composeRule.onNodeWithText("Reset appearance").assertExists()

        val tabToSectionTitles =
            mapOf(
                AdaptiveStageAppearanceTab.LAYOUT to listOf("Layout"),
                AdaptiveStageAppearanceTab.GEOMETRY to listOf("Card geometry", "Stack and stack"),
                AdaptiveStageAppearanceTab.SURFACE to listOf("Surface and glass"),
                AdaptiveStageAppearanceTab.COLOR to listOf("Colour and content"),
                AdaptiveStageAppearanceTab.MOTION to listOf("Motion"),
                AdaptiveStageAppearanceTab.ACCESSIBILITY to listOf("Accessibility fallbacks"),
            )
        tabToSectionTitles.forEach { (tab, titles) ->
            selectTab(tab)
            titles.forEach { title -> composeRule.onNodeWithText(title).assertExists() }
            // The preview stays visible no matter which tab is selected.
            composeRule.onNodeWithContentDescription("Cards appearance preview").assertExists()
        }

        selectTab(AdaptiveStageAppearanceTab.MOTION)
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
    fun selectingSplitDispatchesTheAdaptiveStagePaneArrangementAction() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                // The page's sections exceed the test window's height; without a bounded,
                // scrollable container the "Layout" tab's chips can be measured outside the real
                // window bounds, so performClick() ends up hitting whatever unrelated element
                // occupies that pixel instead -- matching the fixed-size wrapper the other tests
                // in this file already use.
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                    modifier = Modifier.requiredSize(360.dp, 800.dp),
                )
            }
        }

        selectTab(AdaptiveStageAppearanceTab.LAYOUT)
        composeRule.onNodeWithTag("adaptive-stage-pane-arrangement-${AdaptiveStagePaneArrangement.SPLIT.name}")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.SelectAdaptiveStagePaneArrangement
            assertEquals(AdaptiveStagePaneArrangement.SPLIT, action.arrangement)
        }
    }

    @Test
    fun selectingTopDispatchesTheAdaptiveStageRailSideAction() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                    modifier = Modifier.requiredSize(360.dp, 800.dp),
                )
            }
        }

        selectTab(AdaptiveStageAppearanceTab.LAYOUT)
        composeRule.onNodeWithTag("adaptive-stage-rail-side-${AdaptiveStageRailSide.TOP.name}")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.SelectAdaptiveStageRailSide
            assertEquals(AdaptiveStageRailSide.TOP, action.side)
        }
    }

    @Test
    fun noPresetPickerExistsAnywhereInTheEditor() {
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = {},
                )
            }
        }

        listOf("MODERN_ADAPTIVE_STAGE", "FLAT_REDUCED_DEPTH", "WARM_GLASS").forEach { presetName ->
            composeRule.onNodeWithTag("adaptive-stage-preset-$presetName").assertDoesNotExist()
        }
        composeRule.onNodeWithText("Appearance preset").assertDoesNotExist()
        composeRule.onNodeWithText("Preset and reset").assertDoesNotExist()
    }

    @Test
    fun resettingTheFoldedTargetDispatchesTheFoldedDefaultAtomically() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                    modifier = Modifier.requiredSize(360.dp, 800.dp),
                )
            }
        }

        composeRule.onNodeWithText("Reset Folded Cards appearance").performScrollTo().performClick()
        composeRule.onNodeWithText("Reset Folded Cards appearance?").assertExists()
        composeRule.onNodeWithContentDescription("Confirm Cards reset").performClick()
        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateAdaptiveStageAppearance
            assertEquals(AdaptiveStageAppearanceSettings.modern(), action.appearance)
        }
    }

    @Test
    fun switchingToTheUnfoldedTargetEditsAndResetsTheUnfoldedProfileIndependently() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                    modifier = Modifier.requiredSize(360.dp, 800.dp),
                )
            }
        }

        composeRule.onNodeWithTag("adaptive-stage-appearance-target-UNFOLDED").performScrollTo().performClick()
        composeRule.onNodeWithText("Reset Unfolded Cards appearance").performScrollTo().performClick()
        composeRule.onNodeWithText("Reset Unfolded Cards appearance?").assertExists()
        composeRule.onNodeWithContentDescription("Confirm Cards reset").performClick()
        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateUnfoldedAdaptiveStageAppearance
            assertEquals(AdaptiveStageAppearanceSettings.unfolded(), action.appearance)
        }
    }

    @Test
    fun previewUsesTheLauncherWideReducedMotionPreference() {
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
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
    fun editingAFieldWhileTheUnfoldedTargetIsSelectedDispatchesTheUnfoldedActionNotTheFoldedOne() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                    modifier = Modifier.requiredSize(360.dp, 800.dp),
                )
            }
        }

        composeRule.onNodeWithTag("adaptive-stage-appearance-target-UNFOLDED").performScrollTo().performClick()
        selectTab(AdaptiveStageAppearanceTab.GEOMETRY)
        // 80 (not 100): unfolded()'s own default cardAspectRatioPercent is already 100, so setting
        // it to 100 again would be a no-op the slider never dispatches a change for.
        composeRule
            .onNodeWithContentDescription("Card aspect ratio")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(80f))
            }

        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateUnfoldedAdaptiveStageAppearance
            assertEquals(80, action.appearance.geometry.cardAspectRatioPercent)
        }
    }

    @Test
    fun mapsAccessibleSliderValuesToThePersistedProfileBoundary() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                    modifier = Modifier.requiredSize(360.dp, 800.dp),
                )
            }
        }

        selectTab(AdaptiveStageAppearanceTab.GEOMETRY)
        composeRule
            .onNodeWithContentDescription("Card aspect ratio")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(100f))
            }

        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateAdaptiveStageAppearance
            assertEquals(100, action.appearance.geometry.cardAspectRatioPercent)
        }
    }

    @Test
    fun mapsAccessibleMotionControlsToThePersistedProfile() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                )
            }
        }

        selectTab(AdaptiveStageAppearanceTab.MOTION)
        composeRule
            .onNodeWithContentDescription("Settle duration")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(600f))
            }
        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateAdaptiveStageAppearance
            assertEquals(600, action.appearance.motion.settleDurationMillis)
        }
        composeRule
            .onNodeWithContentDescription("Easing: Standard")
            .performSemanticsAction(SemanticsActions.OnClick) { click ->
                assertTrue(click())
            }
        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateAdaptiveStageAppearance
            assertEquals(AdaptiveStageEasing.STANDARD, action.appearance.motion.easing)
        }
        composeRule
            .onNodeWithContentDescription("Haptic strength: Strong")
            .performSemanticsAction(SemanticsActions.OnClick) { click ->
                assertTrue(click())
            }

        composeRule.runOnIdle {
            val action = actions.last() as LauncherShellAction.UpdateAdaptiveStageAppearance
            assertEquals(AdaptiveStageHapticStrength.STRONG, action.appearance.motion.hapticStrength)
        }
    }

    @Test
    fun displaysTheActualUnavailableBlurFallbackInThePreview() {
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearancePageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = {},
                    rendererCapabilities = AdaptiveStageRendererCapabilities(supportsBlur = false),
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
                AdaptiveStageAppearancePreview(
                    appearance = AdaptiveStageAppearanceSettings(surface = AdaptiveStageSurface(blurStrengthPercent = 72)),
                    globalReducedMotion = false,
                    rendererCapabilities = AdaptiveStageRendererCapabilities(supportsBlur = false),
                    modifier = Modifier.requiredSize(360.dp),
                )
            }
        }

        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(AdaptiveStageCardBlurStrengthKey, 0))
            .assertCountEquals(3)
    }
}
