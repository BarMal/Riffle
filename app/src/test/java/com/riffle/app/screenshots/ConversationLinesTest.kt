package com.riffle.app.screenshots

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Device fixes, round 7, item 3, through the real shell: a group conversation whose app names the group as the sender
 * of every message (the owner's "Old Mother Boggins' Ham Hamper") said the group's title on every line of the browse
 * card and of the open card. The card's own title says it once; the lines are the messages.
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = OWNER_UNFOLDED)
internal class ConversationLinesTest : OpenCardPolishHarness(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT) {
    private val group = "Book club"

    private fun thread(sender: (Int) -> String) =
        (1..3).map { i ->
            LauncherNotificationMessage(
                sender = sender(i),
                text = "Message $i from the club.",
                timestampEpochMillis = StreamFixtures.NOW - (4 - i) * 60_000L,
            )
        }

    private fun showGroup(sender: (Int) -> String): List<LauncherNotification> {
        val chats =
            listOf(
                note(1, group, "Message 3 from the club.", messages = thread(sender)),
                note(2, "Sam", "Short one."),
            )
        show(chatNotifications = chats, waitFor = group)
        return chats
    }

    private fun texts(inside: String? = null): List<String> =
        composeRule.onAllNodes(
            SemanticsMatcher("any text") { it.config.getOrNull(SemanticsProperties.Text) != null } and
                (if (inside == null) SemanticsMatcher("anywhere") { true } else hasAnyAncestor(hasTestTag(inside))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    @Test
    fun aGroupThatNamesItselfAsEverySenderShowsTheMessagesAloneOnTheBrowseCard() {
        showGroup { group }
        val all = texts()
        val prefixed = all.filter { it.startsWith("$group:") }
        assertTrue("lines still prefixed with the group's own title: $prefixed (all: $all)", prefixed.isEmpty())
        assertEquals("the title is the card's header only: $all", 1, all.count { it == group })
        assertTrue("the messages are there: $all", all.any { it == "Message 1 from the club." })
    }

    @Test
    fun theOpenCardsThreadIsTheSame() {
        showGroup { group }
        tapFront(group)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val open = texts(STREAM_OPEN_CARD_TEST_TAG)
        val prefixed = open.filter { it.startsWith("$group:") }
        assertTrue("open card lines prefixed with the group's own title: $prefixed (all: $open)", prefixed.isEmpty())
        assertTrue("the messages are there: $open", open.any { it == "Message 1 from the club." })
    }

    @Test
    fun peopleInAGroupAreNamedOnTheirLines() {
        showGroup { i -> if (i % 2 == 0) "Jo" else "Alex" }
        val all = texts()
        assertTrue("the people are named: $all", all.contains("Alex: Message 1 from the club."))
        assertTrue("the people are named: $all", all.contains("Jo: Message 2 from the club."))
    }
}
