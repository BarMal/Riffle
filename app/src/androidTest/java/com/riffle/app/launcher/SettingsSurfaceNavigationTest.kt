package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSurfaceNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun returningToMainKeepsItsScrollPositionAfterScrollingAnotherPage() {
        composeRule.setContent {
            MaterialTheme {
                SettingsSurface(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Appearance").performScrollTo().performClick()
        composeRule.onNodeWithText("Hide navigation bar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    }
}
