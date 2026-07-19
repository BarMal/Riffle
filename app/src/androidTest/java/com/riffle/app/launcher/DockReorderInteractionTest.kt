package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DockReorderInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun crossingASlotBoundaryDuringPreviewStillCommitsTheDrag() {
        val camera = shortcut("camera")
        val mail = shortcut("mail")
        val maps = shortcut("maps")
        val actions = mutableListOf<LauncherShellAction>()
        setContent(camera, mail, maps, actions = actions)

        composeRule.onNodeWithTag(dockItemTestTag(camera.id)).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(width.toFloat() * 1.2f, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(listOf(LauncherShellAction.MoveDockShortcutToIndex(camera.id, 1)), actions)
        }
    }

    @Test
    fun cancelledLongPressDragDoesNotCommit() {
        val camera = shortcut("camera")
        val mail = shortcut("mail")
        val actions = mutableListOf<LauncherShellAction>()
        setContent(camera, mail, actions = actions)

        composeRule.onNodeWithTag(dockItemTestTag(camera.id)).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(width.toFloat() * 1.2f, 0f))
            cancel()
        }

        composeRule.runOnIdle { assertEquals(emptyList<LauncherShellAction>(), actions) }
    }

    @Test
    fun holdingAtOverflowEdgeMovesToAnOffScreenDockTarget() {
        val shortcuts = (0 until 10).map { shortcut("app$it") }.toTypedArray()
        val actions = mutableListOf<LauncherShellAction>()
        setContent(*shortcuts, actions = actions)

        composeRule.onNodeWithTag(dockItemTestTag(shortcuts.first().id)).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(200f, 0f))
            advanceEventTime(600L)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf(LauncherShellAction.MoveDockShortcutToIndex(shortcuts.first().id, 9)),
                actions,
            )
        }
    }

    private fun setContent(
        vararg shortcuts: AppShortcutItem,
        actions: MutableList<LauncherShellAction>,
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(260.dp)) {
                    Dock(
                        dock = DockModel(capacity = shortcuts.size, items = shortcuts.toList()),
                        isEditing = true,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = EmptyAppIconLoader,
                        widgetViewFactory = EmptyHomeWidgetViewFactory,
                        interactions = DockInteractions(onAction = actions::add),
                    )
                }
            }
        }
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
