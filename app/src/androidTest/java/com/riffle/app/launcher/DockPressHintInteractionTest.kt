package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The first-run hint on a device: it is there, its button is a click target, and pressing it removes it. */
@RunWith(AndroidJUnit4::class)
class DockPressHintInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var seen by mutableStateOf(false)

    @Test
    fun theHintShowsAndItsButtonDismissesIt() {
        composeRule.setContent {
            MaterialTheme {
                if (!seen) {
                    DockPressHint(
                        dockBounds = Rect(24f, 1500f, 1000f, 1700f),
                        pull = DockPullDirection.UP,
                        onDismiss = { seen = true },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(DOCK_PRESS_HINT_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DOCK_PRESS_HINT_DISMISS_TEST_TAG).assertHasClickAction().performClick()

        composeRule.onNodeWithTag(DOCK_PRESS_HINT_TEST_TAG).assertDoesNotExist()
    }
}
