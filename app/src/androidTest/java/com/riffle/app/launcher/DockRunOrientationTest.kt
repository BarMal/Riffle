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
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dock is the same strip on any edge: one icon deep, scrolling along its run. These check that
 * the run actually turns with the edge, and that a drag turns with it too.
 */
@RunWith(AndroidJUnit4::class)
class DockRunOrientationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val camera = shortcut("camera")
    private val mail = shortcut("mail")

    @Test
    fun aSideDockStacksItsSlotsDownTheEdge() {
        setContent(DockPosition.LEADING)

        val first = composeRule.onNodeWithTag(dockItemTestTag(camera.id)).fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag(dockItemTestTag(mail.id)).fetchSemanticsNode().boundsInRoot

        assertTrue("expected $second below $first", second.top > first.top)
        assertEquals(first.left, second.left, TOLERANCE_PX)
    }

    @Test
    fun aBottomDockStillRunsAcross() {
        setContent(DockPosition.BOTTOM)

        val first = composeRule.onNodeWithTag(dockItemTestTag(camera.id)).fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag(dockItemTestTag(mail.id)).fetchSemanticsNode().boundsInRoot

        assertTrue("expected $second right of $first", second.left > first.left)
        assertEquals(first.top, second.top, TOLERANCE_PX)
    }

    @Test
    fun aSideDocksSurfaceIsTallerThanItIsWide() {
        setContent(DockPosition.LEADING)

        val surface = composeRule.onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue("expected a tall surface, was $surface", surface.height > surface.width)
    }

    @Test
    fun draggingAnItemOffASideDockMovesItHome() {
        // Sideways, not upward: off a leading dock is toward the workspace.
        val actions = mutableListOf<LauncherShellAction>()
        setContent(DockPosition.LEADING, actions = actions)

        composeRule.onNodeWithTag(dockItemTestTag(camera.id)).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(width.toFloat() * 1.2f, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(listOf(LauncherShellAction.MoveDockItemToHome(camera.id)), actions)
        }
    }

    @Test
    fun draggingAlongASideDocksRunReordersInsteadOfLeaving() {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(DockPosition.LEADING, actions = actions)

        composeRule.onNodeWithTag(dockItemTestTag(camera.id)).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(0f, height.toFloat() * 1.2f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(listOf(LauncherShellAction.MoveDockShortcutToIndex(camera.id, 1)), actions)
        }
    }

    private fun setContent(
        position: DockPosition,
        actions: MutableList<LauncherShellAction> = mutableListOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(260.dp)) {
                    Dock(
                        dock = DockModel(capacity = 2, items = listOf(camera, mail)),
                        isEditing = true,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = EmptyAppIconLoader,
                        widgetViewFactory = EmptyHomeWidgetViewFactory,
                        position = position,
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

    private companion object {
        private const val TOLERANCE_PX = 0.5f
    }
}
