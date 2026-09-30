package com.riffle.app.screenshots.expressions

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.expressions.CategoriesExpression
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.LensResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Categories expression: one card per group previewing up to four icons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class CategoriesExpressionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun categoriesCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun categoriesCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun categoriesCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun categoriesUnfolded() {
        render()
    }

    @Test
    fun categoriesEmpty() {
        render(result = LensResult.Grouped(emptyList()))
    }

    private fun render(result: LensResult = ExpressionFixtures.groupedApps()) {
        composeRule.renderExpression {
            CategoriesExpression(
                result = result,
                onItemClick = {},
                state = ExpressionState.Ready,
                environment = ExpressionFixtures.environment,
            )
        }
    }
}
