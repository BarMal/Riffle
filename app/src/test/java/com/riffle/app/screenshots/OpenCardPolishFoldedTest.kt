package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import com.riffle.app.launcher.DOCK_CARD_ACTIONS_TEST_TAG
import com.riffle.app.launcher.DOCK_QUICK_ACTIONS_TEST_TAG
import com.riffle.app.launcher.DockHeaderAlphaKey
import com.riffle.app.launcher.HOME_DOCK_TEST_TAG
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.SlimTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The same polish on the folded phone (a Dock along the bottom edge holds the quick actions and, with a card open, the
 * card's own actions): the open card grows from the collapsed front card and back to it, the stack rests where it
 * stood, the card uses the room between the top of the Stream and the Dock, and the Dock's row swaps its chips for the
 * card's actions without a pop: the height eases, the contents cross-fade through nothing at full size and the row is
 * never two things at once.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
internal class OpenCardPolishFoldedTest : OpenCardPolishHarness(HomeLayoutDeviceClass.PHONE, null) {
    /** What the Dock's row is at one frame: which of the two it holds, its height and the opacity it is drawn at. */
    private data class Row(val tag: String?, val height: Float, val alpha: Float, val dockTop: Float)

    private fun row(): Row {
        val nodes =
            composeRule.onAllNodes(hasTestTag(DOCK_CARD_ACTIONS_TEST_TAG) or hasTestTag(DOCK_QUICK_ACTIONS_TEST_TAG))
                .fetchSemanticsNodes()
        assertTrue("the row is two things at once: ${nodes.size} nodes", nodes.size <= 1)
        val dock = checkNotNull(boundsOf(HOME_DOCK_TEST_TAG)).top
        val node = nodes.firstOrNull() ?: return Row(null, 0f, 0f, dock)
        return Row(
            node.config.getOrNull(SemanticsProperties.TestTag),
            node.boundsInRoot.height,
            node.config.getOrNull(DockHeaderAlphaKey) ?: 1f,
            dock,
        )
    }

    private class Run(val frames: List<PolishFrame>, val rows: List<Row>)

    private fun run(frames: Int): Run {
        val seen = mutableListOf<PolishFrame>()
        val rows = mutableListOf<Row>()
        repeat(frames + 1) {
            seen += frame()
            rows += row()
            composeRule.mainClock.advanceTimeByFrame()
        }
        return Run(seen, rows)
    }

    private fun openAndClose(): Triple<Rect, Run, Run> {
        show()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        val rest = settledSlot()
        tapFront()
        val opening = run(90)
        composeRule.runOnUiThread { stream.dock.openCards.closeByPress() }
        val closing = run(90)
        return Triple(rest, opening, closing)
    }

    /** Each edge of the card moves one way only: the Dock under it may rise or fall, but never back and forth. */
    private fun assertEachEdgeMovesOneWay(
        cards: List<Rect>,
        what: String,
    ) {
        listOf<
            Pair<
                String,
                (
                    Rect,
                ) -> Float,
                >,
            >("top" to { it.top }, "bottom" to { it.bottom }, "left" to { it.left }).forEach { (edge, of) ->
            val values = cards.map(of)
            val up = values.zipWithNext().all { (a, b) -> b >= a - TOL }
            val down = values.zipWithNext().all { (a, b) -> b <= a + TOL }
            assertTrue("$what: the $edge went both ways: ${values.map { it.toInt() }}", up || down)
        }
    }

    @Test
    fun theStackRestsInTheSamePlaceBeforeItOpensAndAfterItCloses() {
        val (rest, opening, closing) = openAndClose()
        val after = closing.frames.last()
        assertNull(after.open)
        assertEquals(rest, after.slot)
        assertEquals(rest, opening.frames.first().slot)
    }

    @Test
    fun theOpenCardGrowsFromTheFrontCardAndShrinksBackToItOneWayOnly() {
        val (rest, opening, closing) = openAndClose()
        val grow = opening.frames.mapNotNull { it.open }
        assertEquals("first frame, top", rest.top, grow.first().top, TOL)
        assertEquals("first frame, left", rest.left, grow.first().left, TOL)
        assertEachEdgeMovesOneWay(grow, "opening")
        assertTrue(grow.last().height > rest.height * 2f)
        val shrink = closing.frames.mapNotNull { it.open }
        assertEachEdgeMovesOneWay(shrink, "closing")
        assertEquals("last frame, top", rest.top, shrink.last().top, TOL)
        assertEquals("last frame, bottom", rest.bottom, shrink.last().bottom, TOL)
    }

    @Test
    fun theOpenCardUsesTheRoomBetweenTheTopOfTheStreamAndTheDock() {
        val (_, opening, _) = openAndClose()
        val card = checkNotNull(opening.frames.last().open)
        val dockTop = opening.rows.last().dockTop
        // Under the Dock's row (which has grown with the card's actions) by the page's inset and no more.
        assertTrue(
            "the card ends ${(dockTop - card.bottom) / density} dp above the Dock",
            dockTop - card.bottom in 0f..(32f * density),
        )
        // Over the top: two slim tabs of a card behind it, a pitch each (28 dp, 56 dp before), and the page's little
        // air above them.
        val above = card.top - 2 * SlimTabs.PITCH_DP * density
        assertTrue("the strips start ${above / density} dp from the top", above in 0f..(16f * density))
    }

    @Test
    fun theDocksRowSwapsChipsForTheCardsActionsAsOneThingThatEasesAndNeverPops() {
        val (_, opening, closing) = openAndClose()
        for ((what, rows) in listOf("opening" to opening.rows, "closing" to closing.rows)) {
            val log =
                rows.mapIndexed {
                        i,
                        r,
                    ->
                    "$i:${r.tag}/${r.height.toInt()}/${"%.2f".format(r.alpha)}"
                }.joinToString(" ")
            // The content changes only while nothing is drawn of it: no frame has the swap at a visible strength.
            rows.zipWithNext().forEachIndexed { i, (a, b) ->
                if (a.tag != null && b.tag != null && a.tag != b.tag) {
                    assertTrue(
                        "$what frame $i: the row swapped while drawn at ${a.alpha}/${b.alpha}\n$log",
                        a.alpha < 0.05f && b.alpha < 0.2f,
                    )
                }
                // The height eases: no frame takes more than a third of the whole change at once.
                assertTrue(
                    "$what frame $i: the row's height jumped ${a.height} to ${b.height}\n$log",
                    kotlin.math.abs(b.height - a.height) < 0.34f *
                        rows.maxOf {
                            it.height -
                                rows.minOf {
                                        r ->
                                    r.height
                                }
                        }.coerceAtLeast(1f),
                )
                // And the Dock under it never jumps with it.
                assertTrue(
                    "$what frame $i: the Dock jumped ${a.dockTop} to ${b.dockTop}\n$log",
                    kotlin.math.abs(b.dockTop - a.dockTop) < 60f,
                )
            }
            assertTrue("$what: the row never swapped\n$log", rows.map { it.tag }.distinct().size == 2)
            // It cross-fades through nothing: some frame has it fully faded, and it ends fully drawn.
            assertTrue("$what: no dip between the two\n$log", rows.any { it.alpha < 0.05f })
            assertEquals("$what: ends fully drawn\n$log", 1f, rows.last().alpha, 0.001f)
        }
        // Opened, the row holds the card's actions and is taller than the chips were; closed, the chips are back.
        assertEquals(DOCK_CARD_ACTIONS_TEST_TAG, opening.rows.last().tag)
        assertTrue(opening.rows.last().height > opening.rows.first().height * 1.5f)
        assertEquals(DOCK_QUICK_ACTIONS_TEST_TAG, closing.rows.last().tag)
        assertEquals(opening.rows.first().height, closing.rows.last().height, 1f)
        assertEquals(opening.rows.first().dockTop, closing.rows.last().dockTop, 1f)
    }

    @Test
    fun theFoldedOpenCardDrawsInsideTheWindow() {
        val (_, opening, _) = openAndClose()
        val window = checkNotNull(boundsOf("stream-surface"))
        opening.frames.mapNotNull {
            it.open
        }.forEach { assertTrue("$it outside $window", it.top >= window.top - 1f && it.bottom <= window.bottom + 1f) }
    }

    private companion object {
        const val TOL = 1.5f
    }
}
