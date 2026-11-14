package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HOME_WORKSPACE_GRID_OVERFLOW_TEST_TAG
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.PRESS_MENU_ANCHOR_TEST_TAG
import com.riffle.core.domain.launcher.home.DockPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * What a long-press opens on Home: Home's menu from every part of Home that is not an item (the margins, the system
 * bars, the room the Dock and the handle reserve, the handle itself, an empty page, the Dock's body), an item's own
 * menu from an item and never Home's, the menu hanging from the finger, a wobble within slop, the Dock's press against
 * the still press, and nothing from a Home that is hidden.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class LongPressBehaviorTest : LongPressBehaviorBase() {
    // Home's menu from everything that is not an item

    @Test
    fun theBottomMarginUnderTheNavigationBarOpensHomesMenu() {
        show(folded())
        longPressAt(200f, windowDp().height / dp - 6f)
        assertHomeMenu()
    }

    @Test
    fun theRoomUnderTheStatusBarOpensHomesMenu() {
        show(folded())
        longPressAt(200f, 6f)
        assertHomeMenu()
    }

    @Test
    fun theSideMarginsOpenHomesMenu() {
        show(folded())
        longPressAt(3f, 400f)
        assertHomeMenu()
    }

    @Test
    fun theRightSideMarginOpensHomesMenu() {
        show(folded())
        longPressAt(windowDp().width / dp - 3f, 400f)
        assertHomeMenu()
    }

    @Test
    fun theGapBetweenTheGridAndTheHandleOpensHomesMenu() {
        show(folded())
        val handle = composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        longPressAt(handle.center.x / dp, (handle.top / dp) - 10f)
        assertHomeMenu()
    }

    @Test
    fun theRoomBesideTheHandleOpensHomesMenu() {
        show(folded())
        val handle = composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        longPressAt(handle.left / dp - 60f, handle.center.y / dp)
        assertHomeMenu()
    }

    @Test
    fun theRoomBesideAndAroundTheDocksBodyOpensHomesMenu() {
        show(folded())
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val body = composeRule.onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        // The Dock's own strip beside its glass, which is the Dock's room and not an icon.
        longPressAt((dock.left / dp) + 4f, body.center.y / dp)
        assertHomeMenu()
        assertTrue("beside the glass: ${dock.left} < ${body.left}", dock.left < body.left)
    }

    @Test
    fun theGapBetweenTheDocksIconsOpensHomesMenu() {
        show(folded())
        val body = composeRule.onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        // Between the second and third of the four icons.
        val icons = composeRule.itemRects().filter { body.contains(it.center) }.sortedBy { it.center.x }
        val gap = (icons[1].right + icons[2].left) / 2f
        root().performTouchInput { longClick(Offset(gap, body.center.y)) }
        assertHomeMenu()
    }

    @Test
    fun aLongPressOnTheHandleOpensHomesMenuAndDoesNotSearch() {
        show(folded())
        root().performTouchInput { longClick(handle().center) }
        assertHomeMenu()
        assertFalse("not search: $actions", LauncherShellAction.OpenSearch in actions)
    }

    @Test
    fun aTapOnTheHandleStillSearches() {
        show(folded())
        composeRule.onNodeWithContentDescription("Search").performTouchInput { click() }
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
        assertEquals("no menu", 0, haptics.count)
    }

    @Test
    fun anEmptyPageOpensHomesMenu() {
        show(folded().onTheSecondPage())
        longPressAt(200f, 300f)
        assertHomeMenu()
    }

    @Test
    fun theRoomBesideAGeneratedPagesMoreAppsButtonOpensHomesMenuAndTheButtonStillWorks() {
        show(folded().withAGeneratedPage())
        val button = composeRule.onNodeWithTag(HOME_WORKSPACE_GRID_OVERFLOW_TEST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("as wide as its label, not the page: ${button.width}", button.width < 0.6f * windowDp().width)
        root().performTouchInput { longClick(Offset(button.left - 30f * dp, button.center.y)) }
        assertHomeMenu()
        root().performTouchInput { longClick(Offset(button.right + 30f * dp, button.center.y)) }
        assertEquals(2, haptics.count)
    }

    @Test
    fun aSideDocksStripAndTheHandlesColumnOpenHomesMenu() {
        show(folded(DockPosition.RIGHT))
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        longPressAt(dock.center.x / dp, 40f)
        assertHomeMenu()
    }

    // Items keep their own long-press

    @Test
    fun aLongPressOnAnAppOpensTheAppsMenuAndNeverHomes() {
        show(folded())
        composeRule.onNodeWithText("Calendar").performTouchInput { longClick() }
        composeRule.onNodeWithText("App info").assertIsDisplayed()
        composeRule.onAllNodesWithText("Edit page").assertCountEquals(0)
        assertEquals(listOf<LauncherShellAction>(), actions)
    }

    // The menu opens at the finger and stays on screen

    @Test
    fun theMenuHangsFromTheFingerWhereverItLands() {
        show(folded())
        listOf(Offset(200f, 300f), Offset(3f, 6f), Offset(windowDp().width / dp - 3f, windowDp().height / dp - 6f))
            .forEachIndexed { index, point ->
                longPressAt(point.x, point.y)
                val anchors =
                    composeRule.onAllNodesWithTag(PRESS_MENU_ANCHOR_TEST_TAG, useUnmergedTree = true)
                        .fetchSemanticsNodes()
                // The menus are the platform's popups, which keep themselves on screen; here is where each hangs from.
                val anchor = anchors.map { it.positionInRoot }.minBy { (it - point * dp).getDistance() }
                assertTrue(
                    "press $index: the menu hangs from $anchor, the finger was at ${point * dp}",
                    (anchor - point * dp).getDistance() < 2f * dp,
                )
            }
    }

    // A still finger with a little wobble is still a long-press

    @Test
    fun aLongPressThatWobblesWithinTouchSlopStillOpensTheMenu() {
        show(folded())
        root().performTouchInput {
            down(Offset(200f * dp, 300f * dp))
            moveBy(Offset(3f, 2f), delayMillis = 16)
            moveBy(Offset(-2f, 3f), delayMillis = 16)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up()
        }
        assertHomeMenu()
    }

    @Test
    fun aDragFromTheEmptyRoomIsNotALongPress() {
        show(folded())
        root().performTouchInput {
            down(Offset(200f * dp, 300f * dp))
            repeat(6) { moveBy(Offset(0f, -20f * dp), delayMillis = 16) }
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up()
        }
        assertEquals("no menu", 0, haptics.count)
    }

    // The Dock: a still press is the menu, a press toward its edge is the press

    @Test
    fun aStillLongPressOnTheDocksBodyIsTheMenuAndAPressTowardItsEdgeIsNot() {
        show(folded())
        composeRule.pressDock(distancePx = 150f)
        assertEquals("a press toward the edge is the Dock's press, not a menu", 0, haptics.count)
        composeRule.onAllNodesWithText("Edit page").assertCountEquals(0)

        val body = composeRule.onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        root().performTouchInput { longClick(Offset(body.left + 2f * dp, body.center.y)) }
        assertEquals("a still press is the menu", 1, haptics.count)
        composeRule.onNodeWithText("Edit page").assertIsDisplayed()
    }

    // Home hidden: nothing responds

    @Test
    fun aHomeThatSearchCoversTakesNoLongPress() {
        show(folded().searching())
        longPressAt(3f, 400f)
        longPressAt(200f, 6f)
        assertEquals("nothing opened on Home", 0, haptics.count)
        composeRule.onAllNodesWithText("Edit page").assertCountEquals(0)
    }
}
