package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

        // The test host is phone-sized; this verifies the modeled supporting pane is composed.
        // Physical bounds are covered by the policy regression using the 1,200dp window input.
        composeRule.onNodeWithText("Details").assertExists()
    }

    private fun setContent(widthDp: Int) {
        composeRule.setContent {
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
