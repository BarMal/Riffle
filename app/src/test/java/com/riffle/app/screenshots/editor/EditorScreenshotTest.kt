package com.riffle.app.screenshots.editor

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.editor.EditorScreen
import com.riffle.app.launcher.editor.WorkspaceEditorUiState
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.expressions.renderExpression
import com.riffle.core.domain.launcher.workspace.editor.EditorStep
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

    private fun render(state: WorkspaceEditorUiState) {
        composeRule.renderExpression {
            EditorScreen(
                state = state,
                environment = EditorScreenshotFixtures.environment,
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
}
