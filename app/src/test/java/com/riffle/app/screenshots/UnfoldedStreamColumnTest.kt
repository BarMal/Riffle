package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.riffle.app.launcher.DOCK_CARD_ACTIONS_TEST_TAG
import com.riffle.app.launcher.DrawnSurfaceKey
import com.riffle.app.launcher.GridFadeAlphaKey
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HOME_SURFACE_TEST_TAG
import com.riffle.app.launcher.HOME_WORKSPACE_GRID_TEST_TAG
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.STREAM_COLUMN_TEST_TAG
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_SURFACE_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import com.riffle.core.domain.launcher.settings.LayoutSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.stream.StreamColumn
import com.riffle.core.domain.launcher.stream.StreamGridFade
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import org.junit.After
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
 * The Stream on an unfolded posture (ADR 0003, Amendment 2, dock modes U2), through the real shell content with only
 * the platform services faked: pressing the Dock opens the same upright stack as folded, in a column attached to the
 * Dock's inner side, over a Home that stays and whose grid fades beneath it; apps peek through or not (a setting); a
 * card grows the column toward the middle with its actions on the card; every way of closing works; a left Dock mirrors
 * it and a bottom Dock carries it above itself; search is the same column's width, attached to the Dock the same way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class UnfoldedStreamColumnTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "unfolded-column").apply { isDaemon = true } }
    private val sent = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(unfolded())

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private val chatPackage = ScreenshotFixtures.chat.identity.packageName.value
    private val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")
    private val dp get() = composeRule.density.density

    private fun chatNotification(index: Int = 1) =
        LauncherNotification(
            key = LauncherNotificationKey("0|chat|$index"),
            packageName = AppPackageName(chatPackage),
            canDismiss = true,
            title = "Sam",
            text = "Are we still on for lunch tomorrow?",
            messages =
                listOf(
                    LauncherNotificationMessage("Sam", "Morning! Got a minute?", StreamFixtures.NOW - 20 * MINUTE),
                    LauncherNotificationMessage(
                        "Sam",
                        "Are we still on for lunch tomorrow?",
                        StreamFixtures.NOW - 4 * MINUTE,
                    ),
                ),
            postedAtEpochMillis = StreamFixtures.NOW - 4 * MINUTE,
        )

    private fun host(): StreamHost =
        realWiringStreamHost(
            executor = executor,
            activeNotifications = listOf(chatNotification()),
            installedApps = ScreenshotFixtures.installedApps,
            canReply = { true },
            canSnooze = { true },
        ).also { it.pages.remember(HomeLayoutDeviceClass.FOLDABLE, chatPage) }

    private fun unfolded(
        dock: DockPosition? = null,
        destination: ShellDestination = ShellDestination.HOME,
        peekThrough: Boolean = true,
        reducedMotion: Boolean = true,
    ): LauncherShellState {
        val base =
            ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE, dock)
                .withHomePages(3)
                .withManyApps()
                .withAFullFirstPage()
                .copy(destination = destination)
        val unfolded = LayoutSettings().unfolded.copy(appPeekThrough = peekThrough)
        val settings = base.launcherSettings
        return base.copy(
            launcherSettings =
                settings.copy(
                    layout = settings.layout.copy(unfolded = unfolded),
                    motion =
                        settings.motion.copy(
                            reducedMotionPreference =
                                if (reducedMotion) ReducedMotionPreference.ON else ReducedMotionPreference.OFF,
                        ),
                ),
        )
    }

    /** Every cell of the first page holds an app, so the grid reaches under the column wherever it stands. */
    private fun LauncherShellState.withAFullFirstPage(): LauncherShellState {
        val first = homeLayout.pages.first()
        val columns = first.grid.columns
        val items =
            installedApps.take(columns * first.grid.rows).mapIndexed { index, app ->
                ScreenshotFixtures.shortcut(
                    app,
                    GridPlacement(cell = GridCell(column = index % columns, row = index / columns)),
                )
            }
        val layout =
            homeLayout.copy(
                pages = homeLayout.pages.map { if (it.id == first.id) it.copy(items = items) else it },
            )
        return copy(
            homeLayout = layout,
            homeLayoutSet = homeLayoutSet.copy(layouts = mapOf(homeLayoutSet.activeKey to layout)),
        )
    }

    private fun show(state: LauncherShellState = shell) {
        shell = state
        val stream = host()
        composeRule.setContent {
            ScreenshotBackdrop {
                LauncherShellContent(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = stream,
                    onAction = { action ->
                        sent += action
                        shell =
                            when (action) {
                                LauncherShellAction.OpenStream -> shell.copy(destination = ShellDestination.STREAM)
                                is LauncherShellAction.SelectHomePage -> shell.onPage(action.pageId)
                                LauncherShellAction.OpenSearch -> shell.copy(destination = ShellDestination.SEARCH)
                                else -> shell.answering(action)
                            }
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun LauncherShellState.onPage(id: LauncherPageId): LauncherShellState {
        val layout = homeLayout.copy(selectedPageId = id)
        return copy(
            homeLayout = layout,
            homeLayoutSet = homeLayoutSet.copy(layouts = mapOf(homeLayoutSet.activeKey to layout)),
        )
    }

    private fun showOpen(
        dock: DockPosition? = null,
        peekThrough: Boolean = true,
    ) {
        show(unfolded(dock = dock, destination = ShellDestination.STREAM, peekThrough = peekThrough))
        waitForText("Sam")
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(
            tag,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot

    private fun column(): Rect = bounds(STREAM_COLUMN_TEST_TAG)

    /** The Dock's strip along its edge: its gesture area, a margin wider than the glass the icons stand on. */
    private fun dock(): Rect = bounds(HOME_DOCK_PULL_TEST_TAG)

    private fun root(): Rect = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

    private fun theCardIsOpen() =
        composeRule.onAllNodes(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** Every cell of the grid with what the fade draws it at, as (centre x, centre y, opacity). */
    private fun cells(): List<Triple<Float, Float, Float>> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(GridFadeAlphaKey)).fetchSemanticsNodes().map { node ->
            Triple(node.boundsInRoot.center.x, node.boundsInRoot.center.y, node.config[GridFadeAlphaKey])
        }

    private fun SemanticsNodeInteraction.swipeUp() =
        performTouchInput {
            swipe(Offset(center.x, height * 0.85f), Offset(center.x, height * 0.15f), durationMillis = SWIPE_MILLIS)
        }

    // The press opens the stack attached to the Dock

    @Test
    fun pressingARightDockOpensTheStackInAColumnAttachedToIt() {
        show()
        composeRule.onAllNodesWithTag(STREAM_COLUMN_TEST_TAG).assertCountEquals(0)
        composeRule.pressDock(distancePx = PRESS_PX, edge = DockPosition.RIGHT)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithTag(STREAM_COLUMN_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), sent)
        val column = column()
        val dock = dock()
        val width = root().width
        assertTrue(
            "the column reaches the Dock's strip: ${column.right} vs ${dock.left}",
            column.right - dock.left in 0f..MAX_REACH * dp,
        )
        assertTrue("about a third of the width: ${column.width / dp} dp", column.width in width * 0.3f..width * 0.42f)
        assertEquals(
            "it is the one Stream surface",
            1,
            composeRule.onAllNodesWithTag(STREAM_SURFACE_TEST_TAG).fetchSemanticsNodes().size,
        )
        // Its cards stand inside it, whichever page the press landed on.
        val stack = composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("the Stream is the column's", stack.left >= column.left - 1f && stack.right <= column.right + 1f)
    }

    @Test
    fun theStackKeepsClearOfTheDocksAndTheScreensEdges() {
        showOpen()
        val column = column()
        val root = root()
        assertTrue(column.top >= 0f && column.bottom <= root.bottom)
        assertTrue(column.left >= 0f && column.right <= root.right)
    }

    // One continuous Home under and beside the stack

    @Test
    fun homeStaysAtFullOpacityAndItsGridSpansTheWholeWidthBesideAndUnderTheColumn() {
        showOpen()
        val home = composeRule.onNodeWithTag(HOME_SURFACE_TEST_TAG).fetchSemanticsNode()
        assertEquals("Home does not fade out", 1f, home.config[DrawnSurfaceKey].alpha, 0.001f)
        val grid =
            composeRule.onAllNodesWithTag(
                HOME_WORKSPACE_GRID_TEST_TAG,
            ).onFirst().fetchSemanticsNode().boundsInRoot
        val column = column()
        assertTrue(
            "the grid reaches under the column: ${grid.right} vs ${column.left}",
            grid.right > column.left + 20f * dp,
        )
        assertTrue("and spans from the screen's left side", grid.left < root().width * 0.15f)
    }

    @Test
    fun theGridFadesBeneathTheColumnFromAnUntouchedLeftSide() {
        showOpen()
        composeRule.waitForIdle()
        val column = column()
        val cells = cells()
        assertTrue("the grid has cells", cells.size > 8)
        val clear = cells.filter { it.first < column.left - StreamGridFade.FEATHER_DP * dp - 2f }
        val under =
            cells.filter {
                it.first > column.left && it.first < column.right && it.second > column.top && it.second < column.bottom
            }
        assertTrue("some cells are clear of the column", clear.isNotEmpty())
        assertTrue("some cells are under it", under.isNotEmpty())
        assertTrue("clear cells are untouched", clear.all { it.third == 1f })
        assertTrue(
            "cells under the column peek through at ${StreamGridFade.PEEK_ALPHA}",
            under.all {
                abs(it.third - StreamGridFade.PEEK_ALPHA) < 0.01f
            },
        )
        // Reading across a row, the opacity never rises again toward the Dock: no hard cut anywhere.
        val byRow = cells.groupBy { (it.second / dp / 10f).toInt() }
        byRow.values.forEach { row ->
            val sorted = row.sortedBy { it.first }
            sorted.zipWithNext().forEach {
                    (a, b) ->
                assertTrue("opacity only falls toward the column", b.third <= a.third + 0.001f)
            }
        }
    }

    @Test
    fun withPeekThroughOffTheGridIsHiddenUnderTheColumnAndStillFadesSoftlyAtItsEdge() {
        showOpen(peekThrough = false)
        composeRule.waitForIdle()
        val column = column()
        val cells = cells()
        val under =
            cells.filter {
                it.first > column.left && it.first < column.right && it.second > column.top && it.second < column.bottom
            }
        assertTrue(under.isNotEmpty())
        assertTrue("nothing shows under the column", under.all { it.third == 0f })
        val clear = cells.filter { it.first < column.left - StreamGridFade.FEATHER_DP * dp - 2f }
        assertTrue("and nothing is hidden elsewhere", clear.all { it.third == 1f })
    }

    @Test
    fun theGridFadeComesAndGoesWithTheStream() {
        show()
        composeRule.waitForIdle()
        assertTrue("no fade on plain Home", cells().isEmpty() || cells().all { it.third == 1f })
    }

    @Test
    fun pagingTheGridWithTheStreamOpenStillWorks() {
        showOpen()
        val pageCountBefore = sent.count { it is LauncherShellAction.SelectHomePage }
        composeRule.onAllNodesWithTag(HOME_WORKSPACE_GRID_TEST_TAG).onFirst().swipeUp()
        composeRule.waitForIdle()
        assertTrue(
            "the swipe selected another page: $sent",
            sent.count {
                it is LauncherShellAction.SelectHomePage
            } > pageCountBefore,
        )
        // The Stream is still out beside it.
        composeRule.onNodeWithTag(STREAM_COLUMN_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Sam").assertIsDisplayed()
    }

    @Test
    fun theHandleIsNotDrawnWhileTheColumnStandsWhereItDoesAndComesBack() {
        showOpen()
        composeRule.onAllNodesWithTag(HOME_HANDLE_TEST_TAG).assertCountEquals(0)
        composeRule.pressDock(distancePx = PRESS_PX, edge = DockPosition.RIGHT)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(HOME_HANDLE_TEST_TAG).assertCountEquals(1)
    }

    // Opening a card

    @Test
    fun openingACardGrowsTheColumnTowardTheCentreWithItsActionsOnTheCard() {
        showOpen()
        val rest = column()
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()

        assertTrue("the card opened", theCardIsOpen())
        val open = column()
        assertEquals("the Dock side stays put", rest.right, open.right, 1f)
        assertTrue(
            "wider toward the middle: ${rest.width / dp} -> ${open.width / dp}",
            open.width > rest.width + 40f * dp,
        )
        assertTrue("and taller", open.height > rest.height + 20f * dp)
        assertTrue("about half the screen at most: ${open.width / dp}", open.width <= root().width * 0.55f)
        // A side Dock holds no actions row: they are on the card.
        composeRule.onAllNodesWithTag(DOCK_CARD_ACTIONS_TEST_TAG).assertCountEquals(0)
        composeRule.onNodeWithText("Write a reply").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send reply").assertExists()
        val card = composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the open card is inside the grown column",
            card.left >= open.left - 1f && card.right <= open.right + 1f,
        )
        // Home is still there.
        composeRule.onAllNodesWithTag(HOME_WORKSPACE_GRID_TEST_TAG).onFirst().assertExists()
    }

    // Closing, by press, Back and the accessibility action

    @Test
    fun pressingTheDockClosesTheOpenCardFirstAndThenTheStream() {
        showOpen()
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        assertTrue(theCardIsOpen())

        composeRule.pressDock(distancePx = PRESS_PX, edge = DockPosition.RIGHT)
        assertFalse("the card closed", theCardIsOpen())
        assertTrue("the Stream stayed: $sent", sent.isEmpty())
        composeRule.onNodeWithTag(STREAM_COLUMN_TEST_TAG).assertIsDisplayed()

        composeRule.pressDock(distancePx = PRESS_PX, edge = DockPosition.RIGHT)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(STREAM_COLUMN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun backClosesTheCardThenTheStream() {
        showOpen()
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse(theCardIsOpen())
        assertTrue("Back did not leave the Stream yet: $sent", sent.isEmpty())

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
    }

    @Test
    fun theDocksAccessibilityActionOpensAndClosesTheStreamAndTheCard() {
        show()
        val actions =
            composeRule.onNodeWithTag(
                HOME_DOCK_PULL_TEST_TAG,
            ).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val open = actions.first { it.label == "Show live pages" }
        composeRule.runOnUiThread { open.action() }
        composeRule.waitForIdle()
        waitForText("Sam")
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), sent)
        composeRule.onNodeWithTag(STREAM_COLUMN_TEST_TAG).assertIsDisplayed()

        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        val whileOpen =
            composeRule.onNodeWithTag(
                HOME_DOCK_PULL_TEST_TAG,
            ).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val closeCard = whileOpen.first { it.label == "Close card" }
        composeRule.runOnUiThread { closeCard.action() }
        composeRule.waitForIdle()
        assertFalse(theCardIsOpen())

        val afterwards =
            composeRule.onNodeWithTag(
                HOME_DOCK_PULL_TEST_TAG,
            ).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val back = afterwards.first { it.label == "Back to Home" }
        composeRule.runOnUiThread { back.action() }
        composeRule.waitForIdle()
        assertEquals(LauncherShellAction.OpenHome, sent.last())
    }

    // A left Dock mirrors it, a bottom Dock carries the column above itself

    @Test
    fun aLeftDockMirrorsTheColumn() {
        showOpen(dock = DockPosition.LEFT)
        val column = column()
        val dock = dock()
        assertTrue(
            "the column reaches the left Dock's strip: ${column.left} vs ${dock.right}",
            dock.right - column.left in 0f..MAX_REACH * dp,
        )
        assertTrue("on the left half", column.center.x < root().center.x)
        composeRule.onNodeWithText("Sam").assertIsDisplayed()
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        val open = column()
        assertEquals("the Dock side stays put", column.left, open.left, 1f)
        assertTrue(open.width > column.width + 40f * dp)
    }

    @Test
    fun aBottomDockCarriesTheColumnAboveItselfCentredOnIt() {
        showOpen(dock = DockPosition.BOTTOM)
        val column = column()
        val dock = dock()
        assertTrue(
            "the column stands on the Dock: ${column.bottom} vs ${dock.top}",
            column.bottom - dock.top in 0f..MAX_REACH * dp,
        )
        assertTrue("centred on it", abs(column.center.x - dock.center.x) < 2f * dp)
        assertTrue("about a third of the width", column.width in root().width * 0.3f..root().width * 0.42f)
        composeRule.onNodeWithText("Sam").assertIsDisplayed()
        // The grid fades under it too.
        composeRule.waitForIdle()
        assertTrue(cells().any { abs(it.third - StreamGridFade.PEEK_ALPHA) < 0.01f })
    }

    @Test
    fun theColumnsPageInsetIsTheStreamPagesRealPadding() {
        assertEquals(RiffleSpacing.l.value, StreamColumn.PAGE_INSET_DP, 0f)
    }

    @Test
    fun theColumnsGeometryIsTheDomainsToThePixel() {
        showOpen()
        val width = root().width / dp
        assertEquals(StreamColumn.restWidthDp(width), column().width / dp, 1f)
    }

    // Search is the column's width, attached to the Dock

    @Test
    fun searchStandsInTheColumnsWidthAgainstTheDock() {
        show(unfolded().searching(query = ""))
        composeRule.waitForIdle()
        val panel = bounds(com.riffle.app.launcher.search.DOCK_SEARCH_FIELD_TEST_TAG)
        val dock = dock()
        assertTrue("the field is against the Dock: ${panel.right} vs ${dock.left}", dock.left - panel.right < 24f * dp)
        assertTrue(
            "and no wider than the column plus its padding: ${panel.width / dp}",
            panel.width <= (StreamColumn.restWidthDp(root().width / dp) + 1f) * dp,
        )
    }

    // Screenshots

    @Test
    fun unfoldedStreamOnARightDockWithAppsPeekingThroughLight() = shoot(showing = { showOpen() })

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedStreamOnARightDockWithAppsPeekingThroughDark() = shoot(showing = { showOpen() })

    @Test
    fun unfoldedStreamOnARightDockWithPeekThroughOffLight() = shoot(showing = { showOpen(peekThrough = false) })

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedStreamOnARightDockWithPeekThroughOffDark() = shoot(showing = { showOpen(peekThrough = false) })

    @Test
    fun unfoldedOpenCardLight() = shoot(showing = { openCard() })

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedOpenCardDark() = shoot(showing = { openCard() })

    @Test
    fun unfoldedStreamOnALeftDockLight() = shoot(showing = { showOpen(dock = DockPosition.LEFT) })

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedStreamOnALeftDockDark() = shoot(showing = { showOpen(dock = DockPosition.LEFT) })

    @Test
    fun unfoldedStreamOnABottomDockLight() = shoot(showing = { showOpen(dock = DockPosition.BOTTOM) })

    @Test
    fun unfoldedSearchOnARightDockLight() = shoot(showing = { show(unfolded().searching()) })

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedSearchOnARightDockDark() = shoot(showing = { show(unfolded().searching()) })

    private fun openCard() {
        showOpen()
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
    }

    private fun shoot(showing: () -> Unit) {
        showing()
        composeRule.captureScreen()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
        const val PRESS_PX = 150f
        const val SWIPE_MILLIS = 200L

        /** The Dock's margin (12 dp) and the column's reach past the glass (12 dp), with some room. */
        const val MAX_REACH = 30f
    }
}
