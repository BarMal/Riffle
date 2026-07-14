package com.riffle.app.launcher

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

        composeRule.onNodeWithText("Appearance").performScrollTo().performClick()
        composeRule.onNodeWithText("Hide navigation bar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun pageScrollStatesKeepTheMainOffsetSeparateFromAppearance() {
        val mainScrollState = ScrollState(initial = 72)
        val appearanceScrollState = ScrollState(initial = 184)
        val pageScrollStates =
            mapOf(
                SettingsPage.MAIN to mainScrollState,
                SettingsPage.APPEARANCE to appearanceScrollState,
            )

        val restoredMainScrollState = settingsPageScrollStateFor(pageScrollStates, SettingsPage.MAIN)
        val currentAppearanceScrollState = settingsPageScrollStateFor(pageScrollStates, SettingsPage.APPEARANCE)

        assertSame(mainScrollState, restoredMainScrollState)
        assertEquals(72, restoredMainScrollState.value)
        assertEquals(184, currentAppearanceScrollState.value)
    }
}
