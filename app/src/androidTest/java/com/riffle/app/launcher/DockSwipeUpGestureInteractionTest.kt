package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DockSwipeUpGestureInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dominantUpwardDragPastThresholdTriggersTheAction() {
        val actions = setContentWithSwipeUpGesture()

        composeRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(width / 2f, height - 1f))
            moveBy(Offset(0f, -150f))
            up()
        }

        composeRule.runOnIdle { assertEquals(listOf(LauncherShellAction.OpenAppDrawer), actions) }
    }

    @Test
    fun upwardDragBelowThresholdDoesNotTrigger() {
        val actions = setContentWithSwipeUpGesture()

        composeRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(width / 2f, height - 1f))
            moveBy(Offset(0f, -30f))
            up()
        }

        composeRule.runOnIdle { assertEquals(emptyList<LauncherShellAction>(), actions) }
    }

    @Test
    fun downwardDragDoesNotTrigger() {
        val actions = setContentWithSwipeUpGesture()

        composeRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(width / 2f, 1f))
            moveBy(Offset(0f, 150f))
            up()
        }

        composeRule.runOnIdle { assertEquals(emptyList<LauncherShellAction>(), actions) }
    }

    @Test
    fun dominantlyHorizontalDragDoesNotTrigger() {
        val actions = setContentWithSwipeUpGesture()

        composeRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(1f, height / 2f))
            moveBy(Offset(180f, -10f))
            up()
        }

        composeRule.runOnIdle { assertEquals(emptyList<LauncherShellAction>(), actions) }
    }

    @Test
    fun disabledGestureNeverTriggersRegardlessOfDrag() {
        val actions = setContentWithSwipeUpGesture(enabled = false)

        composeRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(Offset(width / 2f, height - 1f))
            moveBy(Offset(0f, -150f))
            up()
        }

        composeRule.runOnIdle { assertEquals(emptyList<LauncherShellAction>(), actions) }
    }

    private fun setContentWithSwipeUpGesture(enabled: Boolean = true): List<LauncherShellAction> {
        val actions = mutableStateListOf<LauncherShellAction>()
        composeRule.setContent {
            Box(
                modifier =
                    Modifier
                        .size(200.dp)
                        .testTag(TEST_TAG)
                        .dockSwipeUpGestureInput(
                            enabled = enabled,
                            action = LauncherGestureAction.OPEN_APP_DRAWER,
                            onAction = actions::add,
                        ),
            )
        }
        return actions
    }

    private companion object {
        const val TEST_TAG = "dock-swipe-up"
    }
}
