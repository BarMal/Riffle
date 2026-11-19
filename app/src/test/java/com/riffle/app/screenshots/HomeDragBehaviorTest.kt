package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
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
                        shell = shell.answering(action)
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

    @Test
    fun anItemHeldAtTheEdgeTurnsThePageAndDropsOnTheNextOne() {
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

    private fun LauncherShellAction.isHomeDrop() =
        this is LauncherShellAction.MoveHomeShortcutToCell || this is LauncherShellAction.DropHomeItem
}
