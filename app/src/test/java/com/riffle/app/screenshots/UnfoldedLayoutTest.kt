package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.riffle.app.launcher.DOCK_CONTEXTUAL_ROW_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HOME_HANDLE_STRIP_TEST_TAG
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HOME_WORKSPACE_GRID_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.library.LIBRARY_PAGED_TEST_TAG
import com.riffle.app.launcher.library.LIBRARY_PAGE_TEST_TAG_PREFIX
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.settings.HomeMode
import com.riffle.core.domain.launcher.settings.LayoutSettings
import com.riffle.core.domain.launcher.settings.PageIndicatorPlacement
import com.riffle.core.domain.launcher.settings.PageScrollDirection
import com.riffle.core.domain.launcher.settings.PostureLayoutSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Layout per posture (ADR 0003, Amendment 2, part U1) through the real [HomeDestination]: where the Dock and the Home
 * handle stand for each Dock edge, folded and unfolded (the handle just in from the Dock, in front of its middle,
 * running the way the pages scroll); the pages scrolling up and down with the platform's vertical pager, by a swipe
 * and by the handle; quick scroll off making the dots an indicator only while a tap still searches; the indicator
 * off; the Dock's press and pull on a right-edge Dock; the paged Library and the App library Home following the same
 * settings; and the screenshots of Home with the right-edge Dock, dark and light, and of the Layout page.
 *
 * The shell is a stand-in that answers the page selection and records every action.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class UnfoldedLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(unfolded())

    private fun unfolded(
        dock: DockPosition? = null,
        layout: LayoutSettings = LayoutSettings(),
        pages: Int = 3,
    ): LauncherShellState =
        ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE, dock)
            .withHomePages(pages)
            .withLayoutSettings(layout)

    private fun folded(
        dock: DockPosition? = null,
        layout: LayoutSettings = LayoutSettings(),
        pages: Int = 3,
    ): LauncherShellState =
        ScreenshotFixtures.homeState(HomeLayoutDeviceClass.PHONE, dock)
            .withHomePages(pages)
            .withLayoutSettings(layout)

    private fun show(
        state: LauncherShellState = shell,
        stream: StreamHost = emptyStreamHost(),
    ) {
        shell = state
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = stream,
                    adaptiveStageWindowLayout =
                        screenshotWindowLayout(
                            if (shell.homeLayoutSet.activeKey.deviceClass == HomeLayoutDeviceClass.PHONE) {
                                AdaptiveStagePosture.COMPACT
                            } else {
                                AdaptiveStagePosture.UNFOLDED
                            },
                        ),
                    onAction = { action ->
                        actions += action
                        when (action) {
                            is LauncherShellAction.SelectHomePage -> shell = shell.onPage(action.pageId)
                            LauncherShellAction.OpenSearch -> shell = shell.copy(destination = ShellDestination.SEARCH)
                            else -> shell = shell.answering(action)
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

    private fun isPageSelection(action: LauncherShellAction) = action is LauncherShellAction.SelectHomePage

    private fun firstPage() = composeRule.onAllNodesWithTag(HOME_WORKSPACE_GRID_TEST_TAG).onFirst()

    private fun SemanticsNodeInteraction.swipeUp() =
        performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.85f),
                end = Offset(center.x, height * 0.15f),
                durationMillis = SWIPE_MILLIS,
            )
        }

    private fun SemanticsNodeInteraction.swipeLeft() =
        performTouchInput {
            swipe(
                start = Offset(width * 0.85f, center.y),
                end = Offset(width * 0.15f, center.y),
                durationMillis = SWIPE_MILLIS,
            )
        }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private val dp get() = composeRule.density.density

    private fun selectorState(): String =
        composeRule.onNodeWithContentDescription("Page selector").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]

    private fun assertCentredOnTheDocksMiddle(
        dock: Rect,
        handle: Rect,
        vertical: Boolean,
    ) {
        val offset = if (vertical) abs(handle.center.y - dock.center.y) else abs(handle.center.x - dock.center.x)
        assertTrue("the handle is ${offset / dp} dp off the middle of the Dock", offset < 2f * dp)
    }

    // Dock and handle for each edge

    @Test
    fun anUnfoldedHomeHasARightDockCentredOnItsEdgeAndAVerticalHandleInFrontOfItsMiddle() {
        show()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val dock = bounds(HOME_DOCK_PULL_TEST_TAG)
        val handle = bounds(HOME_HANDLE_TEST_TAG)

        assertTrue("the Dock is on the right edge", dock.right > root.right - 24f * dp)
        assertTrue("the Dock is centred on its edge", abs(dock.center.y - root.center.y) < 2f * dp)
        assertTrue("the handle is in front of the Dock, on Home's side", handle.right <= dock.left + 1f)
        assertTrue("and just in from it", dock.left - handle.right < 20f * dp)
        assertCentredOnTheDocksMiddle(dock, handle, vertical = true)
        assertTrue("a column for pages that scroll up and down", handle.height > handle.width)
        val dots = composeRule.onNodeWithContentDescription("Page selector").fetchSemanticsNode().boundsInRoot
        // Three dots of 6 dp with 6 dp between them, stacked, centred on the handle.
        assertEquals("the dots are stacked", 30f * dp, dots.height, 1f * dp)
        assertEquals(handle.center.x, dots.center.x, 1f)
    }

    @Test
    fun aLeftDockHasTheHandleOnItsRightInFrontOfItsMiddle() {
        show(unfolded(dock = DockPosition.LEFT))
        val dock = bounds(HOME_DOCK_PULL_TEST_TAG)
        val handle = bounds(HOME_HANDLE_TEST_TAG)

        assertTrue("the Dock is on the left edge", dock.left < 24f * dp)
        assertTrue("the handle is in front of the Dock, on Home's side", handle.left >= dock.right - 1f)
        assertTrue("and just in from it", handle.left - dock.right < 20f * dp)
        assertCentredOnTheDocksMiddle(dock, handle, vertical = true)
    }

    @Test
    fun aBottomDockWithPagesThatScrollUpAndDownHasAColumnAboveItsMiddle() {
        show(unfolded(dock = DockPosition.BOTTOM))
        val dock = bounds(HOME_DOCK_PULL_TEST_TAG)
        val handle = bounds(HOME_HANDLE_TEST_TAG)

        assertTrue("the handle stands above the Dock", handle.bottom <= dock.top + 1f)
        assertCentredOnTheDocksMiddle(dock, handle, vertical = false)
        assertTrue("it still runs the way the pages scroll", handle.height > handle.width)
    }

    @Test
    fun anUnfoldedRightDockWithSidewaysPagesKeepsTheRowBelowThePages() {
        // A row runs across a side Dock's edge, so it stays in the row below the pages instead of a strip as wide as
        // the row, which would squeeze the pages.
        val sideways = LayoutSettings(unfolded = PostureLayoutSettings(pageScroll = PageScrollDirection.HORIZONTAL))
        show(unfolded(layout = sideways))
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val dock = bounds(HOME_DOCK_PULL_TEST_TAG)
        val handle = bounds(HOME_HANDLE_TEST_TAG)

        assertTrue("a row for pages that scroll sideways", handle.width > handle.height)
        assertTrue("the handle is below the pages, at the bottom", handle.bottom > root.bottom - 60f * dp)
        assertTrue("and beside the Dock, not under it", handle.right <= dock.left + 1f)
        val pagesCentre = firstPage().fetchSemanticsNode().boundsInRoot.center.x
        assertEquals("centred in the pages' column", pagesCentre, handle.center.x, 2f * dp)
        composeRule.onNodeWithTag(HOME_HANDLE_STRIP_TEST_TAG).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT_PHONE)
    fun foldedHasABottomDockAndAHorizontalHandleAboveItsMiddle() {
        show(folded())
        val dock = bounds(HOME_DOCK_PULL_TEST_TAG)
        val handle = bounds(HOME_HANDLE_TEST_TAG)

        assertTrue("the handle stands above the Dock", handle.bottom <= dock.top + 1f)
        assertTrue("and just above it", dock.top - handle.bottom < 20f * dp)
        assertCentredOnTheDocksMiddle(dock, handle, vertical = false)
        assertTrue("a row for pages that scroll sideways", handle.width > handle.height)
        composeRule.onNodeWithTag(HOME_HANDLE_STRIP_TEST_TAG).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT_PHONE)
    fun foldedWithARightDockKeepsItsSidewaysPagesAndTheRowBelowThem() {
        show(folded(dock = DockPosition.RIGHT))
        val dock = bounds(HOME_DOCK_PULL_TEST_TAG)
        val handle = bounds(HOME_HANDLE_TEST_TAG)

        assertTrue("folded keeps its sideways pages, so the handle is a row", handle.width > handle.height)
        assertTrue("beside the Dock, not under it", handle.right <= dock.left + 1f)
        composeRule.onNodeWithTag(HOME_HANDLE_STRIP_TEST_TAG).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT_PHONE)
    fun foldedWithAColumnAndARightDockStandsInFrontOfTheDocksMiddle() {
        val foldedVertical = PostureLayoutSettings.FOLDED_DEFAULT.copy(pageScroll = PageScrollDirection.VERTICAL)
        show(folded(dock = DockPosition.RIGHT, layout = LayoutSettings(folded = foldedVertical)))
        val dock = bounds(HOME_DOCK_PULL_TEST_TAG)
        val handle = bounds(HOME_HANDLE_TEST_TAG)

        assertTrue(handle.right <= dock.left + 1f)
        assertCentredOnTheDocksMiddle(dock, handle, vertical = true)
        assertTrue(handle.height > handle.width)
        composeRule.onNodeWithTag(HOME_HANDLE_STRIP_TEST_TAG).assertExists()
    }

    @Test
    fun theHandleMakesRoomSoItNeverCoversAnIcon() {
        show()
        val handle = bounds(HOME_HANDLE_TEST_TAG)
        val grid = firstPage().fetchSemanticsNode().boundsInRoot
        assertTrue("the grid ends before the handle (${grid.right} vs ${handle.left})", grid.right <= handle.left + 1f)
    }

    // The pages scroll the way the setting says

    @Test
    fun verticalPagesChangeByAVerticalSwipe() {
        show()
        assertEquals("Page 1 of 3", selectorState())
        firstPage().swipeUp()
        composeRule.waitForIdle()

        assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("page-2")), actions.last())
        assertEquals("Page 2 of 3", selectorState())
    }

    @Test
    fun aSidewaysSwipeDoesNotChangeVerticalPages() {
        show()
        firstPage().swipeLeft()
        composeRule.waitForIdle()

        assertTrue("no page was selected: $actions", actions.none(::isPageSelection))
        assertEquals("Page 1 of 3", selectorState())
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT_PHONE)
    fun foldedPagesStillChangeBySidewaysSwipeAndNotByAVerticalOne() {
        show(folded())
        firstPage().swipeUp()
        composeRule.waitForIdle()
        assertTrue("a vertical swipe moved no page: $actions", actions.none(::isPageSelection))

        firstPage().swipeLeft()
        composeRule.waitForIdle()
        assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("page-2")), actions.last())
    }

    @Test
    fun draggingDownTheDotsScrubsToTheLastPageAndCommitsItOnRelease() {
        show()
        composeRule.onNodeWithContentDescription("Page selector").performTouchInput {
            swipe(start = Offset(center.x, 1f), end = Offset(center.x, height - 1f))
        }
        composeRule.waitForIdle()

        assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("page-3")), actions.last())
        assertFalse("a drag along the dots must not open search: $actions", LauncherShellAction.OpenSearch in actions)
        assertEquals("Page 3 of 3", selectorState())
    }

    @Test
    fun draggingUpTheDotsGoesBackToTheFirstPage() {
        show(unfolded().let { state -> state.onPage(LauncherPageId("page-3")) })
        assertEquals("Page 3 of 3", selectorState())
        composeRule.onNodeWithContentDescription("Page selector").performTouchInput {
            swipe(start = Offset(center.x, height - 1f), end = Offset(center.x, 1f))
        }
        composeRule.waitForIdle()

        assertEquals(LauncherShellAction.SelectHomePage(LauncherPageId("home")), actions.last())
    }

    @Test
    fun theHandleKeepsItsAccessibilityRoutesOnAVerticalHandle() {
        show()
        val config = composeRule.onNodeWithContentDescription("Page selector").fetchSemanticsNode().config
        assertEquals(listOf("Next page"), config[SemanticsActions.CustomActions].map { it.label })
        assertTrue(SemanticsActions.SetProgress in config)
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performClick()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    // Quick scroll and the indicator

    @Test
    fun withQuickScrollOffTheDotsAreAnIndicatorOnlyAndATapStillSearches() {
        val still = LayoutSettings(unfolded = PostureLayoutSettings.UNFOLDED_DEFAULT.copy(quickScroll = false))
        show(unfolded(layout = still))

        composeRule.onNodeWithContentDescription("Page selector").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page selector").performTouchInput {
            swipe(start = Offset(center.x, 1f), end = Offset(center.x, height - 1f))
        }
        composeRule.waitForIdle()
        assertTrue("the drag scrolled nothing: $actions", actions.none(::isPageSelection))
        assertEquals("Page 1 of 3", selectorState())

        val config = composeRule.onNodeWithContentDescription("Page selector").fetchSemanticsNode().config
        assertFalse("nothing sets a page through an indicator", SemanticsActions.SetProgress in config)
        assertFalse(SemanticsActions.CustomActions in config)

        composeRule.onNodeWithContentDescription("Page selector").performClick()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    @Test
    fun theIndicatorShowsWhereYouAreWhilePagesAreSwiped() {
        val still = LayoutSettings(unfolded = PostureLayoutSettings.UNFOLDED_DEFAULT.copy(quickScroll = false))
        show(unfolded(layout = still))
        firstPage().swipeUp()
        composeRule.waitForIdle()
        assertEquals("Page 2 of 3", selectorState())
    }

    @Test
    fun withTheIndicatorOffThereAreNoDotsButSearchStays() {
        val unfoldedOff = PostureLayoutSettings.UNFOLDED_DEFAULT.copy(indicator = PageIndicatorPlacement.OFF)
        show(unfolded(layout = LayoutSettings(unfolded = unfoldedOff)))

        composeRule.onAllNodesWithContentDescription("Page selector").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        val handle = bounds(HOME_HANDLE_TEST_TAG)
        assertEquals("just the glyph's square", handle.width, handle.height, 1f)
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performClick()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    // The Dock's gestures on a right-edge Dock, unfolded

    @Test
    fun pressingARightDockOpensTheStream() {
        show()
        composeRule.pressDock(distancePx = PRESS_PX, edge = DockPosition.RIGHT)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    @Test
    fun pullingARightDockAwayFromItsEdgeOpensTheLibrary() {
        show()
        val width = composeRule.onRoot().fetchSemanticsNode().size.width
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(PULL_STEPS) { moveBy(Offset(-width * 0.6f / PULL_STEPS, 0f)) }
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppDrawer), actions)
    }

    // Hold to choose is the folded phone's (an expanded posture's press stays the split's settle, as it was), so it is
    // checked on a folded phone with its Dock on the right edge.
    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT_PHONE)
    fun holdingAFoldedRightDockPressedChoosesInsteadOfToggling() {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "unfolded-choose").apply { isDaemon = true } }
        try {
            show(folded(dock = DockPosition.RIGHT), stream = realWiringStreamHost(executor = executor))
            composeRule.pressDock(distancePx = PRESS_PX, edge = DockPosition.RIGHT, release = false, settle = false)
            composeRule.mainClock.advanceTimeBy(HOLD_MILLIS)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag("Today")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).assertExists()
            assertTrue("nothing was toggled while the finger is down: $actions", actions.isEmpty())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun theHandleStillOpensSearchWithARightDock() {
        show()
        composeRule.onNodeWithContentDescription("Search").performClick()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenSearch), actions)
    }

    // The paged Library and the App library Home follow the same settings

    private fun appLibraryHome(state: LauncherShellState): LauncherShellState =
        state.withManyApps().withHomeMode(HomeMode.AppLibrary)

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT_PHONE)
    fun theAppLibraryHomeFollowsTheFoldedSettingsToo() {
        val foldedVertical = PostureLayoutSettings.FOLDED_DEFAULT.copy(pageScroll = PageScrollDirection.VERTICAL)
        val vertical = LayoutSettings(folded = foldedVertical)
        show(appLibraryHome(folded(dock = DockPosition.RIGHT, layout = vertical)))

        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertIsDisplayed()
        val dock = bounds(HOME_DOCK_PULL_TEST_TAG)
        val handle = bounds(HOME_HANDLE_TEST_TAG)
        assertTrue("the handle stands in front of the Dock", handle.right <= dock.left + 1f)
        assertCentredOnTheDocksMiddle(dock, handle, vertical = true)
        assertTrue("a column", handle.height > handle.width)
        composeRule.onNodeWithContentDescription("Page selector")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 1 of 2"))

        composeRule.onNodeWithTag("${LIBRARY_PAGE_TEST_TAG_PREFIX}0").performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.85f),
                end = Offset(center.x, height * 0.15f),
                durationMillis = SWIPE_MILLIS,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Zoo").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Page selector").performTouchInput {
            swipe(start = Offset(center.x, height - 1f), end = Offset(center.x, 1f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Alarm").assertIsDisplayed()
    }

    @Test
    fun theUnfoldedAppLibraryHomeScrollsUpAndDownWithItsHandleBesideTheDock() {
        show(appLibraryHome(unfolded()))

        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertIsDisplayed()
        val dock = bounds(HOME_DOCK_PULL_TEST_TAG)
        composeRule.onAllNodesWithTag(HOME_HANDLE_TEST_TAG).onFirst().fetchSemanticsNode().boundsInRoot.let { handle ->
            assertTrue(handle.right <= dock.left + 1f)
            assertTrue(handle.height >= handle.width)
        }
    }

    // Screenshots

    @Test
    fun unfoldedHomeWithTheRightDockAndHandleLight() = shoot(unfolded())

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedHomeWithTheRightDockAndHandleDark() = shoot(unfolded())

    @Test
    fun unfoldedHomeWithTheLeftDockAndHandleLight() = shoot(unfolded(dock = DockPosition.LEFT))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedAppLibraryHomeWithTheRightDockDark() = shoot(appLibraryHome(unfolded()))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedHomeWithQuickScrollOffDark() =
        shoot(
            unfolded(
                layout = LayoutSettings(unfolded = PostureLayoutSettings.UNFOLDED_DEFAULT.copy(quickScroll = false)),
            ),
        )

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT_PHONE)
    fun foldedHomeWithTheBottomDockAndHandleLight() = shoot(folded())

    // The Layout settings page

    @Test
    fun theLayoutPageLight() = composeRule.renderSettingsPage(SettingsPage.POSTURE_LAYOUT)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theLayoutPageDark() = composeRule.renderSettingsPage(SettingsPage.POSTURE_LAYOUT)

    private fun shoot(state: LauncherShellState) {
        show(state)
        composeRule.captureScreen()
    }

    private companion object {
        const val PRESS_PX = 150f
        const val PULL_STEPS = 12
        const val HOLD_MILLIS = 600L
        const val SWIPE_MILLIS = 200L
    }
}
