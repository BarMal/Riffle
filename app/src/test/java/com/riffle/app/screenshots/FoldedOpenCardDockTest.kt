package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.riffle.app.launcher.DOCK_CARD_ACTIONS_TEST_TAG
import com.riffle.app.launcher.GridFadeAlphaKey
import com.riffle.app.launcher.HOME_DOCK_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_STRIP_TEST_TAG
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.AppsBehindCards
import com.riffle.core.domain.launcher.stream.NotificationOwnAction
import com.riffle.core.domain.launcher.stream.StreamGridFade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Device fixes, round 7, item 6: the folded phone (Dock along the bottom, the card's actions in the Dock), four chats,
 * the front one with a one-line message, a reply field and three actions of its own. The open card is as tall as its
 * content, its tabs below stand against it and above the Dock where the collapsed stack stood, the Dock's text actions
 * are one enabled row, and no app of Home shows at full strength under the Dock's extended body or the card's room.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
internal class FoldedOpenCardDockTest : OpenCardPolishHarness(HomeLayoutDeviceClass.PHONE, null) {
    private val message = "No but I am happy to go whenever"

    private fun open(
        appsBehind: AppsBehindCards? = null,
        tabsBelow: Boolean = false,
    ): Pair<Rect, Float> {
        show(
            sam = message,
            chatNotifications = null,
            more = 1,
            appsBehind = appsBehind,
            ownActions = { key ->
                if (key.isFront()) {
                    listOf(NotificationOwnAction("p:1", "Mark as read"), NotificationOwnAction("p:2", "Mute"))
                } else {
                    emptyList()
                }
            },
        )
        composeRule.waitForIdle()
        val rest = settledSlot()
        val dockTop = checkNotNull(boundsOf(HOME_DOCK_TEST_TAG)).top
        tapFront()
        composeRule.waitForIdle()
        if (tabsBelow) {
            // One card forward: the card in front of it shows as a tab below the open one.
            val step =
                composeRule.onAllNodes(
                    hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)) or hasTestTag(STREAM_OPEN_CARD_TEST_TAG),
                ).fetchSemanticsNodes().firstNotNullOf { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions)
                        ?.firstOrNull { it.label == "Next card" }
                }
            composeRule.runOnUiThread { step.action() }
            composeRule.waitForIdle()
        }
        return rest to dockTop
    }

    private fun LauncherNotificationKey.isFront() = value.endsWith("|1")

    @Test
    fun theOpenCardIsAsTallAsItsContentWithNoDeadBandUnderTheMessage() {
        open()
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        val text = composeRule.onNodeWithText(message).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "no dead band: the card ends ${(card.bottom - text.bottom) / density} dp under its message",
            card.bottom - text.bottom <= 32f * density,
        )
    }

    @Test
    fun theTabsBelowStandAgainstTheCardAndTheStackSitsAboveTheDockWhereItDidAtRest() {
        val (rest, dockTopAtRest) = open(tabsBelow = true)
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        val tabs =
            composeRule.onAllNodesWithTag(STREAM_OPEN_STRIP_TEST_TAG).fetchSemanticsNodes()
                .map { it.boundsInRoot }.sortedBy { it.top }
        assertTrue("a tab is below the card", tabs.isNotEmpty())
        assertEquals("the first tab stands against the card", card.bottom, tabs.first().top, 1.5f * density)
        val dockTop = checkNotNull(boundsOf(HOME_DOCK_TEST_TAG)).top
        val gapAtRest = dockTopAtRest - rest.bottom
        assertEquals(
            "the stack stands the same gap above the Dock as at rest (${gapAtRest / density} dp)",
            gapAtRest,
            dockTop - tabs.last().bottom,
            2f * density,
        )
    }

    @Test
    fun theDocksTextActionsAreOneEnabledRow() {
        open()
        val nodes = listOf("Open", "Mark as read", "Mute").map { composeRule.onNodeWithText(it).fetchSemanticsNode() }
        val tops = nodes.map { it.boundsInRoot.top }
        assertEquals("one row: $tops", tops.min(), tops.max(), 2f * density)
        nodes.forEach { assertFalse("enabled", it.config.contains(SemanticsProperties.Disabled)) }
    }

    private fun cellsWith(): List<Pair<Rect, Float>> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(GridFadeAlphaKey)).fetchSemanticsNodes()
            .map { it.boundsInRoot to it.config[GridFadeAlphaKey] }

    private fun assertNothingShowsFullUnderTheDockOrTheCard(setting: AppsBehindCards) {
        open(setting)
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG))
        val row = checkNotNull(boundsOf(DOCK_CARD_ACTIONS_TEST_TAG))
        val right = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        // From the open card's top down through the card, its tabs, the room under them and the Dock's body.
        val region = Rect(0f, card.top + 24f * density, right, Float.MAX_VALUE)
        val cells = cellsWith()
        val inRegion = cells.filter { region.contains(it.first.center) }
        assertTrue("$setting: cells are under the card's room (${cells.size}, row ${row.top})", inRegion.isNotEmpty())
        val limit = if (setting == AppsBehindCards.BEHIND_AND_AROUND) StreamGridFade.PEEK_ALPHA + 0.02f else 0.02f
        inRegion.forEach { (rect, alpha) ->
            assertTrue("$setting: a cell at ${rect.center} under the card or the Dock is at $alpha", alpha <= limit)
        }
    }

    @Test
    fun withAppsBehindAndAroundNothingShowsFullUnderTheDockOrTheCard() =
        assertNothingShowsFullUnderTheDockOrTheCard(AppsBehindCards.BEHIND_AND_AROUND)

    @Test
    fun withAppsAroundTheCardsNothingShowsUnderTheDockOrTheCard() =
        assertNothingShowsFullUnderTheDockOrTheCard(AppsBehindCards.AROUND)
}
