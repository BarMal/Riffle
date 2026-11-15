package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.QUICK_ACTIONS_STRIP_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_STRIP_TEST_TAG
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.SlimTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Open card polish (device fixes, "Open card polish"): frame by frame, through the real shell with the test clock
 * stepped by hand, on the owner's unfolded window (724 x 784 dp, the Dock on the right edge, three chats and the
 * quick actions strip under the stack). The open card grows from the collapsed front card's own rectangle, one way
 * only, and shrinks back to exactly it; the stack rests in the same place before it opens and after it closes, and
 * whatever the strip does; the open card takes the room the column has; the cards around it show as strips from the
 * first frame.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = OWNER_UNFOLDED)
internal class OpenCardPolishUnfoldedTest : OpenCardPolishHarness(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT) {
    private fun openAndClose(more: Int = 0): Triple<Rect, List<PolishFrame>, List<PolishFrame>> {
        show(more = more)
        composeRule.mainClock.autoAdvance = false
        val rest = settledSlot()
        tapFront()
        val opening = record(80)
        composeRule.runOnUiThread { stream.dock.openCards.closeByPress() }
        val closing = record(80)
        return Triple(rest, opening, closing)
    }

    private fun List<PolishFrame>.log() =
        mapIndexed { i, f -> "$i: slot=${f.slot} open=${f.open} reserve=${f.reserve}" }.joinToString("\n")

    @Test
    fun theStackRestsInTheSamePlaceBeforeItOpensAndAfterItCloses() {
        val (rest, opening, closing) = openAndClose()
        val after = closing.last()
        assertNull("the card is gone once it has closed\n${closing.log()}", after.open)
        assertEquals("left", rest.left, after.slot!!.left, 0.5f)
        assertEquals("top", rest.top, after.slot!!.top, 0.5f)
        assertEquals("right", rest.right, after.slot!!.right, 0.5f)
        assertEquals("bottom", rest.bottom, after.slot!!.bottom, 0.5f)
        // And the first frames of the opening are the stack where it was.
        assertEquals(rest, opening.first().slot)
    }

    @Test
    fun theOpenCardGrowsFromTheCollapsedFrontCardAndOnlyEverFurtherOut() {
        val (rest, opening, _) = openAndClose()
        val cards = opening.mapNotNull { it.open }
        assertTrue("the card never opened\n${opening.log()}", cards.size > 20)
        // The first frame it is drawn it is the front card's rectangle (to the pixel the edges are rounded to).
        val first = cards.first()
        assertEquals("first frame, top\n${opening.log()}", rest.top, first.top, TOL)
        assertEquals("first frame, bottom\n${opening.log()}", rest.bottom, first.bottom, TOL)
        assertEquals("first frame, left\n${opening.log()}", rest.left, first.left, TOL)
        assertEquals("first frame, right\n${opening.log()}", rest.right, first.right, TOL)
        cards.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame $i: the top went back down ($a to $b)\n${opening.log()}", b.top <= a.top + TOL)
            assertTrue("frame $i: the bottom went back up ($a to $b)\n${opening.log()}", b.bottom >= a.bottom - TOL)
            assertTrue("frame $i: the left edge went back in ($a to $b)\n${opening.log()}", b.left <= a.left + TOL)
            assertEquals("frame $i: the right edge moved ($a to $b)\n${opening.log()}", a.right, b.right, TOL)
        }
        val last = cards.last()
        assertTrue("it grew: ${rest.height} to ${last.height}", last.height > rest.height * 1.4f)
        // One spring: eased, not a cut.
        val eased = cards.count { it.height > rest.height + 4f && it.height < last.height - 4f }
        assertTrue("it jumped instead of easing: $eased frames in between\n${opening.log()}", eased >= 8)
    }

    @Test
    fun theOpenCardShrinksBackToExactlyTheCollapsedFrontCardAndNeverMovesAgain() {
        val (rest, _, closing) = openAndClose()
        val cards = closing.mapNotNull { it.open }
        assertTrue(cards.size > 20)
        cards.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame $i: the top went back up ($a to $b)\n${closing.log()}", b.top >= a.top - TOL)
            assertTrue("frame $i: the bottom went back down ($a to $b)\n${closing.log()}", b.bottom <= a.bottom + TOL)
            assertTrue("frame $i: the left edge went back out ($a to $b)\n${closing.log()}", b.left >= a.left - TOL)
        }
        val last = cards.last()
        assertEquals("last frame, top\n${closing.log()}", rest.top, last.top, TOL)
        assertEquals("last frame, bottom\n${closing.log()}", rest.bottom, last.bottom, TOL)
        // After the card is gone the stack is where the card ended: it does not move again.
        val gone = closing.indexOfFirst { it.open == null }
        assertTrue("the card never went\n${closing.log()}", gone > 0)
        closing.drop(gone).forEach { f -> assertEquals("the stack moved after landing", rest, f.slot) }
    }

    @Test
    fun aCardWithALotToSayTakesTheColumnLessTheStripsOfItsNeighbours() {
        show()
        tapFront()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val host = checkNotNull(boundsOf("stream-column-host"))
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        val strips = 2 * SlimTabs.PITCH_DP * density
        // Two older cards stand above it as slim tabs and the page's gap stands under it: the rest is the card's.
        assertTrue(
            "the card is ${card.height / density} dp in a column ${host.height / density} dp tall",
            card.height >= host.height - strips - 40f * density,
        )
        assertTrue("it is no taller than the column", card.top >= host.top && card.bottom <= host.bottom + 1f)
    }

    @Test
    fun aCardWithLittleToSayKeepsItsHeightAndIsNeverSmallerThanTheCollapsedOne() {
        show(sam = "Hi.")
        composeRule.mainClock.autoAdvance = false
        val rest = settledSlot()
        tapFront()
        val frames = record(80)
        val cards = frames.mapNotNull { it.open }
        assertTrue(cards.all { it.height >= rest.height - TOL })
        val host = checkNotNull(boundsOf("stream-column-host"))
        assertTrue("a short card does not fill the column", cards.last().height < host.height * 0.8f)
        assertEquals("first frame, bottom", rest.bottom, cards.first().bottom, TOL)
    }

    @Test
    fun theRoomOfTheStripIsKeptWhileItGoesAndComesBackSoNothingAroundItMoves() {
        val (_, opening, closing) = openAndClose()
        val rests = (opening + closing).mapNotNull { it.reserve }
        assertEquals("the reserve is always there", opening.size + closing.size, rests.size)
        rests.forEach { assertEquals("the strip's room moved", rests.first(), it) }
        // The strip is there at rest, is out of the tree while the card is open, and is back after.
        assertNotNull(opening.first().strip)
        assertNull(opening.last().strip)
        assertNotNull(closing.last().strip)
        assertEquals("the strip returns where it was", opening.first().strip, closing.last().strip)
    }

    /**
     * The stack's front card stands above the room the page keeps under it ([footerDp]: the quick actions strip and its
     * gap, 60 dp, on a page that has quick actions; none on a page that has none), on the Dock body's bottom edge.
     */
    private fun assertTheStackStandsAboveTheRoom(footerDp: Float) {
        val pages = checkNotNull(boundsOf("stream-pages"))
        assertEquals("the front card's bottom", pages.bottom - footerDp * density, settledSlot().bottom, TOL)
    }

    @Test
    fun aPageWithQuickActionsHasTheStackAboveTheStripsRoom() {
        show()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag(QUICK_ACTIONS_STRIP_TEST_TAG).fetchSemanticsNodes().isNotEmpty())
        // The strip's gap (8 dp) and the strip (52 dp): the strip stands on the column's bottom edge.
        assertTheStackStandsAboveTheRoom(8f + 52f)
        assertNotNull("the room is kept", reserveBounds())
    }

    @Test
    fun aPageWithNoQuickActionsStandsItsFrontCardOnTheColumnsBottomEdge() {
        // Round 7, item 2: the room is no longer kept on every page; with no strip the front card is the lowest
        // element and stands on the Dock body's bottom edge (it used to stand 76 dp above it).
        show(landOn = mailPage, waitFor = "Ledger")
        composeRule.waitForIdle()
        assertTrue(
            "no strip",
            composeRule.onAllNodesWithTag(QUICK_ACTIONS_STRIP_TEST_TAG).fetchSemanticsNodes().isEmpty(),
        )
        assertTheStackStandsAboveTheRoom(0f)
        assertEquals("no room is kept", 0f, checkNotNull(reserveBounds()).height, 0f)
    }

    @Test
    fun theCardsAroundTheOpenCardShowAsStripsFromTheFirstFrameItIsDrawn() {
        show()
        composeRule.mainClock.autoAdvance = false
        tapFront()
        val frames = record(60)
        val settled = frames.last()
        val open = checkNotNull(settled.open)
        val others = otherCards()
        assertEquals("the two cards behind it are strips above it", 2, others.size)
        others.forEach { assertTrue("a strip is above the open card: $it vs $open", it.bottom <= open.top + 2f) }
        // From the first frame it is drawn: the cards behind are already out of its way, never under it.
        frames.filter { it.open != null }.forEach { f ->
            val open0 = f.open!!
            assertTrue("a card behind is under the open card in ${f.slot}", f.slot!!.bottom <= open0.top + TOL)
        }
    }

    @Test
    fun theCardsInFrontOfAnOpenCardShowAsStripsBelowIt() {
        show(more = 1)
        tapFront()
        composeRule.waitForIdle()
        invokeStep("Next card")
        invokeStep("Next card")
        val open = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        val below =
            composeRule.onAllNodesWithTag(STREAM_OPEN_STRIP_TEST_TAG).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals("two cards are in front of it", 2, below.size)
        below.sortedBy { it.top }.forEachIndexed { j, strip ->
            assertTrue("strip $j is under the open card: $strip vs $open", strip.top >= open.bottom - 1f)
        }
    }

    @Test
    fun aTapOnAStripBelowOpensThatCard() {
        show(more = 1)
        tapFront()
        composeRule.waitForIdle()
        invokeStep("Next card")
        invokeStep("Next card")
        val strip =
            composeRule.onAllNodesWithTag(STREAM_OPEN_STRIP_TEST_TAG).fetchSemanticsNodes().minByOrNull {
                it.boundsInRoot.top
            }!!
        composeRule.runOnUiThread { strip.config[SemanticsActions.OnClick].action?.invoke() }
        composeRule.waitForIdle()
        assertEquals(
            "one card is open",
            1,
            composeRule.onAllNodesWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNodes().size,
        )
    }

    /** The distance between the two strips farthest back now, in dp: the stack's pitch, easing to the tabs'. */
    private fun stripPitchNow(): Float? {
        val tops = cardSlots().map { it.top }.sorted()
        return if (tops.size >= 2) (tops[1] - tops[0]) / density else null
    }

    private fun pitches(frames: Int): List<Float> {
        val seen = mutableListOf(checkNotNull(stripPitchNow()))
        repeat(frames) {
            composeRule.mainClock.advanceTimeByFrame()
            seen += checkNotNull(stripPitchNow())
        }
        return seen
    }

    @Test
    fun theStripsEaseToSlimTabsOnTheSpringThatGrowsTheCardAndEaseBackWhenItCloses() {
        show()
        composeRule.mainClock.autoAdvance = false
        tapFront()
        val opening = pitches(80)
        val cards = (0..0).let { _ -> opening.size }
        assertEquals("a full strip before it opens", 64f, opening.first(), 0.6f)
        assertEquals("a slim tab once it is open", SlimTabs.PITCH_DP, opening.last(), 0.6f)
        opening.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame $i: the pitch went back up ($a to $b)\n$opening", b <= a + 0.6f)
        }
        val between = opening.count { it > SlimTabs.PITCH_DP + 2f && it < 64f - 2f }
        assertTrue("it jumped instead of easing: $between frames in between\n$opening", between >= 8)
        assertTrue("frames: $cards", cards > 0)
        composeRule.runOnUiThread { stream.dock.openCards.closeByPress() }
        val closing = pitches(80)
        assertEquals("a slim tab as it starts to close", SlimTabs.PITCH_DP, closing.first(), 0.6f)
        closing.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame $i: the pitch went back down ($a to $b)\n$closing", b >= a - 0.6f)
        }
        assertEquals("a full strip again", 64f, closing.last(), 0.6f)
        assertTrue("it eased back: $closing", closing.count { it > SlimTabs.PITCH_DP + 2f && it < 62f } >= 8)
    }

    @Test
    fun theTabsAndTheCardSettleOnTheSameFrameBecauseTheyShareOneSpring() {
        show()
        composeRule.mainClock.autoAdvance = false
        tapFront()
        val pitch = mutableListOf<Float>()
        val height = mutableListOf<Float>()
        repeat(81) {
            pitch += checkNotNull(stripPitchNow())
            height += boundsOf(STREAM_OPEN_CARD_TEST_TAG)?.height ?: 0f
            composeRule.mainClock.advanceTimeByFrame()
        }
        // Settled is a share of the whole travel (the same share for both): the tabs travel 36 dp, the card some 200.
        val pitchShare = 0.97f
        val pitchDone = pitch.indexOfFirst { it <= pitch.first() - (pitch.first() - SlimTabs.PITCH_DP) * pitchShare }
        val drawn = height.first { it > 0f }
        val heightDone = height.indexOfFirst { it > 0f && it >= drawn + (height.last() - drawn) * pitchShare }
        assertTrue("the tabs never got slim\n$pitch", pitchDone > 0)
        assertTrue("the card never settled\n$height", heightDone > 0)
        assertTrue(
            "one spring: 97 % of the way at frame $pitchDone for the tabs, $heightDone for the card",
            kotlin.math.abs(pitchDone - heightDone) <= 4,
        )
    }

    @Test
    fun theMorphFromStripsToTabsRecomposesNothingWhileItRuns() {
        show()
        composeRule.mainClock.autoAdvance = false
        tapFront()
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        val slots = probe.total("stack-slot:")
        val tabs = probe.total("slim-tab:")
        val cards = probe.total("open-card:")
        repeat(70) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("the stack's slots recomposed while the strips became tabs", slots, probe.total("stack-slot:"))
        assertEquals("the tabs recomposed while they morphed", tabs, probe.total("slim-tab:"))
        assertEquals("the open card recomposed while it grew", cards, probe.total("open-card:"))
        assertTrue("the tabs are there", tabs > 0)
    }

    private fun openStripsAndTabs(): List<Rect> = cardSlots().sortedBy { it.top }

    private fun openCardAboutTitle(title: String) =
        composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)) and hasText(title),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private fun tapAt(
        x: Float,
        y: Float,
    ) {
        composeRule.onRoot().performTouchInput { click(Offset(x, y)) }
        composeRule.waitForIdle()
    }

    @Test
    fun aTabIsOneLineWithAnIconAndItsTitleAndAButtonThatSaysSo() {
        show()
        tapFront()
        composeRule.waitForIdle()
        val tabs = openStripsAndTabs()
        assertEquals(2, tabs.size)
        tabs.forEach { assertEquals("a tab is 28 dp tall", SlimTabs.PITCH_DP, it.height / density, 1f) }
        val labels =
            composeRule.onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher("a tab") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("stream-stack-card:") == true
                },
            ).fetchSemanticsNodes().map {
                it.config[SemanticsProperties.ContentDescription].joinToString() to
                    it.config.getOrNull(SemanticsProperties.Role)
            }
        assertEquals(
            "each tab is a button, labelled with its title",
            setOf("Maps, open" to Role.Button, "Notes, open" to Role.Button),
            labels.toSet(),
        )
    }

    @Test
    fun aTapTwentyDpFromATabsCentreStillOpensItAndATapOnTheOpenCardsHeaderOpensNoNeighbour() {
        show()
        val rest = settledSlot()
        tapFront()
        composeRule.waitForIdle()
        val (top, near) = openStripsAndTabs()
        // 20 dp to the side of the tab nearest the card (its centre is 14 dp above the card, in the tab's own band).
        tapAt(near.center.x + 20f * density, near.center.y)
        assertTrue("the nearer tab, tapped 20 dp to the side, opens \"Notes\"", openCardAboutTitle("Notes"))
        // Back to the first card: the one that was open is a tab below now.
        invokeStep("Previous card")
        assertTrue("\"Sam\" is open again", openCardAboutTitle("Sam"))
        val tabs = openStripsAndTabs()
        // 20 dp above the centre of the farthest tab (the free side of it: nothing to share its room with).
        tapAt(tabs.first().center.x, tabs.first().center.y - 20f * density)
        assertTrue("the far tab, tapped 20 dp above its centre, opens \"Maps\"", openCardAboutTitle("Maps"))
        assertTrue("a tab was a slim band: $top", top.height / density < 40f)
    }

    @Test
    fun aTapOnTheOpenCardsHeaderOpensNoNeighbourItClosesTheCard() {
        show()
        val rest = settledSlot()
        tapFront()
        composeRule.waitForIdle()
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        // Where the nearest tab's 48 dp target would reach into the card if it could: just under the card's top edge.
        tapAt(card.center.x, card.top + 6f * density)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertNull("the card closed, no neighbour opened", boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        assertEquals("the stack is on \"Sam\" as before", rest, settledSlot())
    }

    @Test
    fun theFarthestTabBelowTheCardIsHitFromThirteenDpBeneathItsCentre() {
        show(more = 1)
        tapFront()
        composeRule.waitForIdle()
        invokeStep("Next card")
        invokeStep("Next card")
        val below =
            composeRule.onAllNodesWithTag(STREAM_OPEN_STRIP_TEST_TAG).fetchSemanticsNodes().map { it.boundsInRoot }
                .sortedBy { it.top }
        assertEquals(2, below.size)
        below.forEach { assertEquals("a tab is 28 dp tall", SlimTabs.PITCH_DP, it.height / density, 1f) }
        // The tab farthest below the card, at its own bottom edge (13 dp under its centre). It used to be hit from 20
        // dp beneath its centre, in the 16 dp the column left under the stack; the column now ends on the Dock body's
        // bottom edge, where the lowest element stands, so nothing of the Stream is beneath that tab to be hit.
        val last = below.last()
        val bodyBottom = checkNotNull(boundsOf(com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG)).bottom
        assertTrue("the tab stands on the column's bottom edge or above it", last.bottom <= bodyBottom + 1f)
        tapAt(last.center.x, last.center.y + 13f * density)
        assertEquals(
            "a card is open",
            1,
            composeRule.onAllNodesWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNodes().size,
        )
        assertTrue("it is the front card, \"Sam\"", openCardAboutTitle("Sam"))
    }

    @Test
    fun theOpenCardOffersNextAndPreviousCardOnlyWhenThereAreCardsToGoTo() {
        show()
        tapFront()
        composeRule.waitForIdle()
        assertEquals(listOf("Previous card", "Next card"), openCardActionLabels().filter { it.endsWith("card") })
    }

    private fun dragFromTheHeader(
        dyDp: Float,
        steps: Int,
        release: Boolean,
    ) {
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        val start = Offset(card.center.x, card.top + 24f * density)
        composeRule.onRoot().performTouchInput {
            down(start)
            repeat(steps) { moveBy(Offset(0f, dyDp * density / steps), delayMillis = 16) }
            if (release) up()
        }
    }

    @Test
    fun movingToTheNextCardIsOneMotionAndTheTwoBodiesAreNeverBothThere() {
        show(more = 1)
        composeRule.mainClock.autoAdvance = false
        tapFront()
        record(80)
        // A drag down from the header, held part way: the card that leaves and the one that arrives, both whole.
        dragFromTheHeader(dyDp = 100f, steps = 8, release = false)
        val frames = mutableListOf<List<Rect>>()
        repeat(6) {
            composeRule.mainClock.advanceTimeByFrame()
            frames += openCardBounds()
        }
        val during = frames.last()
        assertEquals("the leaving and the arriving card", 2, during.size)
        val host = checkNotNull(boundsOf("stream-column-host"))
        during.forEach {
            assertTrue(
                "a card is inside the column: $it in $host",
                it.top >= host.top - 1f && it.bottom <= host.bottom + 1f,
            )
        }
        // The arriving card is taller than the one leaving, which has shrunk toward its strip.
        assertTrue(during.maxOf { it.height } > during.minOf { it.height })
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNodes().size)
        // The one that left is a strip below the new open card.
        assertEquals(1, composeRule.onAllNodesWithTag(STREAM_OPEN_STRIP_TEST_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun theStripsAndTheCueRecomposeNothingWhileTheStackMoves() {
        show(more = 1)
        tapFront()
        composeRule.waitForIdle()
        invokeStep("Next card")
        composeRule.mainClock.autoAdvance = false
        dragFromTheHeader(dyDp = 24f, steps = 4, release = false)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val strips = probe.total("open-strip:")
        val cues = probe.total("handoff-cue")
        val cards = probe.total("open-card:")
        composeRule.onRoot().performTouchInput { moveBy(Offset(0f, 8f * density), delayMillis = 16) }
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("the strips recomposed while dragging", strips, probe.total("open-strip:"))
        assertEquals("the cue recomposed while dragging", cues, probe.total("handoff-cue"))
        assertEquals("the open cards recomposed while dragging", cards, probe.total("open-card:"))
        composeRule.onRoot().performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    private fun openCardBounds(): List<Rect> =
        composeRule.onAllNodesWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun chipNodes() =
        composeRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("a chip") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("quick-action:") == true
            },
        ).fetchSemanticsNodes()

    @Test
    fun chipsThatCanFitIfTheirLabelsGiveUpTheirEndsAreAllWholeInsideTheStrip() {
        show(chatShortcuts = longShortcutsOf(chat, 3))
        composeRule.waitForIdle()
        val row = checkNotNull(boundsOf(com.riffle.app.launcher.QUICK_ACTIONS_ROW_TEST_TAG))
        val chips = chipNodes()
        assertEquals(3, chips.size)
        chips.forEach { node ->
            val right = node.positionInRoot.x + node.size.width
            assertTrue("a chip runs past the strip: $right of ${row.right}", right <= row.right + 1f)
            assertEquals(
                "a chip is faded though it fits",
                1f,
                node.config.getOrNull(com.riffle.app.launcher.DockEdgeFadeAlphaKey) ?: 1f,
                0f,
            )
        }
    }

    @Test
    fun chipsThatCannotFitScrollAndAChipAtTheEdgeIsEitherWholeOrNearlyGoneNeverHalfAtFullStrength() {
        show(chatShortcuts = longShortcutsOf(chat, 5))
        composeRule.waitForIdle()
        val chips = chipNodes()
        assertEquals(5, chips.size)
        val alphas = chips.map { it.config.getOrNull(com.riffle.app.launcher.DockEdgeFadeAlphaKey) ?: 1f }
        assertTrue("the first chip is whole: $alphas", alphas.first() == 1f)
        assertTrue("a chip is cut at some strength: $alphas", alphas.all { it == 1f || it <= 0.25f })
        assertTrue("some chip is out of view: $alphas", alphas.any { it < 1f })
    }

    /** The actions are on the card's own (merged) node, under the tagged glass. */
    private fun openCardNodes() =
        composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)) or hasTestTag(STREAM_OPEN_CARD_TEST_TAG),
        )
            .fetchSemanticsNodes()

    private fun openCardActionLabels(): List<String> =
        openCardNodes().flatMap {
                node ->
            node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        }

    private fun invokeStep(label: String) {
        val action =
            openCardNodes().firstNotNullOf { node ->
                node.config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == label }
            }
        composeRule.runOnUiThread { action.action() }
        composeRule.waitForIdle()
    }

    /** The strips behind the open card: the front card's own slot is covered by it and leaves the tree. */
    private fun otherCards(): List<Rect> = cardSlots().sortedBy { it.top }

    private companion object {
        /** The edges are whole pixels: a pixel of rounding is not a move. */
        const val TOL = 1.5f
    }
}
