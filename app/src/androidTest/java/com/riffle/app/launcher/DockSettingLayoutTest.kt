package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
    fun effectChoicesStayVisibleAndSelectableAtCompactWidthWithLargeFont() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(240.dp)) {
                        DockSetting(
                            dock = DockModel(capacity = 4),
                            notificationAccessStatus = NotificationAccessStatus.GRANTED,
                            onAction = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Dock effect").assertIsDisplayed()
        composeRule.onNodeWithText("Flat").assertIsDisplayed()
        composeRule.onNodeWithText("Elevated").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("Outlined").assertIsDisplayed().assertHasClickAction()
    }
}
