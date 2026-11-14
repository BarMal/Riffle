package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
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
import com.riffle.app.launcher.GRID_CELL_PROBE_KEY
import com.riffle.app.launcher.GridFadeAlphaKey
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG
import com.riffle.app.launcher.HOME_HANDLE_TEST_TAG
import com.riffle.app.launcher.HOME_INPUT_GATE_TEST_TAG
import com.riffle.app.launcher.HOME_SURFACE_TEST_TAG
import com.riffle.app.launcher.HOME_WORKSPACE_GRID_TEST_TAG
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.OPEN_CARD_SCRIM_TEST_TAG
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.app.launcher.STREAM_COLUMN_TEST_TAG
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.search.DOCK_SEARCH_COLUMN_TEST_TAG
import com.riffle.app.launcher.search.DOCK_SEARCH_FIELD_TEST_TAG
import com.riffle.app.launcher.search.DOCK_SEARCH_SURFACE_TEST_TAG
import com.riffle.app.launcher.search.DockSearchAlphaKey
import com.riffle.app.launcher.search.DockSearchProgressKey
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
@Suppress("LargeClass")
class UnfoldedStreamColumnTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "unfolded-column").apply { isDaemon = true } }
    private val sent = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(unfolded())
    private val probe = RecompositionProbe()

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
            CompositionLocalProvider(LocalRecompositionProbe provides probe) {
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
        // Its cards stand inside the column's rectangle, which is on the Dock's side of the room an open card grows in.
        val surface = composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals("the room an open card grows in ends where the column does", column.right, surface.right, 1f)
        assertTrue("and holds the column", surface.left <= column.left + 1f)
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
        val under = cells.filter { underTheColumn(it, column) }
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

    /** A cell whose middle is deeper inside the column than the fade goes on for: it is at the floor. */
    private fun underTheColumn(
        cell: Triple<Float, Float, Float>,
        column: Rect,
    ): Boolean {
        val margin = StreamGridFade.INNER_DP * dp
        return cell.first > column.left + margin && cell.first < column.right - margin &&
            cell.second > column.top + margin && cell.second < column.bottom - margin
    }

    @Test
    fun withPeekThroughOffTheGridIsHiddenUnderTheColumnAndStillFadesSoftlyAtItsEdge() {
        showOpen(peekThrough = false)
        composeRule.waitForIdle()
        val column = column()
        val cells = cells()
        val under = cells.filter { underTheColumn(it, column) }
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
    fun aDragAcrossOrAlongTheColumnHandleNeverFiresAHomeSwipe() {
        show()
        // Beside the right Dock the handle is a column; a swipe left (one finger: next page, folded's way) or down
        // (search) that starts on it must stay the handle's.
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performTouchInput {
            swipe(
                Offset(width / 2f, height * 0.1f),
                Offset(width / 2f - 200f, height * 0.1f),
                durationMillis = SWIPE_MILLIS,
            )
        }
        composeRule.waitForIdle()
        assertTrue("a swipe across the handle reached Home: $sent", sent.isEmpty())
        composeRule.onNodeWithTag(HOME_HANDLE_TEST_TAG).performTouchInput {
            swipe(
                Offset(width / 2f, height * 0.1f),
                Offset(width / 2f, height * 0.1f + 300f),
                durationMillis = SWIPE_MILLIS,
            )
        }
        composeRule.waitForIdle()
        assertTrue("a swipe down the handle reached Home: $sent", sent.none { it == LauncherShellAction.OpenSearch })
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
    fun openingACardGrowsTheCardTowardTheCentreAndLeavesTheColumnWhereItIs() {
        showOpen()
        val rest = column()
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()

        assertTrue("the card opened", theCardIsOpen())
        val open = column()
        // The column itself no longer resizes (nothing is relaid out while a card grows): the card does the growing.
        assertEquals("the column's left", rest.left, open.left, 1f)
        assertEquals("the column's right", rest.right, open.right, 1f)
        assertEquals("the column's top", rest.top, open.top, 1f)
        assertEquals("the column's bottom", rest.bottom, open.bottom, 1f)
        val card = composeRule.openCardFrame()!!
        card.assertWhole("settled")
        assertTrue(
            "wider toward the middle than a resting card (${(rest.width - 2 * RiffleSpacing.l.value * dp) / dp} dp): " +
                "${card.laidOut.width / dp} dp",
            card.laidOut.width > rest.width - 2 * RiffleSpacing.l.value * dp + 40f * dp,
        )
        assertTrue(
            "about half the screen at most: ${card.laidOut.width / dp}",
            card.laidOut.width <= root().width * 0.55f,
        )
        assertTrue("it stands against the Dock's side", card.laidOut.right <= rest.right + 1f)
        // A side Dock holds no actions row: they are on the card.
        composeRule.onAllNodesWithTag(DOCK_CARD_ACTIONS_TEST_TAG).assertCountEquals(0)
        composeRule.onNodeWithText("Write a reply").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send reply").assertExists()
        // Home is still there.
        composeRule.onAllNodesWithTag(HOME_WORKSPACE_GRID_TEST_TAG).onFirst().assertExists()
    }

    // The open card is never cut off, at any frame of opening or closing

    @Test
    fun theOpenCardIsNeverClippedAtAnyFrameOfOpeningOrClosing() {
        show(unfolded(destination = ShellDestination.STREAM, reducedMotion = false))
        waitForText("Sam")
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Sam").performClick()
        var seen = 0
        var lastHeight = -1f
        var grew = 0
        repeat(OPEN_FRAMES) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.openCardFrame()?.let {
                it.assertWhole("opening frame $frame")
                seen++
                if (it.laidOut.height > lastHeight + 0.5f) grew++
                lastHeight = it.laidOut.height
            }
        }
        assertTrue("the card was there for most of the opening: $seen", seen > OPEN_FRAMES / 2)
        assertTrue("and it grew over several frames, not in one: $grew", grew >= 4)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        var closing = 0
        var shrank = 0
        repeat(OPEN_FRAMES) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.openCardFrame()?.let {
                it.assertWhole("closing frame $frame")
                closing++
                if (it.laidOut.height < lastHeight - 0.5f) shrank++
                lastHeight = it.laidOut.height
            }
        }
        assertTrue("the card was there while closing: $closing", closing > 3)
        assertTrue("and it shrank over several frames: $shrank", shrank >= 3)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertFalse("and it is gone", theCardIsOpen())
    }

    // The motion recomposes nothing

    @Test
    fun aFrameOfTheCardOpeningAndClosingRecomposesNeitherHomeNorTheStacksOtherCards() {
        show(unfolded(destination = ShellDestination.STREAM, reducedMotion = false))
        waitForText("Sam")
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val cells = probe.total(GRID_CELL_PROBE_KEY)
        val others = probe.total("stack-slot:")
        val height = composeRule.openCardFrame()?.laidOut?.height ?: 0f
        repeat(MOTION_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertTrue(
            "the card is still growing in the frames counted",
            (composeRule.openCardFrame()?.laidOut?.height ?: 0f) > height + 1f,
        )
        assertEquals("Home's cells recomposed while the card opened", cells, probe.total(GRID_CELL_PROBE_KEY))
        assertEquals("the stack's cards recomposed while the card opened", others, probe.total("stack-slot:"))

        repeat(OPEN_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val cellsClosing = probe.total(GRID_CELL_PROBE_KEY)
        val othersClosing = probe.total("stack-slot:")
        val closingHeight = composeRule.openCardFrame()?.laidOut?.height ?: 0f
        repeat(MOTION_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        assertTrue(
            "the card is still shrinking in the frames counted",
            (composeRule.openCardFrame()?.laidOut?.height ?: 0f) < closingHeight - 1f,
        )
        assertEquals("Home's cells recomposed while the card closed", cellsClosing, probe.total(GRID_CELL_PROBE_KEY))
        assertEquals("the stack's cards recomposed while it closed", othersClosing, probe.total("stack-slot:"))
    }

    // A tap outside an open card closes it, anywhere

    private fun LauncherShellState.withEmptyPages(): LauncherShellState {
        val layout = homeLayout.copy(pages = homeLayout.pages.map { it.copy(items = emptyList()) })
        return copy(
            homeLayout = layout,
            homeLayoutSet = homeLayoutSet.copy(layouts = mapOf(homeLayoutSet.activeKey to layout)),
        )
    }

    private fun tapAt(point: Offset) = composeRule.onRoot().performTouchInput { click(point) }

    @Test
    fun withACardOpenATapOnAnAppClosesTheCardAndLaunchesNothing() {
        showOpen()
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        assertTrue(theCardIsOpen())
        val app = gridCells().first().center

        tapAt(app)
        composeRule.waitForIdle()
        assertFalse("the card closed", theCardIsOpen())
        assertTrue("the tap launched or changed nothing else: $sent", sent.isEmpty())
        composeRule.onNodeWithTag(STREAM_COLUMN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun withACardOpenATapOnTheColumnsRoomOrTheWallpaperClosesOnlyTheCard() {
        show(unfolded(destination = ShellDestination.STREAM).withEmptyPages())
        waitForText("Sam")
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        assertTrue(theCardIsOpen())
        // Above the open card, inside the room the column has.
        val column = column()
        val card = composeRule.openCardFrame()!!.laidOut
        tapAt(Offset(card.center.x, (column.top + card.top) / 2f))
        composeRule.waitForIdle()
        assertFalse("a tap in the column's room closed the card", theCardIsOpen())
        assertTrue("and did nothing else: $sent", sent.isEmpty())

        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        assertTrue(theCardIsOpen())
        tapAt(Offset(40f * dp, 40f * dp))
        composeRule.waitForIdle()
        assertFalse("a tap on the wallpaper closed the card", theCardIsOpen())
        assertTrue("and did nothing else: $sent", sent.isEmpty())
        composeRule.onNodeWithTag(STREAM_COLUMN_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(OPEN_CARD_SCRIM_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun withOnlyTheStackShowingATapOnAnAppLaunchesItAndATapOnEmptySpaceClosesTheStream() {
        showOpen()
        val app = gridCells().first().center
        tapAt(app)
        composeRule.waitForIdle()
        assertTrue("the visible app launched: $sent", sent.any { it is LauncherShellAction.LaunchApp })
        assertFalse("and the Stream stayed", sent.contains(LauncherShellAction.OpenHome))

        sent.clear()
        shell = unfolded(destination = ShellDestination.STREAM).withEmptyPages()
        composeRule.waitForIdle()
        waitForText("Sam")
        tapAt(Offset(200f * dp, 300f * dp))
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
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
        val card = composeRule.openCardFrame()!!
        card.assertWhole("settled")
        assertEquals("the column stays put", column.left, column().left, 1f)
        assertTrue("the card stands against the Dock's side", card.laidOut.left >= column.left - 1f)
        assertTrue(
            "and grows toward the middle: ${card.laidOut.width / dp} dp",
            card.laidOut.width > column.width - 2 * RiffleSpacing.l.value * dp + 40f * dp,
        )
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
        val panel = bounds(DOCK_SEARCH_FIELD_TEST_TAG)
        val dock = dock()
        assertTrue("the field is against the Dock: ${panel.right} vs ${dock.left}", dock.left - panel.right < 24f * dp)
        assertTrue(
            "and no wider than the column plus its padding: ${panel.width / dp}",
            panel.width <= (StreamColumn.restWidthDp(root().width / dp) + 1f) * dp,
        )
    }

    @Test
    fun searchStandsInTheStreamColumnsOwnRectangleAgainstARightDock() = searchMatchesTheColumn(DockPosition.RIGHT)

    @Test
    fun searchStandsInTheStreamColumnsOwnRectangleAgainstALeftDock() = searchMatchesTheColumn(DockPosition.LEFT)

    /**
     * Search and the Stream's cards share one rectangle (`StreamColumn.rect`: the same width, the same side against the
     * Dock's inner edge, the same bottom), and at rest, once its opening spring has ended, the surface is fully opaque
     * with the field focused.
     */
    private fun searchMatchesTheColumn(dockEdge: DockPosition) {
        show(unfolded(dock = dockEdge, reducedMotion = false).searching(query = ""))
        composeRule.waitForIdle()
        val surface = composeRule.onNodeWithTag(DOCK_SEARCH_SURFACE_TEST_TAG).fetchSemanticsNode()
        assertEquals("the spring has ended", 1f, surface.config[DockSearchProgressKey], 0f)
        assertEquals("opaque at rest", 1f, surface.config[DockSearchAlphaKey], 0f)
        composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).assertIsFocused()
        val search = bounds(DOCK_SEARCH_COLUMN_TEST_TAG)
        val strip = dock()
        val body = bounds(HOME_DOCK_SURFACE_TEST_TAG)

        shell = unfolded(dock = dockEdge, destination = ShellDestination.STREAM, reducedMotion = false)
        waitForText("Sam")
        composeRule.waitForIdle()
        val column = column()
        assertEquals("same left: stream $column, search $search", column.left, search.left, 1f)
        // The Dock is shorter in the Home mode than while the Stream shows its tiles, so the two stand at the Dock's
        // lower end as it is in each mode: the size and the side are the same, the bottom is the Dock's.
        assertEquals("same width: stream $column, search $search", column.width, search.width, 1f)
        // The pages' rectangle is as tall as an open card's room (the stack stands at its bottom); search is as tall as
        // the resting stack, which it grows from.
        assertTrue(
            "the pages' room holds search's: stream $column, search $search",
            column.height >= search.height - 1f,
        )
        assertEquals("its bottom is the Dock's lower end", body.bottom, search.bottom, 4f * dp)
        val reach =
            if (dockEdge == DockPosition.RIGHT) search.right - strip.left else strip.right - search.left
        assertTrue("it touches the Dock's inner edge: $reach", reach in 0f..MAX_REACH * dp)
    }

    @Test
    fun searchAndItsFieldAreLiftedAboveTheKeyboardAndTheGridDoesNotMove() {
        show(unfolded(reducedMotion = false).searching(query = ""))
        composeRule.waitForIdle()
        val before = bounds(DOCK_SEARCH_COLUMN_TEST_TAG)
        val cellsBefore = gridCells()
        val keyboard = (KEYBOARD_DP * dp).toInt()
        composeRule.setImeInset(keyboard)
        val lifted = bounds(DOCK_SEARCH_COLUMN_TEST_TAG)
        assertTrue(
            "the panel stands above the keyboard: ${lifted.bottom} vs ${root().bottom - keyboard}",
            lifted.bottom <= root().bottom - keyboard + 1f,
        )
        assertTrue("it moved up", lifted.bottom < before.bottom)
        assertTrue(
            "the field is above the keyboard",
            bounds(DOCK_SEARCH_FIELD_TEST_TAG).bottom <= root().bottom - keyboard + 1f,
        )
        assertSameCells("with the keyboard up under search", cellsBefore, gridCells())
    }

    // Home takes no input while search covers it

    /** Home with search opened over it; where an app of Home's grid stands (under the gate). */
    private fun searchOverHome(): Offset {
        show()
        val spot = composeRule.onAllNodesWithText(firstAppLabel()).onFirst().fetchSemanticsNode().boundsInRoot.center
        shell = unfolded(reducedMotion = false).searching(query = "")
        composeRule.waitForIdle()
        return spot
    }

    @Test
    fun withSearchOpenAGateCoversAllOfHomeEdgeToEdge() {
        searchOverHome()
        composeRule.onAllNodesWithTag(HOME_INPUT_GATE_TEST_TAG).assertCountEquals(1)
        assertEquals(root(), bounds(HOME_INPUT_GATE_TEST_TAG))
    }

    @Test
    fun withSearchOpenALongPressOnTheAppsBehindItOpensNoMenu() {
        val spot = searchOverHome()
        composeRule.onRoot().performTouchInput {
            down(spot)
            advanceEventTime(LONG_PRESS_MILLIS)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Hide app").assertCountEquals(0)
        composeRule.onAllNodesWithText("App info").assertCountEquals(0)
        // Search takes it as a press on its room and closes; nothing of Home's is reached.
        assertTrue("only search closed: $sent", sent.all { it == LauncherShellAction.OpenHome })
    }

    @Test
    fun withSearchOpenASwipeOverTheAppsBehindItNeitherPagesHomeNorFiresAHomeSwipe() {
        val spot = searchOverHome()
        composeRule.onRoot().performTouchInput { swipe(spot, spot + Offset(0f, 300f), durationMillis = SWIPE_MILLIS) }
        composeRule.waitForIdle()
        assertTrue("Home took the swipe: $sent", sent.all { it == LauncherShellAction.OpenHome })
    }

    @Test
    fun aPressOnTheRoomAroundSearchClosesItAndReachesNothingBehind() {
        val spot = searchOverHome()
        composeRule.onRoot().performTouchInput { click(spot) }
        composeRule.waitForIdle()
        assertEquals("the press only closed search", listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
    }

    @Test
    fun theGateIsGoneOnceSearchHasClosedAndHomeTakesInputAgain() {
        show(unfolded(reducedMotion = false).searching(query = ""))
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(HOME_INPUT_GATE_TEST_TAG).assertCountEquals(1)
        shell = unfolded()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(HOME_INPUT_GATE_TEST_TAG).assertCountEquals(0)
    }

    // The grid never moves

    @Test
    fun homesGridDoesNotMoveWhenTheStreamOpensACardOpensOrTheKeyboardComesUp() {
        show()
        val closed = gridCells()
        assertTrue("the grid has cells", closed.size > 8)

        shell = unfolded(destination = ShellDestination.STREAM)
        waitForText("Sam")
        composeRule.waitForIdle()
        assertSameCells("with the Stream open", closed, gridCells())

        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        assertTrue("the card opened", theCardIsOpen())
        assertSameCells("with a card open", closed, gridCells())

        val keyboard = (KEYBOARD_DP * dp).toInt()
        composeRule.setImeInset(keyboard)
        assertSameCells("with the keyboard up", closed, gridCells())
        // What does move for the keyboard is the column: it stands above it.
        assertTrue(
            "the open card is lifted above the keyboard: ${column().bottom}",
            column().bottom <= root().bottom - keyboard + 1f,
        )
    }

    private fun firstAppLabel(): String = ScreenshotFixtures.installedApps.first().label

    private fun gridCells(): List<Rect> =
        composeRule.onAllNodesWithTag(HOME_GRID_CELL_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedWith(compareBy({ it.top }, { it.left }))

    private fun assertSameCells(
        state: String,
        expected: List<Rect>,
        actual: List<Rect>,
    ) {
        assertEquals("$state: the number of cells", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (a, b) ->
            assertTrue(
                "$state: cell $index moved from $a to $b",
                abs(a.left - b.left) < 0.5f && abs(a.top - b.top) < 0.5f &&
                    abs(a.right - b.right) < 0.5f && abs(a.bottom - b.bottom) < 0.5f,
            )
        }
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

    @Test
    fun unfoldedSearchOnALeftDockLight() = shoot(showing = { show(unfolded(dock = DockPosition.LEFT).searching()) })

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedSearchOnALeftDockDark() = shoot(showing = { show(unfolded(dock = DockPosition.LEFT).searching()) })

    @Test
    fun unfoldedSearchWithTheKeyboardUpLight() =
        shoot(
            showing = {
                show(unfolded().searching())
                composeRule.setImeInset((KEYBOARD_DP * dp).toInt())
            },
        )

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

        /** Frames of the test clock (16 ms each) that carry an open or close well past its end. */
        const val OPEN_FRAMES = 90

        /** Frames counted while the card is mid-way. */
        const val MOTION_FRAMES = 8
        const val LONG_PRESS_MILLIS = 1_500L

        /** A keyboard's height for the simulated input method inset. */
        const val KEYBOARD_DP = 300

        /** The Dock's margin (12 dp) and the column's reach past the glass (12 dp), with some room. */
        const val MAX_REACH = 30f
    }
}
