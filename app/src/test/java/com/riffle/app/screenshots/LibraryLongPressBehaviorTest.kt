package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.settings.HomeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * What a long-press opens on the other surfaces: the Library's own "Show as" from its margin, its sheet's empty space
 * and its paged View's handle (and never closing it, while a tap on its margin still does), and App library Home's
 * own menu.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class LibraryLongPressBehaviorTest : LongPressBehaviorBase() {
    // The other surfaces' own menus

    @Test
    fun theLibrarysMarginAndSheetOpenShowAsAndNeverCloseIt() {
        show(folded().libraryOpenAs(LibraryView.Categories))
        longPressAt(3f, 400f)
        composeRule.onNodeWithText("Show as").assertIsDisplayed()
        assertEquals(1, haptics.count)
        assertTrue("a long-press does not close the Library: $actions", actions.isEmpty())
        composeRule.onAllNodesWithText("Edit page").assertCountEquals(0)
    }

    @Test
    fun theLibrarySheetsOwnEmptySpaceOpensShowAs() {
        show(folded().libraryOpenAs(LibraryView.Categories))
        // Below the last tile of the sheet, inside its glass.
        longPressAt(200f, 700f)
        composeRule.onNodeWithText("Show as").assertIsDisplayed()
        assertEquals(1, haptics.count)
    }

    @Test
    fun aTapOnTheLibrarysMarginStillClosesIt() {
        show(folded().libraryOpenAs(LibraryView.Categories))
        root().performTouchInput { click(Offset(3f * dp, 400f * dp)) }
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun theAppLibraryHomesMarginOpensItsOwnMenu() {
        show(folded().withHomeMode(HomeMode.AppLibrary))
        longPressAt(3f, 400f)
        composeRule.onNodeWithText("Show placed items").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        assertEquals(1, haptics.count)
        composeRule.onAllNodesWithText("Edit page").assertCountEquals(0)
    }

    @Test
    fun thePagedLibrarysMarginAndHandleOpenShowAs() {
        show(folded().libraryOpenAs(LibraryView.Paged))
        longPressAt(3f, 400f)
        composeRule.onNodeWithText("Show as").assertIsDisplayed()
        assertEquals(1, haptics.count)
        root().performTouchInput { longClick(handle().center) }
        assertEquals("the handle's long-press is the same menu", 2, haptics.count)
        assertFalse("not search: $actions", LauncherShellAction.OpenSearch in actions)
    }
}
