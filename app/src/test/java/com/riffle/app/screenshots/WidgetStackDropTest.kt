package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.riffle.app.launcher.HOME_DRAG_STACK_OUTLINE_TEST_TAG
import com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG
import com.riffle.app.launcher.HOME_WIDGET_STACK_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.applyShortcutOrFolderDropEdit
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dragging a widget over another on Home through the real [HomeDestination] (ADR 0003, Amendment 14): the dead zone and
 * the folder hold decide when a stack is offered, the target wears the stack outline, and letting go makes the stack.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WidgetStackDropTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(ScreenshotFixtures.homeState())

    private fun widget(
        id: String,
        label: String,
        host: Int,
        column: Int,
        row: Int,
        span: GridSpan,
    ) = WidgetItem(
        id = LauncherItemId(id),
        appWidgetId = HostedWidgetId(host),
        label = label,
        placement = GridPlacement(GridCell(column, row), span),
    )

    private val gauge = widget("w-gauge", "Gauge", 11, 0, 0, GridSpan(2, 2))
    private val dial = widget("w-dial", "Dial", 12, 2, 0, GridSpan(2, 2))
    private val strip = widget("w-strip", "Strip", 13, 0, 2, GridSpan(2, 1))

    private fun show() {
        val layout = shell.homeLayout
        val page = layout.selectedPage.copy(items = listOf(gauge, dial, strip))
        val updated = layout.copy(pages = listOf(page) + layout.pages.drop(1))
        shell = shell.copy(homeLayout = updated, homeLayoutSet = shell.homeLayoutSet.withActiveLayout(updated))
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = { actions += it },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun centreOf(label: String): Offset =
        composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center

    private fun cellWidth() =
        composeRule.onAllNodesWithTag(HOME_GRID_CELL_TEST_TAG)[0].fetchSemanticsNode().size.width.toFloat()

    private fun liftOver(
        from: String,
        over: String,
        offset: Offset = Offset.Zero,
    ) {
        val start = centreOf(from)
        val end = centreOf(over) + offset
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(1f, 1f))
            moveTo(end)
        }
        composeRule.mainClock.advanceTimeBy(HOLD_PAST_FOLDER_HOLD_MILLIS)
        composeRule.waitForIdle()
    }

    private fun outlines() = composeRule.onAllNodesWithTag(HOME_DRAG_STACK_OUTLINE_TEST_TAG).fetchSemanticsNodes()

    @Test
    fun holdingAWidgetOverTheCentreOfOneOfTheSameSpanOffersAStackAndLettingGoMakesIt() {
        show()
        liftOver(from = "Dial", over = "Gauge")

        assertEquals("the target did not wear the stack outline", 1, outlines().size)
        composeRule.captureScreen()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        val drop = actions.filterIsInstance<LauncherShellAction.MoveHomeShortcutToCell>().single()
        assertEquals(dial.id, drop.itemId)
        assertEquals(gauge.placement!!.cell, drop.cell)
        val result =
            applyShortcutOrFolderDropEdit(drop, shell.homeLayout, HomeShortcutEngine(), FolderEngine())
        val layout = (result as com.riffle.app.launcher.ShortcutOrFolderDropEditResult.Updated).layout
        val stack = layout.selectedPage.items.filterIsInstance<WidgetItem>().first { it.id == gauge.id }
        assertEquals(listOf(11, 12), stack.members.map { it.appWidgetId.value })
        assertTrue(layout.selectedPage.items.none { it.id == dial.id })
    }

    @Test
    fun theStackStateWaitsForTheFolderHold() {
        show()
        val start = centreOf("Dial")
        val end = centreOf("Gauge")
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(1f, 1f))
            moveTo(end)
        }
        composeRule.mainClock.advanceTimeBy(HALF_THE_HOLD_MILLIS)
        composeRule.waitForIdle()

        assertTrue("shown before the hold", outlines().isEmpty())
        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun aWidgetOfAnotherSpanIsPushedAndNeverOffersAStack() {
        show()
        liftOver(from = "Strip", over = "Gauge")

        assertTrue("a stack was offered for another span", outlines().isEmpty())
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        assertTrue(
            actions.toString(),
            actions.filterIsInstance<LauncherShellAction.MoveHomeShortcutToCell>().isEmpty(),
        )
    }

    @Test
    fun passingOverTheEdgeOfAWidgetOnlyPushesIt() {
        show()
        // Over the target's own edge, inside its span but outside the dead zone around its centre.
        liftOver(from = "Dial", over = "Gauge", offset = Offset(-cellWidth() * 0.85f, 0f))

        assertTrue("a stack was offered at the edge", outlines().isEmpty())
        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun aStackOnHomeIsDrawnByTheStackAndKeepsItsOwnSlot() {
        shell = ScreenshotFixtures.homeState()
        val stacked = fixtureStack(labels = listOf("Gauge", "Dial", "Level"), shown = 0)
        val layout = shell.homeLayout
        val page = layout.selectedPage.copy(items = listOf(stacked))
        val updated = layout.copy(pages = listOf(page) + layout.pages.drop(1))
        shell = shell.copy(homeLayout = updated, homeLayoutSet = shell.homeLayoutSet.withActiveLayout(updated))
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = { actions += it },
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithTag(HOME_WIDGET_STACK_TEST_TAG).fetchSemanticsNodes().size)
        composeRule.captureScreen()
    }

    private companion object {
        const val HOLD_PAST_FOLDER_HOLD_MILLIS = 400L
        const val HALF_THE_HOLD_MILLIS = 100L
    }
}
