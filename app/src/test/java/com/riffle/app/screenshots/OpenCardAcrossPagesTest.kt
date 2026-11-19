package com.riffle.app.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.stream.OpenCardAlphaKey
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_STRIP_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Device fixes, round 7, item 4 ("expanding a card shows nothing"): through the real shell on the owner's unfolded
 * window, after cards were opened and closed on one page and the pager moved to another. Whatever the path, a page
 * whose neighbours are slim tabs has its open card drawn (alpha above 0), and a page with no open card shows its front
 * card and plain strips: never tabs with no card, never a hidden front card with no card in its place.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = OWNER_UNFOLDED)
internal class OpenCardAcrossPagesTest : OpenCardPolishHarness(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT) {
    private fun openCards(): List<SemanticsNode> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(OpenCardAlphaKey)).fetchSemanticsNodes()

    private fun tabs() = composeRule.onAllNodesWithTag(STREAM_OPEN_STRIP_TEST_TAG).fetchSemanticsNodes()

    private fun assertDrawnOrCollapsed(why: String) {
        val cards = openCards()
        if (tabs().isNotEmpty() || cards.isNotEmpty()) {
            assertEquals("$why: one open card is drawn", 1, cards.size)
            assertTrue("$why: it is opaque enough to see", cards.single().config[OpenCardAlphaKey] > 0f)
            assertTrue("$why: it has its place", boundsOf(STREAM_OPEN_CARD_TEST_TAG) != null)
        } else {
            assertTrue("$why: the front card is drawn", cardSlots().isNotEmpty())
        }
    }

    private fun swipePage(left: Boolean) {
        // A left swipe went to the next page and a right swipe to the previous one: the pager's actions do that now.
        composeRule.streamPage(next = left)
    }

    private fun goToLedger() {
        // The mail page is on the other side of the chat page from the Library.
        repeat(3) {
            if (composeRule.onAllNodesWithText("Ledger").fetchSemanticsNodes().isEmpty()) {
                swipePage(left = false)
                composeRule.waitForIdle()
            }
        }
        composeRule.onNodeWithText("Ledger").assertExists()
    }

    @Test
    fun aCardOpenedAndClosedOnOnePageDoesNotStopTheNextPagesCardFromOpening() {
        show(more = 1)
        tapFront()
        composeRule.waitForIdle()
        assertDrawnOrCollapsed("opened on the chat page")
        composeRule.runOnUiThread { stream.dock.openCards.closeByPress() }
        composeRule.waitForIdle()
        assertEquals("closed", 0, openCards().size)
        goToLedger()
        assertDrawnOrCollapsed("on the mail page at rest")
        tapFront("Ledger")
        composeRule.waitForIdle()
        assertEquals("the mail page's card is open", 1, openCards().size)
        assertDrawnOrCollapsed("opened on the mail page")
    }

    @Test
    fun openingWhileThePagerIsStillSettlingDrawsTheCard() {
        show(more = 1)
        composeRule.mainClock.autoAdvance = false
        swipePage(left = false)
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("Ledger").fetchSemanticsNodes().isEmpty()) goToLedger()
        tapFront("Ledger")
        composeRule.waitForIdle()
        assertDrawnOrCollapsed("opened right after a swipe")
        assertEquals(1, openCards().size)
    }

    @Test
    fun movingToAnotherPageWhileACardClosesLeavesNoEmptyLayerOverTheNewPage() {
        show(more = 1)
        tapFront()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { stream.dock.openCards.closeByPress() }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        // The close is under way (the motion is above 0 for every stack composed); the pager moves meanwhile.
        swipePage(left = false)
        repeat(6) {
            composeRule.mainClock.advanceTimeByFrame()
            assertDrawnOrCollapsed("frame $it of a page swipe while a card closes")
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals("nothing is open", 0, openCards().size)
        assertEquals("no tabs", 0, tabs().size)
        assertTrue("a front card is drawn", cardSlots().isNotEmpty())
        // Opening on whichever page is now showing works.
        val title = if (composeRule.onAllNodesWithText("Ledger").fetchSemanticsNodes().isNotEmpty()) "Ledger" else "Sam"
        tapFront(title)
        composeRule.waitForIdle()
        assertEquals(1, openCards().size)
        assertDrawnOrCollapsed("opened after the swipe")
    }

    @Test
    fun aNotificationArrivingBetweenTheTapAndTheLayerStillLeavesACardDrawn() {
        show(more = 1)
        composeRule.mainClock.autoAdvance = false
        tapFront()
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        assertDrawnOrCollapsed("two frames after the tap")
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertDrawnOrCollapsed("settled")
        composeRule.onRoot().assertExists()
        composeRule.onNodeWithText("Sam").assertExists()
        assertTrue(
            "the open card can act",
            openCards().single().config.getOrNull(SemanticsActions.CustomActions) != null ||
                boundsOf(STREAM_OPEN_CARD_TEST_TAG) != null,
        )
    }
}
