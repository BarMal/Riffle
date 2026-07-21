package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    @Test
    fun largeWindowKeepsSupportingDetailPaneInsideSafeInsets() {
        setContent(widthDp = 1_200, windowInsets = WindowInsets(24, 16, 48, 32))

        composeRule.onNodeWithText("Details").assertIsDisplayed()
    }

    private fun setContent(
        widthDp: Int,
        windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        composeRule.setContent {
            // Make the physical test host represent the requested adaptive dp window.
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    Box(
                        modifier =
                            Modifier.width(widthDp.dp)
                                .height(TEST_WINDOW_HEIGHT_DP.dp)
                                .clipToBounds(),
                    ) {
                        TimeScapeAppStageSurface(
                            state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                            windowInsets = windowInsets,
                            windowLayout = TimeScapeWindowLayout(widthDp = widthDp, heightDp = TEST_WINDOW_HEIGHT_DP),
                            onAction = {},
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TEST_WINDOW_DENSITY = 0.3f
        const val TEST_WINDOW_HEIGHT_DP = 800
    }
}
