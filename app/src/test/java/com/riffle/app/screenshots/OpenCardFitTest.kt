package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.SlimTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * "Fit content, up to the Dock's height", asserted at layout level through the real shell. An open card is as tall as
 * what it holds (header, whole body, reply row, its own actions) and never shorter than the collapsed front card; its
 * ceiling is the Stream column (the Dock body's span) less the room its neighbours' slim tabs take
 * ([SlimTabs.PITCH_DP] each: "Slim neighbour strips"); its body scrolls only
 * once it has reached that ceiling, and a card below the ceiling has neither a scroll range nor empty space inside.
 */
internal abstract class OpenCardFitHarness(
    deviceClass: HomeLayoutDeviceClass,
    dock: DockPosition?,
) : OpenCardPolishHarness(deviceClass, dock) {
    /** [n] sentences that each take about two lines of the open card's body. */
    protected fun sentences(n: Int) = (1..n).joinToString(" ") { "Line $it of a message that goes on and on." }

    protected class Settled(
        val card: Rect,
        /** The strips of the cards behind the open card, top to bottom. */
        val strips: List<Rect>,
        /** How far the body scrolls, in dp (0: it does not). */
        val scrollRangeDp: Float,
        /** How tall the scrolling body's viewport is, in dp (the lines of the body that show at once). */
        val viewportDp: Float,
        /** The empty room between the last line of the body and the reply row, beyond the spacing every gap has. */
        val slackDp: Float,
        /** The collapsed front card, before it opened. */
        val collapsed: Rect,
    )

    protected fun openAndSettle(): Settled {
        val collapsed = settledSlot()
        tapFront()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        val scrollNodes =
            composeRule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
                    hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
        val scroll = scrollNodes.maxOf { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
        val viewport = scrollNodes.maxOf { it.boundsInRoot.height }
        val inside =
            composeRule.onAllNodes(hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)), useUnmergedTree = true)
                .fetchSemanticsNodes()
        val bodyEnd =
            inside.filter { it.textOrEmpty().startsWith("Line ") || it.textOrEmpty() == "Hi." }
                .maxOf { it.boundsInRoot.bottom }
        // Under the body stands the reply row (its own box starts 16 dp above its text: the field's padding) or, when
        // the actions are in the Dock, the card's own bottom padding of one spacing.
        val replyTop = inside.firstOrNull { it.textOrEmpty() == "Write a reply" }?.boundsInRoot?.top
        val gap =
            if (replyTop != null) {
                (replyTop - bodyEnd) / density - REPLY_TEXT_INSET_DP
            } else {
                (card.bottom - bodyEnd) / density
            }
        return Settled(
            card = card,
            strips = cardSlots().sortedBy { it.top },
            scrollRangeDp = scroll / density,
            viewportDp = viewport / density,
            slackDp = gap - SPACING_DP,
            collapsed = collapsed,
        )
    }

    private fun androidx.compose.ui.semantics.SemanticsNode.textOrEmpty(): String =
        config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

    protected fun Settled.describe() =
        "card ${card.height / density} dp, strips ${strips.size}, scroll $scrollRangeDp dp, slack $slackDp dp"

    protected companion object {
        /** The spacing between the body and the reply row, and the field's own padding above its text. */
        const val SPACING_DP = 12f
        const val REPLY_TEXT_INSET_DP = 16f
        const val TOL_PX = 1f
    }
}

/** The owner's unfolded window: 724 x 784 dp, the Dock on the right edge, three chats, the quick actions strip. */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = OWNER_UNFOLDED)
internal class OpenCardFitUnfoldedTest : OpenCardFitHarness(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT) {
    private fun host() = checkNotNull(boundsOf("stream-column-host"))

    private fun dockBody() = checkNotNull(boundsOf(HOME_DOCK_SURFACE_TEST_TAG))

    @Test
    fun aCardWithMoreToSayThanTheColumnHoldsFillsItLessItsStripsAndItsBodyScrolls() {
        show(sam = sentences(12))
        val s = openAndSettle()
        val host = host()
        val body = dockBody()
        // The column is the Dock body's span (the body is clamped inside the safe area, the column is not narrower).
        assertTrue("the column spans the Dock body: ${host.height / density} dp", host.height >= body.height - TOL_PX)
        // Card and strips fill the column but for the page's own padding (8 dp above, 16 dp under the stack).
        val topPad = 8f * density
        val bottomGap = 16f * density
        assertEquals("the strips start under the column's top", host.top + topPad, s.strips.first().top, TOL_PX)
        assertEquals("the card stands above the column's bottom", host.bottom - bottomGap, s.card.bottom, TOL_PX)
        assertEquals("the card meets the strips above it", s.strips.last().bottom + 0f, s.card.top, 2 * density)
        // So strips and card together stand where the Dock body stands, to within the padding the page keeps.
        val span = s.card.bottom - s.strips.first().top
        assertEquals("the stack spans the Dock body (${s.describe()})", body.height, span, 2f * density)
        assertEquals("top", body.top, s.strips.first().top, 5f * density)
        assertEquals("bottom", body.bottom, s.card.bottom, 5f * density)
        assertTrue("it scrolls: ${s.describe()}", s.scrollRangeDp > 0f)
        val ceiling = host.height - topPad - bottomGap - s.strips.size * SlimTabs.PITCH_DP * density
        assertTrue("and no more than the ceiling: ${s.describe()}", s.card.height <= ceiling + TOL_PX)
        // It fills it: with two slim tabs of 28 dp the ceiling is 432 dp (360 dp with two strips of 64 dp), and the
        // body's viewport, the card less its header, its reply row and their spacing (212 dp), is 220 dp (148 dp).
        assertEquals("the card reaches its ceiling: ${s.describe()}", ceiling, s.card.height, TOL_PX)
        assertEquals("the ceiling is 432 dp: ${s.describe()}", 432f, s.card.height / density, 1f)
        assertEquals("the body's viewport is 220 dp: ${s.describe()}", 220f, s.viewportDp, 1f)
        // The tabs are a slim pitch apart and the open card stands against the lowest.
        s.strips.zipWithNext().forEach { (a, b) ->
            assertEquals("tabs 28 dp apart: ${s.describe()}", SlimTabs.PITCH_DP * density, b.top - a.top, 1.5f)
        }
    }

    @Test
    fun aCardThatFitsBeneathTheCeilingIsAsTallAsItsContentAndDoesNotScroll() {
        // One older card behind it: one tab, a ceiling 28 dp higher than with two (460 dp). Six sentences are taller
        // than the 360 dp that two full strips left and fit under this one.
        show(sam = sentences(6), chats = 2)
        val s = openAndSettle()
        val host = host()
        val ceiling = host.height - 24f * density - s.strips.size * SlimTabs.PITCH_DP * density
        assertTrue("it is taller than 360 dp: ${s.describe()}", s.card.height > 360f * density)
        assertTrue("under its ceiling: ${s.describe()}", s.card.height < ceiling - 4f * density)
        assertEquals("no scroll range: ${s.describe()}", 0f, s.scrollRangeDp, 0f)
        assertEquals("no empty room inside: ${s.describe()}", 0f, s.slackDp, 1f)
    }

    @Test
    fun aCardThatNeededScrollingBesideTwoFullStripsNowFitsBesideTwoSlimTabs() {
        // Six sentences are 380 dp: taller than the 360 dp ceiling two 64 dp strips left (the body scrolled 20 dp), and
        // under the 432 dp two 28 dp tabs leave: the whole of it shows and nothing scrolls.
        show(sam = sentences(6))
        val s = openAndSettle()
        assertEquals("two tabs: ${s.describe()}", 2, s.strips.size)
        assertEquals("380 dp, the height of its content: ${s.describe()}", 380f, s.card.height / density, 1f)
        assertEquals("no scroll range: ${s.describe()}", 0f, s.scrollRangeDp, 0f)
        assertEquals("no empty room inside: ${s.describe()}", 0f, s.slackDp, 1f)
    }

    @Test
    fun aShortCardIsAsTallAsItsContentAndNeverShorterThanTheCollapsedFrontCard() {
        show(sam = "Hi.")
        val s = openAndSettle()
        val least = s.collapsed.height - TOL_PX
        assertTrue("not shorter than the collapsed card: ${s.describe()}", s.card.height >= least)
        assertTrue("short stays short: ${s.describe()}", s.card.height < host().height * 0.6f)
        assertEquals("no scroll range: ${s.describe()}", 0f, s.scrollRangeDp, 0f)
    }

    @Test
    fun aLoneCardWithNoNeighboursTakesTheWholeColumnWhenItNeedsIt() {
        show(sam = sentences(12), chats = 1)
        val s = openAndSettle()
        val host = host()
        assertEquals("no strips", 0, s.strips.size)
        assertEquals("top", host.top + 8f * density, s.card.top, TOL_PX)
        assertEquals("bottom", host.bottom - 16f * density, s.card.bottom, TOL_PX)
        assertTrue("it scrolls: ${s.describe()}", s.scrollRangeDp > 0f)
    }
}

/** The folded phone: the Dock along the bottom edge, the card's room is the Stream's top to the Dock. */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
internal class OpenCardFitFoldedTest : OpenCardFitHarness(HomeLayoutDeviceClass.PHONE, null) {
    private fun dockTop() = checkNotNull(boundsOf(HOME_DOCK_TEST_TAG)).top

    @Test
    fun aCardWithMoreToSayThanTheRoomFillsItAndItsBodyScrolls() {
        show(sam = sentences(30))
        val s = openAndSettle()
        // The room is the Stream's top (a gap of 8 dp under the status bar's inset, none in this window) to the Dock,
        // less the gap the stack keeps above the Dock: the strips and the card fill it.
        assertTrue("the strips reach the top: ${s.strips.first().top}", s.strips.first().top <= 12f * density)
        assertEquals("the card stands on the Dock's gap", dockTop(), s.card.bottom, 30f * density)
        assertTrue("it scrolls: ${s.describe()}", s.scrollRangeDp > 0f)
        // Two slim tabs of 28 dp (two strips of 56 dp before) stand above it.
        assertEquals("two tabs: ${s.describe()}", 2, s.strips.size)
        assertEquals(
            "the card stands against the tabs, 2 x 28 dp: ${s.describe()}",
            2 * SlimTabs.PITCH_DP * density,
            s.card.top - s.strips.first().top,
            2f * density,
        )
    }

    @Test
    fun aCardThatFitsBeneathTheCeilingIsAsTallAsItsContentAndDoesNotScroll() {
        show(sam = sentences(5))
        val s = openAndSettle()
        assertEquals("no scroll range: ${s.describe()}", 0f, s.scrollRangeDp, 0f)
        assertEquals("no empty room inside: ${s.describe()}", 0f, s.slackDp, 1f)
    }

    @Test
    fun aShortCardIsAsTallAsItsContentAndNeverShorterThanTheCollapsedFrontCard() {
        show(sam = "Hi.")
        val s = openAndSettle()
        assertTrue("not shorter than the collapsed card: ${s.describe()}", s.card.height >= s.collapsed.height - TOL_PX)
        assertEquals("no scroll range: ${s.describe()}", 0f, s.scrollRangeDp, 0f)
    }
}
