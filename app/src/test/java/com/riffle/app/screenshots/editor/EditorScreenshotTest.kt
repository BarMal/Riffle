package com.riffle.app.screenshots.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.riffle.app.launcher.editor.EDITOR_OFFER_TEST_TAG
import com.riffle.app.launcher.editor.EDITOR_SAVED_LENS_DETACH_TEST_TAG
import com.riffle.app.launcher.editor.EDITOR_SAVED_LENS_IN_USE_TEST_TAG
import com.riffle.app.launcher.editor.EDITOR_SAVE_AS_NAME_TEST_TAG
import com.riffle.app.launcher.editor.EDITOR_SAVE_AS_TOGGLE_TEST_TAG
import com.riffle.app.launcher.editor.EditorEnvironment
import com.riffle.app.launcher.editor.EditorScreen
import com.riffle.app.launcher.editor.WorkspaceEditorUiState
import com.riffle.app.launcher.editor.detachTestTag
import com.riffle.app.launcher.editor.savedLensRowTestTag
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.expressions.renderExpression
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.editor.EditorStep
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.SearchQueryInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The workspace editor: the overview and each flow step (Source, Expression, Container, Confirm) at compact and
 * unfolded widths, plus the needs-access state. The editor is not reachable from the home surface yet (the
 * workspace menu's Edit effect hands off to it), so this is its only visual coverage.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class EditorScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(
        state: WorkspaceEditorUiState,
        environment: EditorEnvironment = EditorScreenshotFixtures.environment,
    ) {
        composeRule.renderExpression {
            EditorScreen(
                state = state,
                environment = environment,
                services = EditorScreenshotFixtures.services,
                dispatch = {},
                onRequestSourceAccess = {},
            )
        }
    }

    @Test
    fun overviewCompact() = render(EditorScreenshotFixtures.overview())

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun overviewUnfolded() = render(EditorScreenshotFixtures.overview())

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun overviewCompactDark() = render(EditorScreenshotFixtures.overview())

    @Test
    fun sourceStepCompact() = render(EditorScreenshotFixtures.flowAt(EditorStep.SOURCE))

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun sourceStepUnfolded() = render(EditorScreenshotFixtures.flowAt(EditorStep.SOURCE))

    /** The per-lens search text field, pre-filled and unfocused (see the fixture): no typing, no cursor blink. */
    @Test
    fun sourceStepSearchQueryCompact() =
        render(
            EditorScreenshotFixtures.searchQuery("weekend plans"),
            EditorScreenshotFixtures.searchEnvironment,
        )

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun sourceStepSearchQueryAtLimitUnfolded() =
        render(
            EditorScreenshotFixtures.searchQuery("q".repeat(SearchQueryInput.MAX_LENGTH)),
            EditorScreenshotFixtures.searchEnvironment,
        )

    @Test
    fun expressionStepCompact() = render(EditorScreenshotFixtures.flowAt(EditorStep.EXPRESSION))

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun expressionStepUnfolded() = render(EditorScreenshotFixtures.flowAt(EditorStep.EXPRESSION))

    @Test
    fun containerStepCompact() = render(EditorScreenshotFixtures.flowAt(EditorStep.CONTAINER))

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun containerStepUnfolded() = render(EditorScreenshotFixtures.flowAt(EditorStep.CONTAINER))

    @Test
    fun confirmStepCompact() = render(EditorScreenshotFixtures.flowAt(EditorStep.CONFIRM))

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun confirmStepUnfolded() = render(EditorScreenshotFixtures.flowAt(EditorStep.CONFIRM))

    @Test
    fun sourceNeedsAccessCompact() = render(EditorScreenshotFixtures.needsAccess())

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun sourceStepCompactLargeFont() = render(EditorScreenshotFixtures.flowAt(EditorStep.SOURCE))

    // Saved lenses. No test types into the name field or opens a dialog: the field is drawn pre-filled and unfocused.

    private fun renderSavedList() {
        render(EditorScreenshotFixtures.savedLensList())
        composeRule.onNodeWithTag(savedLensRowTestTag(EditorScreenshotFixtures.notesByAppId)).assertExists()
        // A lens the container cannot use stays in the list, disabled, with its reason in the spoken summary.
        composeRule.onNodeWithTag(savedLensRowTestTag(EditorScreenshotFixtures.latestAppsId)).assertIsNotEnabled()
        composeRule.onNodeWithTag(savedLensRowTestTag(EditorScreenshotFixtures.oldFeedId)).assertIsNotEnabled()
        composeRule.onAllNodesWithContentDescription("Not available here", substring = true).assertCountEquals(2)
    }

    @Test
    fun savedLensListCompact() = renderSavedList()

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun savedLensListUnfolded() = renderSavedList()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun savedLensListCompactDark() = renderSavedList()

    @Test
    fun usingASavedLensCompact() {
        render(EditorScreenshotFixtures.usingSavedLens())
        composeRule.onNodeWithTag(EDITOR_SAVED_LENS_IN_USE_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(EDITOR_SAVED_LENS_DETACH_TEST_TAG).assertExists()
    }

    private fun renderConfirmSaveAs() {
        render(EditorScreenshotFixtures.confirmSaveAs())
        composeRule.onNodeWithTag(EDITOR_SAVE_AS_TOGGLE_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(EDITOR_SAVE_AS_NAME_TEST_TAG).assertExists()
    }

    @Test
    fun confirmStepSaveAsCompact() = renderConfirmSaveAs()

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun confirmStepSaveAsUnfolded() = renderConfirmSaveAs()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun confirmStepSaveAsCompactDark() = renderConfirmSaveAs()

    @Test
    fun confirmStepSaveAsNameProblemCompact() {
        render(EditorScreenshotFixtures.confirmSaveAs(name = "notes by APP"))
        composeRule.onNodeWithText("Another saved lens already has that name.").assertExists()
    }

    @Test
    fun adoptOfferCompact() {
        render(EditorScreenshotFixtures.adoptOffer())
        composeRule.onNodeWithTag(EDITOR_OFFER_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Use it there too").assertExists()
    }

    @Test
    fun overviewWithSavedLensesCompact() {
        render(EditorScreenshotFixtures.overviewWithSavedLenses())
        composeRule.onNodeWithTag(detachTestTag(FlowMode.EditPage(ContainerId("per-app")))).assertExists()
        composeRule.onNodeWithTag(
            detachTestTag(FlowMode.EditWidget(ContainerId("now"), ContainerId("inbox"))),
        ).assertExists()
    }
}
