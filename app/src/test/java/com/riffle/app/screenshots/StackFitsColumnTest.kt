package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.app.launcher.QUICK_ACTIONS_STRIP_TEST_TAG
import com.riffle.app.launcher.stream.StripHeaderBottom
import com.riffle.app.launcher.stream.StripVisibleHeightKey
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.NextAlarm
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.workspace.sources.MediaControl
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Device fixes, round 7, items 1 and 2: on the owner's unfolded window (724 x 784 dp, the Dock on the right edge,
 * status
 * and gesture bars) the stack stands inside the Dock's body, through the real shell. The column is the body as it is
 * drawn; the lowest element of a page (the quick actions strip, else the front card) has its bottom edge on the body's
 * bottom edge, on every page; the tallest stack's top is not above the body's top; a strip shows whole lines only.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = OWNER_UNFOLDED)
internal class StackFitsColumnTest : OpenCardPolishHarness(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT) {
    private val nowPage = StreamPagePlanner.keyOf(StreamSection.NOW)
    private val music = ScreenshotFixtures.notes.identity.packageName.value

    private val session =
        MediaSessionInfo(
            packageName = music,
            title = "Midnight City",
            artist = "M83",
            isPlaying = true,
            positionMillis = 83_000L,
            durationMillis = 244_000L,
            controls = setOf(MediaControl.PLAY, MediaControl.PAUSE, MediaControl.PREVIOUS, MediaControl.NEXT),
        )

    private fun dockBody() = checkNotNull(boundsOf(HOME_DOCK_SURFACE_TEST_TAG))

    private fun host() = checkNotNull(boundsOf("stream-column-host"))

    private fun showNow(
        pins: Int = 5,
        top: Int = 28,
        bottom: Int = 24,
    ) {
        show(
            landOn = nowPage,
            waitFor = "Midnight City",
            pins = pins,
            mediaSession = session,
            nextAlarm = NextAlarm("alarm-1", StreamFixtures.NOW + 8 * 3_600_000L),
        )
        composeRule.setSystemBarInsets(top, bottom)
        composeRule.waitForIdle()
    }

    private fun showChat(
        pins: Int = 5,
        chats: Int = 3,
    ) {
        show(pins = pins, chats = chats)
        composeRule.setSystemBarInsets(28, 24)
        composeRule.waitForIdle()
    }

    private fun Rect.dp() = "[${left / density},${top / density} ${right / density},${bottom / density}] dp"

    private fun assertEdge(
        what: String,
        expectedPx: Float,
        actualPx: Float,
    ) = assertEquals(
        "$what: expected ${expectedPx / density} dp, was ${actualPx / density} dp",
        expectedPx,
        actualPx,
        density,
    )

    /**
     * The stack's rectangles as they are laid out (every card of the page the Stream reports, not the quick actions
     * strip): not the bounds the semantics give, which are clipped to the stack's own area, so a strip standing above
     * the column would read as standing at its top edge (the "tops cut off" of the device).
     */
    private fun cardRects(): List<Rect> {
        val strip = boundsOf(QUICK_ACTIONS_STRIP_TEST_TAG)
        return stream.dock.cardRects.rectsPx().filter { rect ->
            strip == null || abs(rect.top - strip.top) > 1f || abs(rect.bottom - strip.bottom) > 1f
        }
    }

    private fun stack(): Rect {
        val slots = cardRects()
        val left = slots.minOf { it.left }
        val top = slots.minOf { it.top }
        return Rect(left, top, slots.maxOf { it.right }, slots.maxOf { it.bottom })
    }

    @Test
    fun theColumnIsTheDockBodyAsItIsDrawn() {
        showChat()
        val body = dockBody()
        val host = host()
        assertEdge("column top (body ${body.dp()}, column ${host.dp()})", body.top, host.top)
        assertEdge("column bottom (body ${body.dp()}, column ${host.dp()})", body.bottom, host.bottom)
    }

    @Test
    fun theNowPagesStackFitsInsideTheDockBodyAndItsFrontCardStandsOnItsBottomEdge() {
        showNow()
        val body = dockBody()
        val stack = stack()
        assertTrue(
            "the stack's top is above the Dock body's: ${stack.dp()} in ${body.dp()}",
            stack.top >= body.top - density,
        )
        assertEdge(
            "front card bottom (no quick actions: the card is the lowest element; stack ${stack.dp()})",
            body.bottom,
            stack.bottom,
        )
    }

    @Test
    fun aPageWithQuickActionsStandsItsStripOnTheBodysBottomEdge() {
        showChat()
        val body = dockBody()
        val strip = checkNotNull(boundsOf(QUICK_ACTIONS_STRIP_TEST_TAG))
        assertEdge("quick actions strip bottom (body ${body.dp()}, strip ${strip.dp()})", body.bottom, strip.bottom)
        assertTrue("the stack is above the strip: ${stack().dp()} vs ${strip.dp()}", stack().bottom <= strip.top)
    }

    @Test
    fun aPageWithNoQuickActionsStandsItsFrontCardOnTheBodysBottomEdge() {
        show(landOn = mailPage, waitFor = "Ledger", pins = 5)
        composeRule.setSystemBarInsets(28, 24)
        composeRule.waitForIdle()
        val body = dockBody()
        assertEdge("front card bottom (body ${body.dp()}, stack ${stack().dp()})", body.bottom, stack().bottom)
    }

    @Test
    fun aLoneCardStandsOnTheBodysBottomEdge() {
        show(landOn = mailPage, waitFor = "Ledger", pins = 5, mails = 1)
        composeRule.setSystemBarInsets(28, 24)
        composeRule.waitForIdle()
        val body = dockBody()
        assertEdge("lone card bottom (body ${body.dp()}, card ${stack().dp()})", body.bottom, stack().bottom)
    }

    private fun nowStackStaysInside(pins: Int) {
        showNow(pins = pins)
        val body = dockBody()
        val stack = stack()
        assertTrue(
            "$pins pins: the stack's top is above the Dock body's: ${stack.dp()} in ${body.dp()}",
            stack.top >= body.top - density,
        )
    }

    @Test
    fun theNowPagesStackStaysInsideABodyOfOnePin() = nowStackStaysInside(1)

    @Test
    fun theNowPagesStackStaysInsideABodyOfThreePins() = nowStackStaysInside(3)

    @Test
    fun theNowPagesStackStaysInsideABodyOfEightPins() = nowStackStaysInside(8)

    @Test
    fun aStripShowsItsHeaderAndNothingBelowIt() {
        showChat(chats = 3)
        val shown =
            composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(StripVisibleHeightKey), useUnmergedTree = true)
                .fetchSemanticsNodes()
        assertTrue("the stack's cards report what shows: ${shown.size}", shown.size >= 3)
        val header = StripHeaderBottom.value * density
        val strips = shown.filter { it.config[StripVisibleHeightKey] < it.size.height - 0.5f }
        assertEquals("two strips (the cards behind the front one) are clipped", 2, strips.size)
        strips.forEach { node ->
            assertEquals(
                "a strip shows its header, ${StripHeaderBottom.value} dp, and no line of the body",
                header,
                node.config[StripVisibleHeightKey],
                0.5f,
            )
        }
        val front = shown.filter { it.config[StripVisibleHeightKey] >= it.size.height - 0.5f }
        assertEquals("the front card shows whole", 1, front.size)
    }
}
