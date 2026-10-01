package com.riffle.app.screenshots.expressions

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.app.launcher.expressions.IconRowExpression
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.LensResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The IconRow expression: a horizontal row of labelled icons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class IconRowExpressionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun iconRowCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun iconRowCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun iconRowCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun iconRowUnfolded() {
        render()
    }

    @Test
    fun iconRowEmpty() {
        render(result = LensResult.Flat(emptyList()))
    }

    @Test
    fun iconRowLoading() {
        render(state = ExpressionState.Loading)
    }

    @Test
    fun iconRowUnavailable() {
        render(state = ExpressionState.Unavailable("Notification access is off"))
    }

    private fun render(
        result: LensResult = ExpressionFixtures.flatApps(count = 12),
        state: ExpressionState = ExpressionState.Ready,
    ) {
        composeRule.renderExpression {
            IconRowExpression(
                result = result,
                onItemClick = {},
                state = state,
                environment = ExpressionFixtures.environment,
            )
        }
    }
}
