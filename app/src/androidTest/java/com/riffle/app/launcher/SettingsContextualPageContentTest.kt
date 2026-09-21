package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.LauncherShellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The contextual page's toggle is fully automatic once enabled -- there is nothing else to
 * configure -- so its subtitle must not promise a "Work, Personal, or Cards" picker that doesn't
 * exist anywhere in Settings (#1177-adjacent misdirection finding).
 */
@RunWith(AndroidJUnit4::class)
class SettingsContextualPageContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun toggleDescribesAutomaticBehaviourRatherThanPromisingConfiguration() {
        val actions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                SettingsContextualPageContent(
                    state = LauncherShellState().settingsSurfaceState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Contextual behaviour").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Automatically bring forward a Today, Work, Personal, frequently-used, or notification " +
                    "page or card when it matches what's happening right now. This is automatic -- there's " +
                    "nothing else to configure.",
            ).assertIsDisplayed()

        composeRule.onNodeWithText("Contextual behaviour").performClick()

        assertEquals(listOf(LauncherShellAction.SelectContextualEnabled(true)), actions)
    }
}
