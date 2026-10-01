package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.AddTextRuleDialog
import com.riffle.app.launcher.EXCLUSIONS_ADD_TEST_TAG
import com.riffle.app.launcher.EXCLUSIONS_EMPTY_TEST_TAG
import com.riffle.app.launcher.EXCLUSIONS_SUMMARY_TEST_TAG
import com.riffle.app.launcher.EXCLUSION_PROBLEM_TEST_TAG
import com.riffle.app.launcher.ExclusionsPageCallbacks
import com.riffle.app.launcher.ExclusionsSettingsContent
import com.riffle.app.launcher.exclusionRowTestTag
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatchMode
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionTextField
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsModel
import com.riffle.core.domain.launcher.workspace.settings.TextRuleDraft
import com.riffle.core.domain.launcher.workspace.settings.TextRuleValidator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Hidden items and rules over fixed fake rules (no repository): the list at compact (dark, large font)
 * and unfolded (two columns) widths, the empty state, another layout, a source that is off, and the delete and add
 * dialogs. The page is drawn directly (it is only reachable with the preview on).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class ExclusionsSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<ExclusionsSettingsAction>()
    private val callbacks =
        ExclusionsPageCallbacks(
            onAction = { actions += it },
            problemWith = { draft -> TextRuleValidator.validate(draft, ExclusionRuleSet.EMPTY) },
        )

    @Test
    fun exclusionsCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun exclusionsCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun exclusionsCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun exclusionsUnfolded() {
        render()
    }

    @Test
    fun exclusionsEmpty() {
        render(ExclusionsSettingsFixtures.empty)

        composeRule.onNodeWithTag(EXCLUSIONS_EMPTY_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(EXCLUSIONS_SUMMARY_TEST_TAG).assertExists()
    }

    @Test
    fun exclusionsOtherLayoutShowsItsOwnRules() {
        render(ExclusionsSettingsFixtures.otherLayout, viewed = ExclusionsSettingsFixtures.foldable)

        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.appRule)).assertExists()
        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.textRule)).assertDoesNotExist()
    }

    @Test
    fun exclusionsSourceOffWithholdsTheCounts() {
        render(ExclusionsSettingsFixtures.sourceOff)

        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.textRule)).assertExists()
        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.textRule))
            .assert(hasContentDescription("Source is off", substring = true))
    }

    @Test
    fun exclusionsNotLoadedYet() {
        render(null)

        composeRule.onNodeWithText("Rules are still loading", substring = true).assertExists()
    }

    @Test
    fun tappingARowTurnsItOffOrOn() {
        render(capture = false)

        composeRule.onNodeWithTag(
            exclusionRowTestTag(ExclusionsSettingsFixtures.feedRule),
        ).performScrollTo().assertIsOn()
        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.feedRule)).performClick()
        composeRule.onNodeWithTag(
            exclusionRowTestTag(ExclusionsSettingsFixtures.patternRule),
        ).performScrollTo().assertIsOff()
        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.patternRule)).performClick()

        assertEquals(
            listOf<ExclusionsSettingsAction>(
                ExclusionsSettingsAction.SetEnabled(ExclusionsSettingsFixtures.feedRule, false),
                ExclusionsSettingsAction.SetEnabled(ExclusionsSettingsFixtures.patternRule, true),
            ),
            actions,
        )
    }

    @Test
    fun aRowReadsAsOneElementWithItsSummaryAndTheRowActionsAreCustomActions() {
        render(capture = false)

        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.textRule)).performScrollTo()
            .assert(hasContentDescription("Title contains \"flash sale\"", substring = true))
        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.textRule))
            .assert(hasContentDescription("Hides 2 items right now", substring = true))
    }

    @Test
    fun deleteAsksForConfirmationBeforeDoingAnything() {
        render(capture = false)

        composeRule.onNodeWithContentDescription(
            "More actions for Title contains \"flash sale\"",
            useUnmergedTree = true,
        ).performScrollTo().performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNode(hasText("Delete rule?") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        assertEquals(emptyList<ExclusionsSettingsAction>(), actions)
        composeRule.captureScreen()

        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog()) and hasClickAction()).performClick()

        assertEquals(
            listOf<ExclusionsSettingsAction>(ExclusionsSettingsAction.Delete(ExclusionsSettingsFixtures.textRule)),
            actions,
        )
    }

    @Test
    fun applyToAllLayoutsActsAtOnce() {
        render(capture = false)

        composeRule.onNodeWithContentDescription("More actions for Feed example-blog", useUnmergedTree = true)
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Apply to all layouts").performClick()

        assertEquals(
            listOf<ExclusionsSettingsAction>(
                ExclusionsSettingsAction.ApplyToAllLayouts(ExclusionsSettingsFixtures.feedRule),
            ),
            actions,
        )
    }

    @Test
    fun theAddDialogOpensDisabledUntilThereIsValidText() {
        pauseClock()
        render(capture = false)

        composeRule.onNodeWithTag(EXCLUSIONS_ADD_TEST_TAG).performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(CLOCK_STEP_MILLIS)

        composeRule.onNode(hasText("Add text rule") and hasAnyAncestor(isDialog())).assertExists()
        composeRule.onNode(hasText("Add rule") and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        assertEquals(emptyList<ExclusionsSettingsAction>(), actions)
    }

    // The dialog is drawn with its text pre-filled: typing into a focused field keeps the test clock from idling.
    @Test
    fun aTooShortTextShowsItsReasonAndCannotBeAdded() {
        pauseClock()
        renderAddDialog("ab")

        composeRule.onNodeWithTag(EXCLUSION_PROBLEM_TEST_TAG)
            .assert(hasText("Use at least 3 characters", substring = true))
        composeRule.onNode(hasText("Add rule") and hasAnyAncestor(isDialog())).assertIsNotEnabled()
        assertEquals(emptyList<ExclusionsSettingsAction>(), actions)
    }

    @Test
    fun aValidTextAddsExactlyTheTypedRule() {
        pauseClock()
        renderAddDialog("abc sale")

        composeRule.onNode(hasText("Add rule") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(
            listOf<ExclusionsSettingsAction>(
                ExclusionsSettingsAction.AddText(
                    TextRuleDraft(
                        SourceIds.NOTIFICATIONS,
                        ExclusionTextField.TITLE,
                        ExclusionMatchMode.CONTAINS,
                        "abc sale",
                    ),
                ),
            ),
            actions,
        )
    }

    // A text field in a dialog can take focus, and its blinking cursor then never lets the test clock idle.
    private fun pauseClock() {
        composeRule.mainClock.autoAdvance = false
    }

    private fun renderAddDialog(initialValue: String) {
        composeRule.setContent {
            ScreenshotBackdrop {
                AddTextRuleDialog(
                    problemWith = callbacks.problemWith,
                    onAdd = { draft -> actions += ExclusionsSettingsAction.AddText(draft) },
                    onDismiss = {},
                    initialValue = initialValue,
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(CLOCK_STEP_MILLIS)
    }

    private fun render(
        model: ExclusionsSettingsModel? = ExclusionsSettingsFixtures.populated,
        viewed: HomeLayoutDeviceClass = ExclusionsSettingsFixtures.phone,
        capture: Boolean = true,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    ExclusionsSettingsContent(
                        model = model,
                        viewed = viewed,
                        tabs = ExclusionsSettingsFixtures.tabs,
                        callbacks = callbacks,
                    )
                }
            }
        }
        if (capture) composeRule.captureScreen()
    }
}

private const val CLOCK_STEP_MILLIS = 500L
