package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.applyEdit
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pick-up and drop on Home through the real [HomeDestination]: one arbitration, no edit mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeDragBehaviorTest {
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
                        shell = shell.answering(action).answeringPages(action)
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun cellWidth(): Float {
        val cell = composeRule.onAllNodesWithTag(HOME_GRID_CELL_TEST_TAG)[0].fetchSemanticsNode()
        return cell.size.width.toFloat()
    }

    @Test
    fun aLongPressThenAMoveLiftsAnAppAndDropsItInTheNextCellWithoutAnyEditMode() {
        show()
        val step = cellWidth()

        composeRule.onNodeWithText("Calendar").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(step * 0.6f, 0f))
            moveBy(Offset(step * 0.6f, 0f))
            up()
        }
        composeRule.waitForIdle()

        assertTrue(actions.toString(), actions.any { it.isHomeDrop() })
    }

    private fun labels(): List<String> =
        shell.homeLayout.selectedPage.items.mapNotNull { item ->
            (item as? com.riffle.core.domain.launcher.home.AppShortcutItem)?.label
                ?: (item as? com.riffle.core.domain.launcher.home.FolderItem)?.label
        }

    private fun liftAndMove(
        label: String,
        from: (androidx.compose.ui.test.TouchInjectionScope) -> Offset,
        dx: Float,
        dy: Float,
    ) {
        composeRule.onNodeWithText(label).performTouchInput {
            down(from(this))
            advanceEventTime(700)
            moveBy(Offset(dx / 2, dy / 2))
            moveBy(Offset(dx / 2, dy / 2))
            up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun everyItemOnTheGridCanBePickedUpFromTheIconTheLabelAndNearItsEdge() {
        show()
        val step = cellWidth()
        val names = labels()
        assertTrue(names.isNotEmpty())
        val starts: List<(androidx.compose.ui.test.TouchInjectionScope) -> Offset> =
            listOf(
                { s -> s.center },
                { s -> Offset(s.width / 2f, 2f) },
                { s -> Offset(2f, s.height / 2f) },
                { s -> Offset(s.width - 2f, s.height / 2f) },
            )
        val failures = mutableListOf<String>()
        names.forEach { name ->
            starts.forEachIndexed { index, start ->
                actions.clear()
                val column =
                    shell.homeLayout.selectedPage.items
                        .first { (it as? AppShortcutItem)?.label == name || (it as? FolderItem)?.label == name }
                        .placement!!.cell.column
                val lastColumn = shell.homeLayout.selectedPage.grid.columns - 1
                liftAndMove(name, start, if (column >= lastColumn) -step * 1.2f else step * 1.2f, 0f)
                if (actions.none { it.isHomeDrop() }) {
                    failures += "$name start#$index: $actions"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun withSecondPage() {
        val layout = shell.homeLayout
        val second =
            com.riffle.core.domain.launcher.home.LauncherPage(
                id = com.riffle.core.domain.launcher.home.LauncherPageId("home-second"),
                grid = layout.selectedPage.grid,
                items =
                    listOf(
                        com.riffle.core.domain.launcher.home.AppShortcutItem(
                            id = com.riffle.core.domain.launcher.home.LauncherItemId("second-app"),
                            appIdentity = shell.installedApps.last().identity,
                            label = shell.installedApps.last().label,
                            placement =
                                com.riffle.core.domain.launcher.home.GridPlacement(
                                    com.riffle.core.domain.launcher.home.GridCell(0, 0),
                                ),
                        ),
                    ),
            )
        val twoPages = layout.copy(pages = layout.pages + second)
        shell = shell.copy(homeLayout = twoPages, homeLayoutSet = shell.homeLayoutSet.withActiveLayout(twoPages))
        show()
        assertTrue("needs two pages: ${shell.homeLayout.pages.size}", shell.homeLayout.pages.size >= 2)
    }

    @Test
    fun aHeldDropOnAnItemOfThePageTurnedToMakesTheFolderThereWithTheDeadZone() {
        withSecondPage()
        val name = labels().first()
        val rootRight = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        val start = composeRule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.center
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(10f, 0f))
            moveTo(Offset(rootRight - 6f, start.y))
            moveBy(Offset(1f, 0f))
        }
        // Past the edge hold (500 ms) and before the next repeat (a new page is offered while still held on the last).
        composeRule.mainClock.advanceTimeBy(900)
        composeRule.waitForIdle()
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        // The first cell (column 0, row 0) of the page now on screen: the centre of its icon cell is the dead zone.
        val target =
            composeRule.onAllNodesWithTag(HOME_GRID_CELL_TEST_TAG).fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .first { it.center.x in screen.left..screen.right && it.center.y in screen.top..screen.bottom }
                .center
        composeRule.onRoot().performTouchInput {
            moveTo(target)
            moveBy(Offset(1f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        val drop = actions.filterIsInstance<LauncherShellAction.DropHomeItem>().singleOrNull()
        assertTrue(actions.toString(), drop != null && drop.dropOnItemId?.value == "second-app")
    }

    @Test
    fun anItemHeldAtTheEdgeTurnsThePageAndDropsOnTheNextOne() {
        withSecondPage()
        val first = shell.homeLayout.pages.first()
        val name = labels().first()

        val rootRight = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        val start = composeRule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.center
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(10f, 0f))
            moveTo(Offset(rootRight - 6f, start.y))
            moveBy(Offset(1f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        val drop = actions.filterIsInstance<LauncherShellAction.DropHomeItem>().singleOrNull()
        assertTrue(first.id.value + " " + actions, drop != null && drop.targetPageId != drop.sourcePageId)
    }

    /** The page actions a drag sends, answered by the real page engine. */
    private fun com.riffle.core.domain.launcher.LauncherShellState.answeringPages(
        action: LauncherShellAction,
    ): com.riffle.core.domain.launcher.LauncherShellState {
        if (action !is LauncherShellAction.AddHomePage && action !is LauncherShellAction.DiscardEmptyHomePage) return this
        val result = com.riffle.core.domain.launcher.home.HomePageEngine().applyEdit(action, homeLayout)
        val updated = (result as? com.riffle.core.domain.launcher.home.HomePageEditResult.Updated)?.layout ?: return this
        return copy(homeLayout = updated, homeLayoutSet = homeLayoutSet.withActiveLayout(updated))
    }

    private fun holdAtTheTrailingEdge() {
        val rootRight = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        val name = labels().first()
        val start = composeRule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.center
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(10f, 0f))
            moveTo(Offset(rootRight - 6f, start.y))
            moveBy(Offset(1f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
    }

    private fun pageSelectorState() =
        composeRule.onNodeWithContentDescription("Page selector").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]

    @Test
    fun heldAtTheTrailingEdgeOfTheLastPageOffersANewPageThatTheDropKeepsAndTheDotsShow() {
        show()
        val pagesBefore = shell.homeLayout.pages.size
        holdAtTheTrailingEdge()
        assertTrue(actions.toString(), actions.any { it is LauncherShellAction.AddHomePage })
        assertEquals(pagesBefore + 1, shell.homeLayout.pages.size)
        assertEquals(
            "the dots show the new page as the target",
            "Page ${pagesBefore + 1} of ${pagesBefore + 1}",
            pageSelectorState(),
        )

        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        val drop = actions.filterIsInstance<LauncherShellAction.DropHomeItem>().singleOrNull()
        assertTrue(actions.toString(), drop != null && drop.targetPageId == shell.homeLayout.pages.last().id)
        assertTrue(actions.toString(), actions.none { it is LauncherShellAction.DiscardEmptyHomePage })
    }

    @Test
    fun aCancelledDragDiscardsTheOfferedPage() {
        show()
        val pagesBefore = shell.homeLayout.pages.size
        holdAtTheTrailingEdge()
        assertEquals(pagesBefore + 1, shell.homeLayout.pages.size)

        composeRule.onRoot().performTouchInput { cancel() }
        composeRule.waitForIdle()
        assertTrue(actions.toString(), actions.any { it is LauncherShellAction.DiscardEmptyHomePage })
        assertEquals(pagesBefore, shell.homeLayout.pages.size)
    }

    private fun LauncherShellAction.isHomeDrop() =
        this is LauncherShellAction.MoveHomeShortcutToCell || this is LauncherShellAction.DropHomeItem

    private fun cellCentre(
        column: Int,
        row: Int,
    ): Offset {
        val columns = shell.homeLayout.selectedPage.grid.columns
        return composeRule.onAllNodesWithTag(HOME_GRID_CELL_TEST_TAG)[row * columns + column]
            .fetchSemanticsNode().boundsInRoot.center
    }

    private fun itemCell(name: String) =
        shell.homeLayout.selectedPage.items.first { (it as? AppShortcutItem)?.label == name }.placement!!.cell

    @Test
    fun aDragInProgressShowsTheGhostTheLandingOutlineAndPushedNeighbours() {
        show()
        val names = labels()
        val dragged = names.first()
        val from = cellCentre(itemCell(dragged).column, itemCell(dragged).row)
        // Between the second item and its neighbour (outside both dead zones, inside the ring of both).
        val other = itemCell(names[1])
        val centre = cellCentre(other.column, other.row)
        val step = cellWidth()
        composeRule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(700)
            moveBy(Offset(1f, 1f))
            moveTo(Offset(centre.x - step * 0.4f, centre.y))
            moveBy(Offset(1f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        composeRule.captureScreen()
        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun holdingOverAnAppsCentreShowsTheFolderStateAndDroppingThereMakesAFolder() {
        show()
        val names = labels()
        val dragged = names.first()
        val from = cellCentre(itemCell(dragged).column, itemCell(dragged).row)
        val other = itemCell(names[1])
        val centre = cellCentre(other.column, other.row)
        composeRule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(700)
            moveBy(Offset(1f, 1f))
            moveTo(centre)
            moveBy(Offset(1f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        composeRule.captureScreen()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        val drop = actions.filterIsInstance<LauncherShellAction.MoveHomeShortcutToCell>().single()
        assertTrue(drop.cell == other)
    }
}
