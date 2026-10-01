package com.riffle.app.screenshots.expressions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.app.launcher.expressions.ListExpression
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.core.domain.launcher.workspace.LensResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The List expression: rows with icon, subtitle and time, plus its empty and loading states. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class ListExpressionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun listCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun listCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun listUnfolded() {
        render()
    }

    @Test
    fun listEmpty() {
        render(result = ExpressionFixtures.flatApps(count = 0))
    }

    @Test
    fun listLoading() {
        render(state = ExpressionState.Loading)
    }

    @Test
    fun listUnavailable() {
        render(state = ExpressionState.Unavailable("Notification access is off"))
    }

    @Test
    fun listOff() {
        render(state = ExpressionState.Off("This source is turned off", "Turn on") {})
    }

    @Test
    fun listOffTurnOnButtonRunsTheEnableAction() {
        var enabled = 0
        render(state = ExpressionState.Off("This source is turned off", "Turn on") { enabled++ })

        composeRule.onNodeWithText("This source is turned off").assertIsDisplayed()
        composeRule.onNodeWithText("Turn on").performClick()

        assertEquals(1, enabled)
    }

    private fun render(
        result: LensResult = ExpressionFixtures.flatMessages(),
        state: ExpressionState = ExpressionState.Ready,
    ) {
        composeRule.renderExpression {
            ListExpression(
                result = result,
                onItemClick = {},
                state = state,
                environment = ExpressionFixtures.environment,
            )
        }
    }
}
