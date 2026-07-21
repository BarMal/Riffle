package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeWorkspaceContextMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeLongPressShowsWorkspaceActions() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    HomeBackgroundContextMenu(
                        haptics = NoopLauncherHaptics,
                        onAction = {},
                        modifier = Modifier.fillMaxSize().testTag("home-background"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home-background").performTouchInput { longClick() }
        composeRule.onNodeWithText("Widgets").assertExists()
        composeRule.onNodeWithText("Manage pages").assertExists()
    }
}
