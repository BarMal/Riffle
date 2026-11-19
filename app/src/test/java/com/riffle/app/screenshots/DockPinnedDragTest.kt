package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.RIFFLE_CONTEXT_MENU_TEST_TAG
import com.riffle.app.launcher.dockItemTestTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Dock's pinned items are picked up like Home's, no edit mode, through the real [HomeDestination]: a long-press
 * then
 * a move lifts the item and reorders the run (or drags it out onto Home); a hold that does not move is the item's menu.
 * A Home item dragged down onto the Dock is pinned where it hovers. The decisions are the domain's and the drop rules'
 * (`dockDragDropAction`, `dockDropOnHome`, `dockInsertionIndex`); this is the arbitration through real touches.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPinnedDragTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(ScreenshotFixtures.homeState().withManyApps())

    private fun show() {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = { action ->
                        actions += action
                        shell = shell.answering(action)
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** The window's root: an open menu is a second root, and the touches belong to the first. */
    private fun mainRoot() = composeRule.onAllNodes(isRoot())[0]

    private val pinned get() = shell.homeLayout.dock.items

    private fun itemBounds(index: Int) =
        composeRule.onNodeWithTag(dockItemTestTag(pinned[index].id)).fetchSemanticsNode().boundsInRoot

    private fun menuShown() =
        composeRule.onAllNodesWithTag(RIFFLE_CONTEXT_MENU_TEST_TAG).fetchSemanticsNodes().isNotEmpty()

    /** Presses at [start], holds past the long-press timeout, then moves to each of [path] and lifts. */
    private fun holdThenDrag(
        start: Offset,
        path: List<Offset>,
    ) {
        mainRoot().performTouchInput {
            down(start)
            advanceEventTime(HOLD_MILLIS)
        }
        composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
        mainRoot().performTouchInput {
            path.forEach { to ->
                moveTo(to, delayMillis = 16)
            }
            up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aHoldWithoutMovingOnAPinnedAppOpensItsMenuAndReordersNothing() {
        show()
        val start = itemBounds(0).center
        mainRoot().performTouchInput { down(start) }
        composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
        mainRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        assertTrue("the hold did not open the menu", menuShown())
        assertTrue(actions.toString(), actions.none { it is LauncherShellAction.MoveDockShortcutToIndex })
    }

    @Test
    fun aHoldThenAMoveAlongTheRunReordersThePinnedItemWithoutEditMode() {
        show()
        val start = itemBounds(0).center
        val stride = itemBounds(1).center.x - start.x
        holdThenDrag(start, listOf(start + Offset(stride * 0.9f, 0f), start + Offset(stride * 1.6f, 0f)))

        val move = actions.filterIsInstance<LauncherShellAction.MoveDockShortcutToIndex>().singleOrNull()
        assertTrue(actions.toString(), move != null)
        assertEquals(pinned[0].id, move!!.itemId)
        assertTrue("the item did not move later: ${move.targetIndex}", move.targetIndex >= 1)
        assertTrue("the menu stayed open under the drag", !menuShown())
    }

    @Test
    fun aPinnedItemDraggedOutOntoAFreeHomeCellIsPlacedThere() {
        show()
        val cells = composeRule.onAllNodesWithTag(HOME_GRID_CELL_TEST_TAG).fetchSemanticsNodes()
        val target = cells.last().boundsInRoot.center
        val start = itemBounds(0).center
        holdThenDrag(start, listOf(start + Offset(0f, -60f), target))

        val move = actions.filterIsInstance<LauncherShellAction.MoveDockItemToHome>().singleOrNull()
        assertTrue(actions.toString(), move != null)
        assertEquals(pinned[0].id, move!!.itemId)
        assertEquals(shell.homeLayout.selectedPage.id, move.pageId)
        val grid = shell.homeLayout.selectedPage.grid
        assertEquals(
            "the last cell of the grid",
            com.riffle.core.domain.launcher.home.GridCell(grid.columns - 1, grid.rows - 1),
            move.cell,
        )
    }

    @Test
    fun aPinnedItemDroppedWhereHomeHasNoPageUnderTheFingerReturns() {
        show()
        val start = itemBounds(0).center
        // Far up in the status bar area, off the grid: not a target.
        holdThenDrag(start, listOf(start + Offset(0f, -60f), Offset(start.x, 2f)))

        assertTrue(actions.toString(), actions.none { it is LauncherShellAction.MoveDockItemToHome })
        assertTrue(actions.toString(), actions.none { it is LauncherShellAction.MoveDockShortcutToIndex })
    }

    @Test
    fun aHomeItemDraggedOntoTheDockIsPinnedWhereItHovers() {
        show()
        val firstItem = itemBounds(0)
        val lastItem = itemBounds(pinned.lastIndex)
        val start = composeRule.onNodeWithText("Calendar").fetchSemanticsNode().boundsInRoot.center
        holdThenDrag(start, listOf(start + Offset(0f, 40f), Offset(firstItem.left + 2f, firstItem.center.y)))
        val first = actions.filterIsInstance<LauncherShellAction.MoveHomeItemToDock>().singleOrNull()
        assertTrue(actions.toString(), first != null)
        assertEquals("pinned at the start of the Dock", 0, first!!.targetIndex)

        actions.clear()
        holdThenDrag(start, listOf(start + Offset(0f, 40f), Offset(lastItem.right - 2f, lastItem.center.y)))
        val last = actions.filterIsInstance<LauncherShellAction.MoveHomeItemToDock>().singleOrNull()
        assertTrue(actions.toString(), last != null)
        assertEquals("pinned at the end of the Dock", pinned.size, last!!.targetIndex)
    }

    private companion object {
        const val HOLD_MILLIS = 700L
    }
}
