package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Rule
import org.junit.Test

class TimeScapeAdaptiveLayoutInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mediumWindowUsesNamedStageRailControls() {
        setContent(widthDp = 800)

        composeRule.onNodeWithText("Stages").assertIsDisplayed()
        composeRule.onNodeWithText("Previous").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test
    fun largeWindowAddsSupportingDetailPane() {
        setContent(widthDp = 1_200)

        composeRule.onNodeWithText("Details").assertIsDisplayed()
    }

    private fun setContent(widthDp: Int) {
        composeRule.setContent {
            // Make the physical test host represent the requested adaptive dp window.
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    TimeScapeAppStageSurface(
                        state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                        windowLayout = TimeScapeWindowLayout(widthDp = widthDp, heightDp = 800),
                        onAction = {},
                    )
                }
            }
        }
    }

    private companion object {
        const val TEST_WINDOW_DENSITY = 0.3f
    }
}
