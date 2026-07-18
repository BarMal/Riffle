package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DockSettingLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun effectChoicesStayVisibleAndSelectableAtCompactWidthWithLargeFont() {
        var selectedEffect: DockVisualEffect? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(240.dp)) {
                        DockSetting(
                            dock = DockModel(capacity = 4),
                            notificationAccessStatus = NotificationAccessStatus.GRANTED,
                            onAction = { action ->
                                selectedEffect = (action as? LauncherShellAction.SelectDockVisualEffect)?.effect
                            },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Dock effect").assertIsDisplayed()
        composeRule.onNodeWithText("Flat").assertIsDisplayed()
        composeRule.onNodeWithText("Elevated").assertIsDisplayed()
        composeRule.onNodeWithText("Outlined").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(DockVisualEffect.OUTLINED, selectedEffect)
        }
    }
}
