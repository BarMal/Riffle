package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import org.junit.Assert.assertEquals
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
                Box(modifier = Modifier.height(480.dp)) {
                    SettingsSurface(
                        state = LauncherShellState().settingsSurfaceState(),
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Appearance").performScrollTo()
        val mainScrollPosition = settingsScrollPosition()
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.onNodeWithText("Hide navigation bar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        assertEquals(mainScrollPosition, settingsScrollPosition(), 0f)
    }

    private fun settingsScrollPosition(): Float =
        composeRule
            .onNodeWithTag(SETTINGS_PAGE_CONTENT_TEST_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()
}
