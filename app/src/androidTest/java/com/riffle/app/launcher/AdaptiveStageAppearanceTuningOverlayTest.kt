package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tuning the Cards appearance over the surface it shapes: the controls are there, they swipe out of
 * the way, and moving one dispatches the real setting rather than editing a copy.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveStageAppearanceTuningOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theControlsOpenOverTheSurfaceTheyShape() {
        setContent()

        composeRule.onNodeWithTag(APPEARANCE_TUNING_OVERLAY_TEST_TAG).assertIsDisplayed()
        composeRule
            .onNodeWithTag("adaptive-stage-appearance-tab-${AdaptiveStageAppearanceTab.GEOMETRY.name}")
            .assertIsDisplayed()
    }

    @Test
    fun swipingTheHandleDownGetsTheControlsOutOfTheWay() {
        setContent()

        val expanded = sheetTop()
        composeRule.onNodeWithTag(APPEARANCE_TUNING_SHEET_HANDLE_TEST_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 40f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, 120f))
            up()
        }
        composeRule.waitForIdle()

        assertTrue("expected the sheet to have shrunk, was $expanded now ${sheetTop()}", sheetTop() > expanded)
    }

    @Test
    fun tappingTheHandleWorksWithoutADrag() {
        setContent()

        val expanded = sheetTop()
        composeRule.onNodeWithTag(APPEARANCE_TUNING_SHEET_HANDLE_TEST_TAG).performClick()
        composeRule.waitForIdle()

        assertTrue("expected the sheet to have shrunk, was $expanded now ${sheetTop()}", sheetTop() > expanded)
    }

    @Test
    fun aControlInTheSheetDispatchesTheRealAppearanceSetting() {
        // Not an edit of a copy to be applied later: the surface behind is showing the setting, so
        // a control has to reach the same action the settings page would have sent.
        val actions = mutableListOf<LauncherShellAction>()
        setContent(actions::add)

        composeRule.onNodeWithText("Reset Folded").performClick()
        composeRule.onNodeWithContentDescription("Confirm Cards reset").performClick()

        composeRule.runOnIdle {
            assertTrue(
                "expected a real appearance update, got $actions",
                actions.any { action -> action is LauncherShellAction.UpdateAdaptiveStageAppearance },
            )
        }
    }

    @Test
    fun choosingWhichAppearanceToEditChangesNothingOnItsOwn() {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(actions::add)

        composeRule
            .onNodeWithTag("adaptive-stage-appearance-target-${AdaptiveStageAppearanceEditorTarget.UNFOLDED.name}")
            .performClick()

        composeRule.runOnIdle {
            assertTrue("expected no action from choosing a target, got $actions", actions.isEmpty())
        }
    }

    private fun sheetTop(): Float =
        composeRule
            .onNodeWithTag(APPEARANCE_TUNING_SHEET_HANDLE_TEST_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
            .top

    private fun setContent(onAction: (LauncherShellAction) -> Unit = {}) {
        composeRule.setContent {
            MaterialTheme {
                AdaptiveStageAppearanceTuningOverlay(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = onAction,
                    onDismiss = {},
                )
            }
        }
    }
}
