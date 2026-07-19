package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DockShelfGestureInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun upwardDockDragExpandsShelfBeforeHomeGestureCanHandleIt() {
        val homeActions = mutableListOf<LauncherShellAction>()
        var isShelfExpanded by mutableStateOf(false)
        composeRule.setContent {
            Box(
                modifier =
                    Modifier
                        .size(200.dp)
                        .homeGestureInput(
                            enabled = true,
                            settings = HomeGestureSettings(),
                            onAction = homeActions::add,
                        ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag("dock-shelf")
                            .dockShelfGestureInput(
                                DockInteractions(
                                    isShelfExpanded = isShelfExpanded,
                                    onShelfExpandedChange = { isShelfExpanded = it },
                                    onAction = {},
                                ),
                            ),
                )
            }
        }

        composeRule.onNodeWithTag("dock-shelf").performTouchInput {
            swipe(
                start = Offset(width / 2f, height - 1f),
                end = Offset(width / 2f, 1f),
            )
        }

        composeRule.runOnIdle {
            assertTrue(isShelfExpanded)
            assertEquals(emptyList<LauncherShellAction>(), homeActions)
        }
    }
}
