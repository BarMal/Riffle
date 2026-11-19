package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.down
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.app.launcher.homeGridCellProbeKey
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A Home drag moves every frame, and the finger's place must not recompose Home's cells: only the cells a drag touches
 * (the lifted item's source, the cells it passes over, the neighbours it pushes) change. The lifted icon and the
 * pointer are read in the layout and draw phases.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeDragFrameStabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val probe = RecompositionProbe()
    private var shell by mutableStateOf(ScreenshotFixtures.homeState().withManyApps())

    private fun show() {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalRecompositionProbe provides probe) {
                    HomeDestination(
                        state = shell,
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = emptyStreamHost(),
                        onAction = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun cellCounts(): Map<GridCell, Int> {
        val page = shell.homeLayout.selectedPage
        return (0 until page.grid.rows).flatMap { row ->
            (0 until page.grid.columns).map { column ->
                val cell = GridCell(column, row)
                cell to probe.count(homeGridCellProbeKey(page.id.value, cell))
            }
        }.toMap()
    }

    private fun firstApp() =
        shell.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>()
            .minByOrNull { it.placement!!.cell.row * 100 + it.placement!!.cell.column }!!

    private fun cellWidth() =
        composeRule.onAllNodesWithTag(HOME_GRID_CELL_TEST_TAG)[0].fetchSemanticsNode().size.width.toFloat()

    @Test
    fun theFingerMovingWithinOneCellRecomposesNoCellAtAll() {
        show()
        val first = firstApp()
        val cellWidth = cellWidth()
        val start = composeRule.onNodeWithText(first.label).fetchSemanticsNode().boundsInRoot.center
        val root = composeRule.onAllNodes(isRoot())[0]
        root.performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(cellWidth * 0.05f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.waitForIdle()
        val before = cellCounts()
        for (step in 1..STEPS) {
            val wobble = Offset(cellWidth * 0.1f * (step % 3 - 1), cellWidth * 0.1f * (step % 2))
            root.performTouchInput { moveTo(start + wobble, delayMillis = 16) }
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.waitForIdle()
        val after = cellCounts()
        root.performTouchInput { cancel() }
        val changed = after.filter { it.value != before.getValue(it.key) }
        assertTrue("moving within one cell recomposed cells: $changed", changed.isEmpty())
    }

    @Test
    fun draggingAcrossThreeCellsRecomposesNoCellThatIsNotTouched() {
        show()
        val first = firstApp()
        val source = first.placement!!.cell
        val cellWidth = cellWidth()
        val start = composeRule.onNodeWithText(first.label).fetchSemanticsNode().boundsInRoot.center
        val root = composeRule.onAllNodes(isRoot())[0]

        root.performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(cellWidth * 0.2f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.waitForIdle()
        val before = cellCounts()
        // Across three cells, a frame apart.
        for (step in 1..STEPS) {
            root.performTouchInput { moveTo(start + Offset(cellWidth * 2.2f * step / STEPS, 0f), delayMillis = 16) }
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.waitForIdle()
        val after = cellCounts()
        root.performTouchInput { cancel() }

        val reached = (0..2).map { GridCell(source.column + it, source.row) }
        val untouched =
            after.keys.filter { cell ->
                cell != source && cell !in reached &&
                    // The cells a push can reach (the ring is under a cell wide; an item moves to the nearest free
                    // cell): two rows around the path.
                    kotlin.math.abs(cell.row - source.row) > 2
            }
        val recomposed = untouched.filter { after.getValue(it) != before.getValue(it) }
        assertTrue("cells far from the drag recomposed: $recomposed", recomposed.isEmpty())
        val perMove = (after.getValue(source) - before.getValue(source))
        assertTrue("the source cell recomposed on every move ($perMove)", perMove <= MAX_SOURCE_RECOMPOSITIONS)
        assertEquals("a cell off the path", 0, recomposed.size)
    }

    private companion object {
        const val STEPS = 12
        const val MAX_SOURCE_RECOMPOSITIONS = 3
    }
}
