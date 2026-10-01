package com.riffle.app.screenshots.expressions

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.app.launcher.expressions.IconGridExpression
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.LensResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The IconGrid expression: an adaptive grid of labelled icons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class IconGridExpressionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun iconGridCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun iconGridCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun iconGridCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun iconGridUnfolded() {
        render()
    }

    @Test
    fun iconGridEmpty() {
        render(result = LensResult.Flat(emptyList()))
    }

    @Test
    fun iconGridLoading() {
        render(state = ExpressionState.Loading)
    }

    @Test
    fun iconGridUnavailable() {
        render(state = ExpressionState.Unavailable("Notification access is off"))
    }

    private fun render(
        result: LensResult = ExpressionFixtures.flatApps(),
        state: ExpressionState = ExpressionState.Ready,
    ) {
        composeRule.renderExpression {
            IconGridExpression(
                result = result,
                onItemClick = {},
                state = state,
                environment = ExpressionFixtures.environment,
            )
        }
    }
}
