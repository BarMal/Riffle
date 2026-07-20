package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
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
    fun dockOpeningDragEndingOnReleaseExpandsBeforeTheHomeGestureThreshold() {
        val homeActions = mutableListOf<LauncherShellAction>()
        var isShelfExpanded by mutableStateOf(false)
        var openingDragWasConsumedBeforeHomeThreshold by mutableStateOf(false)
        composeRule.setContent {
            Box(
                modifier =
                    Modifier
                        .size(200.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                                val start = down.position
                                while (true) {
                                    val change =
                                        awaitPointerEvent(PointerEventPass.Final)
                                            .changes
                                            .firstOrNull { it.id == down.id }
                                            ?: break
                                    val drag = change.position - start
                                    if (
                                        drag.y <= -24f &&
                                        drag.y > -80f &&
                                        change.isConsumed
                                    ) {
                                        openingDragWasConsumedBeforeHomeThreshold = true
                                    }
                                    if (!change.pressed) break
                                }
                            }
                        }
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
            down(Offset(width / 2f, height - 1f))
            moveBy(Offset(0f, -24f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -64f))
            up()
        }

        composeRule.runOnIdle {
            assertTrue(isShelfExpanded)
            assertTrue(openingDragWasConsumedBeforeHomeThreshold)
            assertEquals(emptyList<LauncherShellAction>(), homeActions)
        }
    }
}
