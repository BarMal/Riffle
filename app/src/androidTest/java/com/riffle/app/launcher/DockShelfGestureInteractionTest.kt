package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
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

    @Test
    fun upwardSwipeOnCollapsedProductionDockExpandsTheOverflowShelf() {
        val primary = shortcut("primary")
        val overflow = shortcut("overflow")
        val layout =
            HomeLayoutDefaults.standard().let { standardLayout ->
                standardLayout.copy(
                    dock = standardLayout.dock.copy(capacity = 1, items = listOf(primary, overflow)),
                )
            }
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp)) {
                    StandardHome(
                        layout = layout,
                        installedApps = emptyList(),
                        interactions = StandardHomeInteractions(),
                        presentation = StandardHomePresentation(appShortcutsByApp = emptyMap()),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(dockItemTestTag(primary.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -24f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -64f))
            up()
        }

        composeRule.waitForIdle()
        val primaryBounds = composeRule.onNodeWithTag(dockItemTestTag(primary.id)).fetchSemanticsNode().boundsInRoot
        val overflowBounds = composeRule.onNodeWithTag(dockItemTestTag(overflow.id)).fetchSemanticsNode().boundsInRoot

        assertTrue(overflowBounds.center.y < primaryBounds.center.y)
    }

    @Test
    fun productionDockRetainsAnOpeningSwipeAcrossCallbackRecomposition() {
        val primary = shortcut("primary")
        val overflow = shortcut("overflow")
        var isShelfExpanded by mutableStateOf(false)
        var callbackVersion by mutableStateOf(0)
        composeRule.setContent {
            val callbackGeneration = callbackVersion
            val onShelfExpandedChange =
                remember(callbackGeneration) {
                    { expanded: Boolean -> isShelfExpanded = expanded }
                }
            MaterialTheme {
                Box(
                    modifier =
                        Modifier
                            .size(200.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                                    while (true) {
                                        val change =
                                            awaitPointerEvent(PointerEventPass.Final)
                                                .changes
                                                .firstOrNull { it.id == down.id }
                                                ?: break
                                        if (change.isConsumed) {
                                            callbackVersion += 1
                                            break
                                        }
                                        if (!change.pressed) break
                                    }
                                }
                            },
                ) {
                    Dock(
                        dock = DockModel(capacity = 1, items = listOf(primary, overflow)),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = EmptyAppIconLoader,
                        interactions =
                            DockInteractions(
                                onShelfExpandedChange = onShelfExpandedChange,
                                onAction = {},
                            ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(dockItemTestTag(primary.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -24f))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -64f))
            up()
        }

        composeRule.runOnIdle { assertTrue(isShelfExpanded) }
    }

    private fun shortcut(name: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(name),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
        )
}
