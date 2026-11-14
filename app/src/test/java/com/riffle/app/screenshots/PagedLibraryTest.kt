package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.HOME_DOCK_TEST_TAG
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.library.LIBRARY_PAGED_TEST_TAG
import com.riffle.app.launcher.library.LIBRARY_PAGE_TEST_TAG_PREFIX
import com.riffle.app.launcher.library.LIBRARY_SEARCH_FIELD_TEST_TAG
import com.riffle.app.launcher.library.LIBRARY_SHEET_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.library.LibraryView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The paged Library View (ADR 0003, Amendment 1) through the real [HomeDestination]: every app as Home's grid on
 * horizontal pages, drawn over the wallpaper with no sheet behind it, with the Home handle's dots and quick-drag;
 * "Show as" switches it with Undo; the Dock stays under it. Twenty-six apps fill two pages of the fixture's 4 by 5
 * grid.
 *
 * The shell is a stand-in that answers the settings and navigation actions the way the real one does (see
 * [answering]) and records every action.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class PagedLibraryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(pagedState())

    private fun pagedState(): LauncherShellState =
        ScreenshotFixtures.homeState().withManyApps().libraryOpenAs(LibraryView.Paged)

    private fun show(state: LauncherShellState = shell) {
        shell = state
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

    private fun pageSelector() = composeRule.onNodeWithContentDescription("Page selector")

    private fun assertPage(text: String) =
        pageSelector().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text))

    // Looks

    @Test
    fun everyAppIsAnIconOnHomesGridWithNoSheetOrSearchFieldBehindIt() {
        show()

        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("$LIBRARY_PAGE_TEST_TAG_PREFIX" + "0").assertIsDisplayed()
        composeRule.onNodeWithText("Alarm").assertIsDisplayed()
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        // Page two holds the rest, so its apps are not composed yet.
        composeRule.onNodeWithText("Zoo").assertDoesNotExist()
        // No header, no field of its own: search is the Dock's.
        composeRule.onNodeWithTag(LIBRARY_SEARCH_FIELD_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Library").assertDoesNotExist()
        composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun theHandleIsHomesHandleWithTheLibrarysPagesAsItsDots() {
        show()

        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        assertPage("Page 1 of 2")
    }

    @Test
    fun aLibraryOfOnePageHasNoDotsButStillSearches() {
        show(ScreenshotFixtures.homeState().libraryOpenAs(LibraryView.Paged))

        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed().performClick()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
        composeRule.onNodeWithContentDescription("Page selector").assertDoesNotExist()
    }

    @Test
    fun pagedLibraryCompact() {
        show()
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun pagedLibraryCompactDark() {
        show()
        composeRule.captureScreen()
    }

    @Test
    fun pagedLibrarySecondPage() {
        show()
        composeRule.onNodeWithTag("${LIBRARY_PAGE_TEST_TAG_PREFIX}0").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Zoo").assertIsDisplayed()
        composeRule.captureScreen()
    }

    // Pages

    @Test
    fun swipingSidewaysMovesToTheNextPageAndTheDotsFollow() {
        show()

        composeRule.onNodeWithTag("${LIBRARY_PAGE_TEST_TAG_PREFIX}0").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Zoo").assertIsDisplayed()
        composeRule.onNodeWithText("Alarm").assertDoesNotExist()
        assertPage("Page 2 of 2")
    }

    @Test
    fun draggingAlongTheHandlesDotsQuickDragsToTheLastPageWithoutOpeningSearch() {
        show()

        pageSelector().performTouchInput {
            swipe(
                start = Offset(1f, height / 2f),
                end = Offset(width - 1f, height / 2f),
            )
        }
        composeRule.waitForIdle()

        assertPage("Page 2 of 2")
        composeRule.onNodeWithText("Zoo").assertIsDisplayed()
        assertTrue("a drag along the dots must not open search: $actions", LauncherShellAction.OpenSearch !in actions)
    }

    @Test
    fun theHandleHasNextAndPreviousPageActionsAsTheNonGestureRoute() {
        show()

        val labels = pageSelector().fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Next page"), labels)
        val next = pageSelector().fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        composeRule.runOnUiThread { next.action() }
        composeRule.waitForIdle()

        assertPage("Page 2 of 2")
    }

    // Apps

    @Test
    fun tappingAnAppLaunchesItAndClosesTheLibrary() {
        show()

        composeRule.onNodeWithText("Alarm").performClick()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.LaunchApp(pagedApp("Alarm").identity),
                LauncherShellAction.OpenHome,
            ),
            actions,
        )
    }

    @Test
    fun anAppOnAnotherPageLaunchesToo() {
        show()
        composeRule.onNodeWithTag("${LIBRARY_PAGE_TEST_TAG_PREFIX}0").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Zoo").performClick()

        assertEquals(LauncherShellAction.LaunchApp(pagedApp("Zoo").identity), actions.first())
    }

    @Test
    fun longPressingAnAppOffersItsMenu() {
        show()

        composeRule.onNodeWithText("Alarm").performTouchInput { longClick() }

        composeRule.onNodeWithText("Add to home").assertIsDisplayed()
        composeRule.onNodeWithText("Hide").assertIsDisplayed()
    }

    @Test
    fun iconsKeepHomesSizeSoThePagesReadAsItsSibling() {
        show()

        composeRule.onNode(hasContentDescription("Alarm icon"), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(shell.homeLayout.settings.labels.iconSizeDp.dp)
    }

    // Show as

    @Test
    fun longPressingTheEmptySpaceOffersShowAsAndChoosingARedrawsAtOnceWithUndo() {
        show()

        composeRule.longPressEmptySpace("${LIBRARY_PAGE_TEST_TAG_PREFIX}0")
        composeRule.onNodeWithText("Show as").assertIsDisplayed()
        composeRule.onNodeWithText("Grid").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.SelectLibraryView(LibraryView.Grid)), actions)
        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LIBRARY_SHEET_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Library shown as Grid").assertIsDisplayed()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.SelectLibraryView(LibraryView.Grid),
                LauncherShellAction.SelectLibraryView(LibraryView.Paged),
            ),
            actions,
        )
        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun theOtherViewsOfferPagedInShowAsToo() {
        show(shell.libraryOpenAs(LibraryView.Categories))

        composeRule.onNodeWithContentDescription("Library options").performClick()
        composeRule.onNodeWithText("Pages").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.SelectLibraryView(LibraryView.Paged)), actions)
        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun theSameRoutesAreCustomActionsForAssistiveTechnology() {
        show()

        val custom =
            composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        val labels = custom.map { it.label }
        assertTrue(
            labels.toString(),
            "Close Library" in labels && "Show as Grid" in labels && "Show as Pages" in labels,
        )

        composeRule.runOnUiThread { custom.single { it.label == "Close Library" }.action() }
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun pullingTowardTheDocksEdgeOnThePagesClosesTheLibrary() {
        show()

        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).performTouchInput {
            swipe(
                start = Offset(width / 2f, height * 0.2f),
                end = Offset(width / 2f, height * 0.9f),
                durationMillis = 150,
            )
        }
        composeRule.waitForIdle()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }
}
