package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.riffle.app.launcher.HOME_GROUP_GHOST_PROBE_KEY
import com.riffle.app.launcher.HOME_GROUP_GHOST_TEST_TAG
import com.riffle.app.launcher.HOME_REMOVE_TARGET_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_CLEAR_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_COUNT_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_FOLDER_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_REMOVE_CONFIRM_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_REMOVE_TEST_TAG
import com.riffle.app.launcher.HOME_SELECTION_SELECT_ALL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.app.launcher.applyEdit
import com.riffle.app.launcher.applyShortcutOrFolderDropEdit
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine
import com.riffle.core.domain.launcher.home.HomeSelection
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Multi-select in Edit page through the real [HomeDestination]: a tap ticks an app or folder (check, state, count), the
 * bar selects all and clears, removes (asked once, with an Undo) and makes a folder, and a tick lifts the group
 * together: a stacked ghost with the count, a drop from the cell onward, a drop on Remove asks once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeMultiSelectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private val probe = RecompositionProbe()
    private var shell by mutableStateOf(editing(ScreenshotFixtures.homeState()))

    private fun editing(state: LauncherShellState): LauncherShellState {
        val layout = state.homeLayout.copy(editMode = HomeEditMode.EditingPage(state.homeLayout.selectedPageId))
        return state.copy(homeLayout = layout, homeLayoutSet = state.homeLayoutSet.withActiveLayout(layout))
    }

    private fun LauncherShellState.answered(action: LauncherShellAction): LauncherShellState {
        val updated =
            when (action) {
                is LauncherShellAction.UndoHomeBulkEdit -> action.before
                is LauncherShellAction.DropHomeGroup ->
                    (
                        applyShortcutOrFolderDropEdit(action, homeLayout, HomeShortcutEngine(), FolderEngine())
                            as? com.riffle.app.launcher.ShortcutOrFolderDropEditResult.Updated
                    )?.layout
                else -> (HomePageEngine().applyEdit(action, homeLayout) as? HomePageEditResult.Updated)?.layout
            } ?: return this
        return copy(homeLayout = updated, homeLayoutSet = homeLayoutSet.withActiveLayout(updated))
    }

    private fun show() {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalRecompositionProbe provides probe) {
                    HomeDestination(
                        state = shell,
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = emptyStreamHost(),
                        onAction = { action ->
                            actions += action
                            shell = shell.answered(action)
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val apps get() = shell.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>()

    private fun tap(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    private fun selected() = HomeSelection.selectedIds(shell.homeLayout)

    private fun stateOf(label: String) =
        composeRule.onNodeWithText(label, useUnmergedTree = false).fetchSemanticsNode().config
            .let { config -> if (SemanticsProperties.StateDescription in config) config[SemanticsProperties.StateDescription] else null }

    @Test
    fun aTapTicksAnAppWithAStateTheCountAndAnUntickRemovesIt() {
        show()
        val first = apps[0]
        tap(first.label)
        assertEquals(setOf(first.id), selected())
        assertEquals("Selected", stateOf(first.label))
        composeRule.onNodeWithTag(HOME_SELECTION_COUNT_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        // A tap while editing no longer opens the menu.
        composeRule.onAllNodesWithText("Remove from Home").assertCountEquals(1) // the bar's button only
        tap(first.label)
        assertTrue(selected().isEmpty())
        assertEquals("Not selected", stateOf(first.label))
    }

    @Test
    fun selectAllAndClearWorkFromTheBarWithoutDragging() {
        show()
        composeRule.onNodeWithTag(HOME_SELECTION_SELECT_ALL_TEST_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(HomeSelection.selectableIds(shell.homeLayout.selectedPage).toSet(), selected())
        composeRule.captureScreen()
        composeRule.onNodeWithTag(HOME_SELECTION_CLEAR_TEST_TAG).performClick()
        composeRule.waitForIdle()
        assertTrue(selected().isEmpty())
        composeRule.onAllNodesWithTag(HOME_SELECTION_COUNT_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun removingFromTheBarAsksOnceAndUndoPutsEverythingBack() {
        show()
        val before = shell.homeLayout.selectedPage.items.size
        tap(apps[0].label)
        tap(apps[1].label)
        composeRule.onNodeWithTag(HOME_SELECTION_REMOVE_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Remove from Home?").assertIsDisplayed()
        assertEquals("nothing is removed before the answer", before, shell.homeLayout.selectedPage.items.size)

        composeRule.onNodeWithTag(HOME_SELECTION_REMOVE_CONFIRM_TEST_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(before - 2, shell.homeLayout.selectedPage.items.size)
        composeRule.onNodeWithText("Removed 2 items").assertIsDisplayed()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(before, shell.homeLayout.selectedPage.items.size)
    }

    @Test
    fun makeAFolderIsOfferedForAppsOnlyAndMakesOne() {
        show()
        tap(apps[0].label)
        composeRule.onNodeWithTag(HOME_SELECTION_FOLDER_TEST_TAG).assertIsDisplayed()
        tap(apps[1].label)
        val names = setOf(apps[0].appIdentity, apps[1].appIdentity)
        composeRule.onNodeWithTag(HOME_SELECTION_FOLDER_TEST_TAG).performClick()
        composeRule.waitForIdle()
        val folder = shell.homeLayout.selectedPage.items.filterIsInstance<FolderItem>().single()
        assertEquals(names, folder.items.map { it.appIdentity }.toSet())
    }

    private fun liftGroup(): Offset {
        tap(apps[0].label)
        tap(apps[1].label)
        val start = composeRule.onNodeWithText(apps[0].label).fetchSemanticsNode().boundsInRoot.center
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(40f, 40f))
        }
        composeRule.waitForIdle()
        return start
    }

    @Test
    fun draggingATickedItemLiftsTheGroupAsOneStackedGhostWithTheCount() {
        show()
        liftGroup()
        composeRule.onNodeWithTag(HOME_GROUP_GHOST_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.captureScreen()
        composeRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun theGroupDropsFromTheDropCellOnwardAndTheSelectionIsSpent() {
        show()
        val appsBefore = apps.size
        val start = liftGroup()
        val cells = composeRule.onAllNodesWithTag(com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG).fetchSemanticsNodes()
        val target = cells.last().boundsInRoot.center
        composeRule.onRoot().performTouchInput {
            moveTo(target)
            moveBy(Offset(1f, 1f))
            up()
        }
        composeRule.waitForIdle()
        val drop = actions.filterIsInstance<LauncherShellAction.DropHomeGroup>().single()
        assertEquals(shell.homeLayout.selectedPage.id, drop.targetPageId)
        assertTrue(start != target)
        assertTrue("both left where they were: ${selected()}", selected().isEmpty())
        // Nothing is lost: the last cell holds the first and the second spilled onward.
        assertEquals(appsBefore, shell.homeLayout.pages.flatMap { it.items }.filterIsInstance<AppShortcutItem>().size)
    }

    @Test
    fun droppingTheGroupOnRemoveAsksOnceAndRemovesNothingUntilAnswered() {
        show()
        val before = shell.homeLayout.selectedPage.items.size
        liftGroup()
        val target = composeRule.onNodeWithTag(HOME_REMOVE_TARGET_TEST_TAG).fetchSemanticsNode().boundsInRoot.center
        composeRule.onRoot().performTouchInput {
            moveTo(target)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Remove from Home?").assertIsDisplayed()
        assertEquals(before, shell.homeLayout.selectedPage.items.size)
        composeRule.onNodeWithTag(HOME_SELECTION_REMOVE_CONFIRM_TEST_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(before - 2, shell.homeLayout.selectedPage.items.size)
        assertTrue(actions.toString(), actions.none { it is LauncherShellAction.RemoveHomeShortcut })
    }

    @Test
    fun movingTheGroupAroundRecomposesTheGhostNoMoreThanOnce() {
        show()
        liftGroup()
        val settled = probe.count(HOME_GROUP_GHOST_PROBE_KEY)
        composeRule.onRoot().performTouchInput { repeat(20) { moveBy(Offset(4f, 4f)) } }
        composeRule.waitForIdle()
        assertTrue(
            "${probe.count(HOME_GROUP_GHOST_PROBE_KEY)} after $settled",
            probe.count(HOME_GROUP_GHOST_PROBE_KEY) - settled <= 1,
        )
        composeRule.onRoot().performTouchInput { up() }
    }
}
