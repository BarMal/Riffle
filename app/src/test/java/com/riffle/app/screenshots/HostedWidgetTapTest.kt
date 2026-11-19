package com.riffle.app.screenshots

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.HomeDragSession
import com.riffle.app.launcher.HomeItemDragState
import com.riffle.app.launcher.HomeWidgetPlaceholder
import com.riffle.app.launcher.HomeWorkspaceActions
import com.riffle.app.launcher.NoopLauncherHaptics
import com.riffle.app.launcher.ShortcutContextSurface
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A hosted platform widget on Home gets its own taps: Home's gesture layer must not cover it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HostedWidgetTapTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val touches = mutableListOf<Int>()
    private var clicks = 0

    private val factory =
        HomeWidgetViewFactory { context, _ -> ClickableHost(context) }

    private inner class ClickableHost(context: Context) : View(context) {
        init {
            isClickable = true
            setOnClickListener { clicks++ }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            touches += event.actionMasked
            return super.onTouchEvent(event)
        }
    }

    private val widget =
        WidgetItem(
            id = LauncherItemId("widget-1"),
            appWidgetId = HostedWidgetId(7),
            label = "Weather",
            placement = GridPlacement(GridCell(0, 0), GridSpan(2, 2)),
        )

    private val sessions = mutableListOf<HomeDragSession?>()
    private var current: HomeDragSession? = null

    private val workspaceActions =
        HomeWorkspaceActions(
            onFolderOpen = {},
            onDragSessionChanged = {
                sessions += it
                current = it
            },
            currentDragSession = { current },
            haptics = NoopLauncherHaptics,
            onAction = {},
        )

    private fun dragState(item: WidgetItem) =
        HomeItemDragState(
            pageId = LauncherPageId("home"),
            cell = GridCell(0, 0),
            cellSizePx = 100f,
            grid = GridDimensions(4, 5),
            pageItems = listOf(item),
        )

    private fun show(item: WidgetItem = widget) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(modifier = Modifier.padding(24.dp).size(200.dp).testTag("lone-widget")) {
                    HomeWidgetPlaceholder(
                        widget = item,
                        isEditing = false,
                        onAction = {},
                        contextSurface = ShortcutContextSurface.HOME,
                        widgetViewFactory = factory,
                        dragState = dragState(item),
                        workspaceActions = workspaceActions,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aTapOnALoneHostedWidgetReachesItsView() {
        show()
        composeRule.onNodeWithTag("lone-widget").performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals("touches=$touches", 1, clicks)
        assertTrue(sessions.toString(), sessions.isEmpty())
    }

    @Test
    fun aSwipeOnALoneHostedWidgetReachesItsViewAndNeverLiftsIt() {
        show()
        composeRule.onNodeWithTag("lone-widget").performTouchInput {
            down(center)
            moveBy(Offset(0f, -120f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("touches=$touches", touches.contains(MotionEvent.ACTION_MOVE))
        assertTrue(sessions.toString(), sessions.isEmpty())
    }

    @Test
    fun aLongPressOnALoneHostedWidgetLiftsItAndTheViewSeesACancel() {
        show()
        composeRule.onNodeWithTag("lone-widget").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(80f, 0f))
            moveBy(Offset(80f, 0f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue(sessions.toString(), sessions.any { it != null })
        assertTrue("touches=$touches", touches.contains(MotionEvent.ACTION_CANCEL) || touches.last() != MotionEvent.ACTION_UP)
        assertEquals(0, clicks)
    }

    @Test
    fun aTapOnAPlatformWidgetInAStackReachesItsView() {
        val stack = fixtureStack(shown = 1)
        show(stack)
        composeRule.onNodeWithTag("lone-widget").performTouchInput { click(Offset(width * 0.3f, height * 0.5f)) }
        composeRule.waitForIdle()
        assertEquals("touches=$touches", 1, clicks)
    }
}
