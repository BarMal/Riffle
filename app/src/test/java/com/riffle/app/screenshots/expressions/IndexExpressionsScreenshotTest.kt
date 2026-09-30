package com.riffle.app.screenshots.expressions

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.app.launcher.expressions.IndexExpression
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.LensResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Index expression: text-first entries with inline snippets, flat and grouped. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class IndexExpressionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun indexGroupedCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun indexGroupedCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun indexGroupedCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun indexGroupedUnfolded() {
        render()
    }

    @Test
    fun indexFlatCompact() {
        render(result = ExpressionFixtures.flatMessages())
    }

    @Test
    fun indexEmpty() {
        render(result = LensResult.Grouped(emptyList()))
    }

    @Test
    fun indexUnavailable() {
        render(state = ExpressionState.Unavailable("Notification access is off"))
    }

    private fun render(
        result: LensResult = ExpressionFixtures.groupedMessages(),
        state: ExpressionState = ExpressionState.Ready,
    ) {
        composeRule.renderExpression {
            IndexExpression(
                result = result,
                onItemClick = {},
                state = state,
                environment = ExpressionFixtures.environment,
            )
        }
    }
}
