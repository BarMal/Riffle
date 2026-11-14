package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.DrawnSurfaceKey
import com.riffle.app.launcher.GridFadeAlphaKey
import com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG
import com.riffle.app.launcher.HOME_SURFACE_TEST_TAG
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import com.riffle.core.domain.launcher.settings.LayoutSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
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
 * "Show apps behind the cards" on a folded phone (ADR 0003, Amendment 2; per posture): off (the default) Home fades out
 * while the Stream is open, as it always did; on, Home stays on screen behind the stack and its grid fades cell by cell
 * under it, the way the unfolded Home's does. In both, Home's grid never moves for the Stream, a card or the keyboard.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class FoldedAppsBehindCardsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "folded-behind").apply { isDaemon = true } }
    private val sent = mutableListOf<LauncherShellAction>()
    private var shell by mutableStateOf(folded())

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private val chatPackage = ScreenshotFixtures.chat.identity.packageName.value
    private val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")
    private val dp get() = composeRule.density.density

    private fun host(): StreamHost =
        realWiringStreamHost(
            executor = executor,
            activeNotifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("0|chat|1"),
                        packageName = AppPackageName(chatPackage),
                        canDismiss = true,
                        title = "Sam",
                        text = "Are we still on for lunch tomorrow?",
                        messages =
                            listOf(
                                LauncherNotificationMessage(
                                    "Sam",
                                    "Are we still on for lunch tomorrow?",
                                    StreamFixtures.NOW - 4 * MINUTE,
                                ),
                            ),
                        postedAtEpochMillis = StreamFixtures.NOW - 4 * MINUTE,
                    ),
                ),
            installedApps = ScreenshotFixtures.installedApps,
            canReply = { true },
            canSnooze = { true },
        ).also { it.pages.remember(HomeLayoutDeviceClass.PHONE, chatPage) }

    private fun folded(
        destination: ShellDestination = ShellDestination.HOME,
        appsBehind: Boolean = false,
        reducedMotion: Boolean = true,
    ): LauncherShellState {
        val base = ScreenshotFixtures.homeState().withHomePages(2).withManyApps().withAFullFirstPage()
        val settings = base.launcherSettings
        val layout = LayoutSettings()
        return base.copy(
            destination = destination,
            launcherSettings =
                settings.copy(
                    layout = layout.copy(folded = layout.folded.copy(appPeekThrough = appsBehind)),
                    motion =
                        settings.motion.copy(
                            reducedMotionPreference =
                                if (reducedMotion) ReducedMotionPreference.ON else ReducedMotionPreference.OFF,
                        ),
                ),
        )
    }

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

    private fun show(state: LauncherShellState) {
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
                                LauncherShellAction.OpenHome -> shell.copy(destination = ShellDestination.HOME)
                                else -> shell.answering(action)
                            }
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun cells(): List<Rect> =
        composeRule.onAllNodesWithTag(HOME_GRID_CELL_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedWith(compareBy({ it.top }, { it.left }))

    private fun fades(): List<Float> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(GridFadeAlphaKey)).fetchSemanticsNodes()
            .map { it.config[GridFadeAlphaKey] }

    @Test
    fun theOpenCardIsNeverClippedAtAnyFrameOfOpeningOrClosingFolded() {
        show(folded(reducedMotion = false))
        shell = folded(destination = ShellDestination.STREAM, reducedMotion = false)
        waitForText("Sam")
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Sam").performClick()
        var seen = 0
        repeat(FRAMES) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.openCardFrame()?.let {
                it.assertWhole("opening frame $frame")
                seen++
            }
        }
        assertTrue("the card was there while opening: $seen", seen > FRAMES / 2)
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        var closing = 0
        repeat(FRAMES) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.openCardFrame()?.let {
                it.assertWhole("closing frame $frame")
                closing++
            }
        }
        assertTrue("the card was there while closing: $closing", closing > 3)
    }

    @Test
    fun withTheKeyboardUpTheOpenCardKeepsItsHeaderAndTheReplyRowOnScreen() {
        show(folded())
        shell = folded(destination = ShellDestination.STREAM)
        waitForText("Sam")
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        composeRule.setImeInset((BIG_KEYBOARD_DP * dp).toInt())
        composeRule.waitForIdle()
        composeRule.openCardFrame()!!.assertWhole("keyboard up")
        composeRule.onNodeWithText("Sam").assertIsDisplayed()
        composeRule.onNodeWithText("Write a reply").assertIsDisplayed()
    }

    @Test
    fun foldedATapOnTheStacksEmptySpaceClosesTheStreamAndATapOnTheCardDoesNot() {
        show(folded())
        shell = folded(destination = ShellDestination.STREAM)
        waitForText("Sam")
        // The card's own tap opens it (a conversation) and leaves the Stream where it is.
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        assertTrue("the card opened", composeRule.openCardFrame() != null)
        assertTrue("and the Stream stayed: $sent", sent.isEmpty())
        // A tap beside the open card closes the card and leaves the Stream.
        composeRule.onRoot().performTouchInput { click(Offset(20f * dp, 200f * dp)) }
        composeRule.waitForIdle()
        assertTrue("the card closed", composeRule.openCardFrame() == null)
        assertTrue("the Stream stayed: $sent", sent.isEmpty())
        // A tap on the empty space above the stack closes the Stream.
        composeRule.onRoot().performTouchInput { click(Offset(width / 2f, 120f * dp)) }
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
    }

    @Test
    fun theDefaultIsOffAndHomeFadesOutWithTheStreamAsItAlwaysDid() {
        show(folded())
        shell = folded(destination = ShellDestination.STREAM)
        waitForText("Sam")
        assertEquals("Home is not drawn behind the Stream", 0, cells().size)
        assertTrue(
            "Home is faded out",
            composeRule.onAllNodesWithTag(HOME_SURFACE_TEST_TAG).fetchSemanticsNodes().none {
                it.config[DrawnSurfaceKey].alpha > 0.01f
            },
        )
    }

    @Test
    fun withAppsBehindTheCardsHomeStaysAndItsGridFadesUnderTheStack() {
        show(folded())
        shell = folded(destination = ShellDestination.STREAM, appsBehind = true)
        waitForText("Sam")
        composeRule.onNodeWithTag(HOME_SURFACE_TEST_TAG).assertExistsWithAlpha(1f)
        assertTrue("Home's grid is drawn", cells().size > 8)
        val fades = fades()
        assertTrue(
            "cells under the stack fade to the peek: $fades",
            fades.any { abs(it - StreamGridFade.PEEK_ALPHA) < 0.01f },
        )
        // The fade is a long, soft falloff now: the cells farthest from the stack are close to untouched.
        assertTrue("cells far from it are close to untouched: $fades", fades.any { it > 0.8f })
    }

    @Test
    fun theGridNeverMovesForTheStreamACardOrTheKeyboardWithAppsBehindTheCards() {
        show(folded(appsBehind = true))
        val closed = cells()
        assertTrue("the grid has cells", closed.size > 8)

        shell = folded(destination = ShellDestination.STREAM, appsBehind = true)
        waitForText("Sam")
        assertSame("with the Stream open", closed, cells())

        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
        assertTrue(
            "a card opened",
            composeRule.onAllNodesWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNodes().isNotEmpty(),
        )
        assertSame("with a card open", closed, cells())

        val keyboard = (KEYBOARD_DP * dp).toInt()
        composeRule.setImeInset(keyboard)
        assertSame("with the keyboard up", closed, cells())
        composeRule.onRoot().assertExists()
    }

    @Test
    fun theGridDoesNotMoveForTheKeyboardOnPlainHomeEither() {
        show(folded())
        val closed = cells()
        composeRule.setImeInset((KEYBOARD_DP * dp).toInt())
        assertSame("with the keyboard up", closed, cells())
        composeRule.setImeInset(0)
        assertSame("with the keyboard gone", closed, cells())
        assertFalse(closed.isEmpty())
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsWithAlpha(alpha: Float) {
        assertEquals(alpha, fetchSemanticsNode().config[DrawnSurfaceKey].alpha, 0.001f)
    }

    private fun assertSame(
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
    fun foldedStreamWithAppsBehindTheCardsLight() = shoot(showing = { open(appsBehind = true) })

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun foldedStreamWithAppsBehindTheCardsDark() = shoot(showing = { open(appsBehind = true) })

    @Test
    fun foldedOpenCardWithAppsBehindTheCardsLight() = shoot(showing = { openCard() })

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun foldedOpenCardWithAppsBehindTheCardsDark() = shoot(showing = { openCard() })

    @Test
    fun foldedStreamWithAppsBehindTheCardsOffLight() = shoot(showing = { open(appsBehind = false) })

    private fun open(appsBehind: Boolean) {
        show(folded(destination = ShellDestination.STREAM, appsBehind = appsBehind))
        waitForText("Sam")
    }

    private fun openCard() {
        open(appsBehind = true)
        composeRule.onNodeWithText("Sam").performClick()
        composeRule.waitForIdle()
    }

    private fun shoot(showing: () -> Unit) {
        showing()
        composeRule.captureScreen()
    }

    private companion object {
        /** Frames of the test clock (16 ms each) that carry an open or close well past its end. */
        const val FRAMES = 90

        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
        const val KEYBOARD_DP = 280
        const val BIG_KEYBOARD_DP = 400
    }
}
