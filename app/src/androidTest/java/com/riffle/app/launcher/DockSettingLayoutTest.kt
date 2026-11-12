package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DockSettingLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun simplifiedDockSettingsKeepHeightAndWidthControlsWithoutSlotPlaceholders() {
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    DockSetting(
                        dock = DockModel(capacity = 4),
                        notificationAccessStatus = NotificationAccessStatus.GRANTED,
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Dock height").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Dock width").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Dock corner radius").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Grid to dock controls spacing").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Fit content").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Full width").performScrollTo().assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Dock alignment").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText("Start")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsNotSelected()
        composeRule.onNodeWithText("Center").performScrollTo().assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("End").performScrollTo().assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Dock slots").assertDoesNotExist()
        composeRule.onNodeWithText("Dock item spacing").assertDoesNotExist()
    }
}
