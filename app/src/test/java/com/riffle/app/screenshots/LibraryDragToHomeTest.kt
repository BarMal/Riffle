package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.riffle.app.launcher.ExternalHomeDragSource
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.library.LibraryView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Dragging an app from the Library to Home (every View): a long-press opens the app's menu as before; moving on past
 * the drag distance dismisses it, closes the Library and continues as a Home drag of a new item. Released without moving
 * the menu stays; a drop on a target adds, a cancel adds nothing; an app Home shows already is not offered.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class LibraryDragToHomeTest : LongPressBehaviorBase() {
    private fun drops() = actions.filterIsInstance<LauncherShellAction.DropExternalHomeItem>()

    private fun SemanticsNodeInteraction.carry(
        endAtFraction: Offset = Offset(0.5f, 0.4f),
        finish: androidx.compose.ui.test.TouchInjectionScope.() -> Unit = { up() },
    ) {
        val window = windowDp()
        val start = fetchSemanticsNode().boundsInRoot.center
        // Injected through the root, so every point is in the window's own coordinates.
        root().performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(6f * dp, 0f))
            moveBy(Offset(20f * dp, 0f))
            moveTo(Offset(window.width * endAtFraction.x, window.height * endAtFraction.y))
            moveBy(Offset(2f, 2f))
            finish()
        }
        composeRule.waitForIdle()
    }

    private fun assertCarriedToHome(view: LibraryView) {
        show(folded().libraryOpenAs(view))
        composeRule.onNodeWithText("Alarm").carry()

        val drop = drops().singleOrNull()
        assertTrue("$view: $actions", drop != null)
        assertEquals("Alarm", (drop!!.source as ExternalHomeDragSource.Library).app.label)
        assertTrue("$view closes the Library: $actions", LauncherShellAction.OpenHome in actions)
    }

    @Test
    fun anAppHeldAndMovedInTheAlphabeticalListBecomesAHomeDrop() = assertCarriedToHome(LibraryView.AlphaList)

    @Test
    fun anAppHeldAndMovedInTheGridBecomesAHomeDrop() = assertCarriedToHome(LibraryView.Grid)

    @Test
    fun anAppHeldAndMovedInThePagedViewBecomesAHomeDrop() = assertCarriedToHome(LibraryView.Paged)

    @Test
    fun anAppHeldAndMovedInTheCategoriesBecomesAHomeDrop() {
        show(folded().libraryOpenAs(LibraryView.Categories))
        // Categories draw each app as an icon with its name as the description.
        val tile = composeRule.onAllNodesWithContentDescription("Alarm icon", useUnmergedTree = true).onFirst()
        tile.carry()
        val drop = drops().singleOrNull()
        assertTrue(actions.toString(), drop != null)
        assertEquals("Alarm", (drop!!.source as ExternalHomeDragSource.Library).app.label)
        assertTrue(actions.toString(), LauncherShellAction.OpenHome in actions)
    }

    @Test
    fun justAfterTheMenuBecomesAHomeDragTheLibraryHasGoneAndTheAppIsUnderTheFinger() {
        show(folded().libraryOpenAs(LibraryView.AlphaList))
        val start = composeRule.onNodeWithText("Alarm").fetchSemanticsNode().boundsInRoot.center
        val window = windowDp()
        root().performTouchInput {
            down(start)
            advanceEventTime(700)
            moveBy(Offset(6f * dp, 0f))
            moveBy(Offset(20f * dp, 0f))
            moveTo(Offset(window.width * 0.5f, window.height * 0.4f))
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Add to Home").assertCountEquals(0)
        composeRule.captureScreen()
        root().performTouchInput { up() }
    }

    @Test
    fun releasedWithoutMovingTheMenuStaysAndNothingIsAdded() {
        show(folded().libraryOpenAs(LibraryView.AlphaList))
        composeRule.onNodeWithText("Alarm").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(1f, 1f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add to Home").assertIsDisplayed()
        assertTrue(actions.toString(), drops().isEmpty())
        assertTrue(actions.toString(), LauncherShellAction.OpenHome !in actions)
    }

    @Test
    fun aDropOutsideAnyTargetAddsNothingAndStillClosesTheLibrary() {
        show(folded().libraryOpenAs(LibraryView.AlphaList))
        // Over the status bar: no page, no Dock, no Remove.
        composeRule.onNodeWithText("Alarm").carry(endAtFraction = Offset(0.5f, 0.01f))
        assertTrue(actions.toString(), drops().isEmpty())
        assertTrue(actions.toString(), LauncherShellAction.OpenHome in actions)
    }

    @Test
    fun anAppHomeShowsAlreadyIsNotOfferedTheDrag() {
        show(folded().libraryOpenAs(LibraryView.AlphaList))
        composeRule.onNodeWithText("Calendar").carry()
        assertTrue(actions.toString(), drops().isEmpty())
        assertTrue(actions.toString(), LauncherShellAction.OpenHome !in actions)
    }
}
