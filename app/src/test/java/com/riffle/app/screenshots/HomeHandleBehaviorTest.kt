package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.library.LIBRARY_SHEET_TEST_TAG
import com.riffle.app.launcher.search.DOCK_SEARCH_FIELD_TEST_TAG
import com.riffle.app.launcher.search.DOCK_SEARCH_SURFACE_TEST_TAG
import com.riffle.app.launcher.search.DockSearchProgressKey
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Home handle and search from the Dock (ADR 0003, Amendment 1) through the real [HomeDestination]: one pill above
 * the Dock that shows the page dots, opens search on a tap and still scrubs between pages; search rising from the Dock
 * with its field on the Dock's edge and the best match nearest it, and closing back to Home.
 *
 * The shell is a stand-in that answers the navigation actions the way the real one does and records every action.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class HomeHandleBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(ScreenshotFixtures.homeState().withHomePages(3))

    private fun show(
        state: LauncherShellState = shell,
        answers: Boolean = true,
    ) {
        shell = state
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    onAction = { action ->
                        actions += action
                        if (answers) {
                            when (action) {
                                LauncherShellAction.OpenSearch -> shell = shell.onDestination(ShellDestination.SEARCH)
                                LauncherShellAction.OpenHome -> shell = shell.onDestination(ShellDestination.HOME)
                                is LauncherShellAction.SelectHomePage -> shell = shell.onPage(action.pageId)
                                else -> Unit
                            }
                        }
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun LauncherShellState.onPage(id: LauncherPageId): LauncherShellState {
        val layout = homeLayout.copy(selectedPageId = id)
        val layouts = mapOf(homeLayoutSet.activeKey to layout)
        return copy(homeLayout = layout, homeLayoutSet = homeLayoutSet.copy(layouts = layouts))
    }

    private fun LauncherShellState.onDestination(destination: ShellDestination) = copy(destination = destination)

    private fun customActionLabels(description: String): List<String> =
        composeRule.onNodeWithContentDescription(description).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].map { it.label }

    private fun invokeDockAction(label: String) {
        val action =
            composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].first { it.label == label }
        composeRule.runOnUiThread { action.action() }
        composeRule.waitForIdle()
    }

    private fun searchProgress(): Float =
        composeRule.onNodeWithTag(DOCK_SEARCH_SURFACE_TEST_TAG).fetchSemanticsNode().config[DockSearchProgressKey]

    // The handle

    @Test
    fun theHandleShowsThePageDotsAsOnePillWithTheSearchGlyphAndNoSeparateSearchPill() {
        show()
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page selector").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 1 of 3"))
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        // The old pill was a text button reading "Search".
        composeRule.onAllNodesWithText("Search").assertCountEquals(0)
    }

    @Test
    fun theHandleIsAtLeast48DpToTouchWhateverItDraws() {
        show()
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Search").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Page selector").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aSinglePageHasNoDotsButStillSearches() {
        show(ScreenshotFixtures.homeState())
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Page selector").assertCountEquals(0)
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performClick()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    @Test
    fun aTapOnTheHandleOpensSearch() {
        show(answers = false)
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performClick()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    @Test
    fun aTapOnTheDotsOpensSearchToo() {
        show(answers = false)
        composeRule.onNodeWithContentDescription("Page selector").performClick()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    @Test
    fun theSearchButtonIsAccessibleAndOpensSearch() {
        show(answers = false)
        val node = composeRule.onNodeWithContentDescription("Search").fetchSemanticsNode()
        assertTrue(SemanticsActions.OnClick in node.config)
        assertEquals(androidx.compose.ui.semantics.Role.Button, node.config[SemanticsProperties.Role])
        composeRule.runOnUiThread { node.config[SemanticsActions.OnClick].action?.invoke() }
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    @Test
    fun theDockOffersSearchAsAnAccessibilityAction() {
        show(answers = false)
        invokeDockAction("Search")
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    // Quick-drag

    @Test
    fun draggingAlongTheDotsScrubsToTheLastPageAndCommitsItOnRelease() {
        show(answers = false)
        composeRule.onNodeWithContentDescription("Page selector").performTouchInput {
            swipe(start = Offset(1f, height / 2f), end = Offset(width - 1f, height / 2f))
        }
        composeRule.waitForIdle()

        assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("page-3")), actions.last())
        assertTrue("a drag along the dots must not open search: $actions", LauncherShellAction.OpenSearch !in actions)
        composeRule.onNodeWithContentDescription("Page selector")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 3 of 3"))
    }

    @Test
    fun theHandleHasPreviousAndNextPageActionsAsTheDragsNonGestureRoute() {
        show(answers = false)
        // On the first page there is nothing before it.
        assertEquals(listOf("Next page"), customActionLabels("Page selector"))
        val next =
            composeRule.onNodeWithContentDescription("Page selector").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].single()
        composeRule.runOnUiThread { next.action() }
        composeRule.waitForIdle()

        assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("page-2")), actions.last())
    }

    @Test
    fun thePageSelectorCanBeSetToAPage() {
        show()
        val setProgress =
            composeRule.onNodeWithContentDescription("Page selector").fetchSemanticsNode()
                .config[SemanticsActions.SetProgress].action!!
        composeRule.runOnUiThread { setProgress(1f) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Page selector")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 2 of 3"))
        assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("page-2")), actions.last())
    }

    // The Dock still works with the handle beside it

    @Test
    fun pressingTheDockDownStillOpensTheStreamWithTheHandleThere() {
        show(answers = false)
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).assertIsDisplayed()
        composeRule.pressDock(distancePx = 150f)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    @Test
    fun theHandleSitsAboveTheDockAndDoesNotOverlapIt() {
        show()
        val handle = composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("handle $handle must end above the dock $dock", handle.bottom <= dock.top + 1f)
    }

    // Search from the Dock

    @Test
    fun theHandleTapRisesSearchFromTheDockAndTheFieldTakesFocus() {
        show()
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DOCK_SEARCH_SURFACE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).assertIsFocused()
        // It is its own surface, not the Library's sheet, and the Dock stays drawn under it.
        composeRule.onAllNodesWithTag(LIBRARY_SHEET_TEST_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).assertIsDisplayed()
        assertEquals(1f, searchProgress(), 1e-3f)
    }

    @Test
    fun theFieldSitsOnTheDocksEdgeWithinThumbReach() {
        show(ScreenshotFixtures.homeState().searching(query = ""))
        val field = composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val density = composeRule.density.density
        assertTrue("field $field must end above the dock $dock", field.bottom <= dock.top)
        assertTrue("field $field is more than 40 dp above the dock $dock", dock.top - field.bottom <= 40 * density)
    }

    @Test
    fun theBestMatchIsNearestTheFieldAndFurtherOnesStackAbove() {
        show(ScreenshotFixtures.homeState().searching())
        val first = composeRule.onNodeWithText("Calendar").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithText("Camera").fetchSemanticsNode().boundsInRoot
        val field = composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the first hit $first should be below the second $second", first.top > second.top)
        assertTrue("the first hit $first should be above the field $field", first.bottom <= field.top)
    }

    @Test
    fun launchingAResultGoesThroughTheShellAsItDoesInTheLibrary() {
        show(ScreenshotFixtures.homeState().searching(), answers = false)
        composeRule.onNodeWithText("Calendar").performClick()
        assertEquals(LauncherShellAction.LaunchApp(ScreenshotFixtures.calendar.identity), actions.first())
        assertEquals(LauncherShellAction.OpenHome, actions.last())
    }

    @Test
    fun closeSearchRunsTheOpeningBackAndThenReturnsHome() {
        composeRule.mainClock.autoAdvance = false
        show(ScreenshotFixtures.homeState().searching(query = ""), answers = false)
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(1f, searchProgress(), 1e-3f)

        val close =
            composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).fetchSemanticsNode().parent!!
                .config[SemanticsActions.CustomActions].single { it.label == "Close search" }
        composeRule.runOnUiThread { close.action() }
        composeRule.mainClock.advanceTimeBy(80)
        // Part-way back: still there, not yet reported to the shell.
        val partway = searchProgress()
        assertTrue("progress $partway should be between open and closed", partway in 0.05f..0.95f)
        assertTrue("the shell was told too early: $actions", actions.isEmpty())

        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun aDragDownOrUpFromTheHandleNeverFiresAHomeSwipe() {
        show()
        // Swipe down (one finger) is search on Home; the handle is search's own button, so a drag that starts on it
        // must not be mistaken for that swipe, nor for any other.
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performTouchInput {
            swipe(Offset(width * 0.1f, height / 2f), Offset(width * 0.1f, height / 2f + 260f), durationMillis = 200)
        }
        composeRule.waitForIdle()
        assertTrue("a swipe down from the handle reached Home: $actions", actions.isEmpty())
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performTouchInput {
            swipe(Offset(width * 0.1f, height / 2f), Offset(width * 0.1f, height / 2f - 260f), durationMillis = 200)
        }
        composeRule.waitForIdle()
        assertTrue("a swipe up from the handle reached Home: $actions", actions.isEmpty())
        composeRule.onAllNodesWithTag(DOCK_SEARCH_SURFACE_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun aDragFromTheHandleOfASinglePageNeverFiresAHomeSwipeEither() {
        show(ScreenshotFixtures.homeState())
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performTouchInput {
            swipe(Offset(width * 0.5f, height / 2f), Offset(width * 0.5f, height / 2f + 260f), durationMillis = 200)
        }
        composeRule.waitForIdle()
        assertTrue("a swipe from the handle reached Home: $actions", actions.isEmpty())
    }

    @Test
    fun aTapOnTheRoomAroundSearchClosesItBackToHome() {
        show()
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DOCK_SEARCH_SURFACE_TEST_TAG).performTouchInput { click(Offset(width / 2f, 40f)) }
        composeRule.waitForIdle()

        assertEquals(LauncherShellAction.OpenHome, actions.last())
        composeRule.onAllNodesWithTag(DOCK_SEARCH_SURFACE_TEST_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun systemBackWithAnEmptyFieldClosesSearchAndWithTextClearsTheTextFirst() {
        show(ScreenshotFixtures.homeState().searching(), answers = false)
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.SearchQueryChanged("")), actions)

        actions.clear()
        shell = ScreenshotFixtures.homeState().searching(query = "")
        composeRule.waitForIdle()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun searchOpensOverTheStreamAndClosesBackToIt() {
        show(ScreenshotFixtures.homeState().searching(query = "").copy(libraryOverStream = true), answers = false)
        invokeCloseSearch()
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    private fun invokeCloseSearch() {
        val close =
            composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).fetchSemanticsNode().parent!!
                .config[SemanticsActions.CustomActions].single { it.label == "Close search" }
        composeRule.runOnUiThread { close.action() }
    }

    @Test
    fun theSwipeDownShortcutStillOpensSearchWithTheFieldFocused() {
        // The shell's search destination, which swipe down on Home and OPEN_SEARCH reach: now the Dock's search.
        show(ScreenshotFixtures.homeState().copy(destination = ShellDestination.SEARCH))
        composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).assertIsFocused()
    }

    // Motion

    @Test
    fun searchRisesOnTheDocksSpringAndNeverOvershoots() {
        composeRule.mainClock.autoAdvance = false
        show(ScreenshotFixtures.homeState().searching(query = ""), answers = false)
        val seen = mutableListOf<Float>()
        repeat(40) {
            seen += searchProgress()
            composeRule.mainClock.advanceTimeBy(16)
        }
        assertTrue("it starts closed: ${seen.first()}", seen.first() < 0.1f)
        assertTrue("it opens: ${seen.last()}", seen.last() > 0.95f)
        assertTrue("it never overshoots: $seen", seen.all { it in 0f..1f })
        assertTrue("it only rises: $seen", seen.zipWithNext().all { (a, b) -> b >= a - 1e-4f })
    }

    @Test
    fun underReducedMotionSearchIsAnEightyMillisecondFade() {
        composeRule.mainClock.autoAdvance = false
        show(ScreenshotFixtures.homeState().withReducedMotion().searching(query = ""), answers = false)
        composeRule.mainClock.advanceTimeBy(16)
        val early = searchProgress()
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals(1f, searchProgress(), 1e-3f)
        assertTrue("the fade is under way after a frame: $early", early < 0.6f)
    }
}
