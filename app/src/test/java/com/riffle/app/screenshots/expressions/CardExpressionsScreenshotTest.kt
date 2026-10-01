package com.riffle.app.screenshots.expressions

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.expressions.CardExpression
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.LensResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Card expression: one rich card with artwork, text and actions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class CardExpressionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun cardCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun cardCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun cardUnfolded() {
        render()
    }

    @Test
    fun cardEmpty() {
        render(result = LensResult.Flat(emptyList()))
    }

    @Test
    fun cardLoading() {
        render(state = ExpressionState.Loading)
    }

    @Test
    fun cardUnavailable() {
        render(state = ExpressionState.Unavailable("Notification access is off"))
    }

    private fun render(
        result: LensResult = ExpressionFixtures.singleCard(),
        state: ExpressionState = ExpressionState.Ready,
    ) {
        composeRule.renderExpression {
            CardExpression(
                result = result,
                onItemClick = {},
                state = state,
                environment = ExpressionFixtures.environment,
            )
        }
    }
}
