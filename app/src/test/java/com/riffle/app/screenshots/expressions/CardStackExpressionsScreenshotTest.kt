package com.riffle.app.screenshots.expressions

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.expressions.CardStackExpression
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.LensResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The CardStack expression: the shared card stack fed by a lens result. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class CardStackExpressionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardStackCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun cardStackCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun cardStackCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun cardStackUnfolded() {
        render()
    }

    @Test
    fun cardStackEmpty() {
        render(result = LensResult.Flat(emptyList()))
    }

    @Test
    fun cardStackLoading() {
        render(state = ExpressionState.Loading)
    }

    @Test
    fun cardStackUnavailable() {
        render(state = ExpressionState.Unavailable("Notification access is off"))
    }

    @Test
    fun cardStackOff() {
        render(state = ExpressionState.Off("This source is turned off", "Turn on") {})
    }

    private fun render(
        result: LensResult = ExpressionFixtures.flatMessages(),
        state: ExpressionState = ExpressionState.Ready,
    ) {
        composeRule.renderExpression {
            CardStackExpression(
                result = result,
                onItemClick = {},
                state = state,
                environment = ExpressionFixtures.environment,
            )
        }
    }
}
