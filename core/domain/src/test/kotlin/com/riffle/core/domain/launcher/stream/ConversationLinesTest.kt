package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import com.riffle.core.domain.launcher.notifications.NotificationThreadSummary
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemInput
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Device fixes, round 7, item 3: a group conversation's lines were each prefixed with the conversation's own title
 * ("Book club: ..."), so the title was said four times and the message was truncated away. The rule: never say a
 * sender that is the card's title or the conversation's title; in a group say the person; in a one-to-one say nothing.
 */
class ConversationLinesTest {
    private fun message(
        sender: String?,
        text: String,
        at: Long = 1L,
    ) = NotificationMessage(sender = sender, text = text, timestampEpochMillis = at)

    private fun written(
        thread: List<NotificationMessage>,
        vararg titles: String?,
    ) = ConversationLines.resolve(thread, titles.toList()).map { it.written("You") }

    private fun chatContent(
        title: String,
        messages: List<LauncherNotificationMessage>,
    ): NotificationContent {
        val notification =
            LauncherNotification(
                key = LauncherNotificationKey("k1"),
                packageName = AppPackageName("chat"),
                canDismiss = true,
                title = title,
                text = "latest",
                messages = messages,
                postedAtEpochMillis = NOW - 10,
            )
        val item =
            NotificationItemMapper().notificationItems(
                NotificationItemInput(
                    notifications = listOf(notification),
                    nowEpochMillis = NOW,
                    profileContentVisibility = mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
                ),
            ).single()
        return assertIs<NotificationContent>(StreamContentMapper.map(item))
    }

    @Test
    fun aGroupWhoseAppNamesTheGroupAsEverySenderShowsTheTextAlone() {
        val thread =
            listOf(message("Book club", "one", 1), message("Book club", "two", 2), message("Book club", "three", 3))

        assertEquals(listOf("one", "two", "three"), written(thread, "Book club"))
    }

    @Test
    fun theComparisonIgnoresCaseAndSurroundingSpace() {
        val thread = listOf(message("  BOOK CLUB ", "one"), message("book club", "two"))

        assertEquals(listOf("one", "two"), written(thread, "Book Club"))
    }

    @Test
    fun anyOfTheTitlesCounts() {
        // The card's title and the conversation's can differ (the app label stands in for a missing title).
        val thread = listOf(message("Old Mother Boggins' Ham Hamper", "NO WAY"))

        assertEquals(listOf("NO WAY"), written(thread, "Hamper chat", "Old Mother Boggins' Ham Hamper"))
    }

    @Test
    fun inAGroupEachLineNamesThePersonWhenThePlatformGivesOne() {
        val thread = listOf(message("Alex", "one", 1), message("Jo", "two", 2), message("Alex", "three", 3))

        assertEquals(listOf("Alex: one", "Jo: two", "Alex: three"), written(thread, "Book club"))
    }

    @Test
    fun inAGroupALineFromTheGroupsOwnNameStaysAloneBesideNamedPeople() {
        val thread = listOf(message("Alex", "one", 1), message("Book club", "two", 2), message("Jo", "three", 3))

        assertEquals(listOf("Alex: one", "two", "Jo: three"), written(thread, "Book club"))
    }

    @Test
    fun aGroupWithOneSpeakerStillNamesThemWhenTheirNameIsNotTheTitle() {
        val thread = listOf(message("Alex", "one", 1), message("Alex", "two", 2))

        assertEquals(listOf("Alex: one", "Alex: two"), written(thread, "Book club"))
    }

    @Test
    fun aOneToOneChatShowsTheTextAloneAndTheUsersOwnLinesStayMarked() {
        val thread = listOf(message("Sam", "one", 1), message(null, "two", 2), message("Sam", "three", 3))

        assertEquals(listOf("one", "You: two", "three"), written(thread, "Sam"))
    }

    @Test
    fun aOneToOneChatWhoseTitleIsTheFullNameOfTheOnlySpeakerShowsTheTextAlone() {
        val thread = listOf(message("Sam", "one", 1), message("Sam", "two", 2))

        assertEquals(listOf("one", "two"), written(thread, "Sam Smith"))
    }

    @Test
    fun aBlankTitleSaysNothingAboutTheSenders() {
        val thread = listOf(message("Sam", "one"))

        assertEquals(listOf("Sam: one"), written(thread, null, "  "))
    }

    @Test
    fun theContentOfANotificationWritesItsThreadWithTheRule() {
        val content =
            chatContent(
                title = "Book club",
                messages =
                    listOf(
                        LauncherNotificationMessage("Book club", "one", 1),
                        LauncherNotificationMessage("Book club", "two", 2),
                    ),
            )

        val body = content.body as NotificationBody.Thread
        assertEquals(listOf("one", "two"), body.messages.map { it.written("You") })
        // The browse card's blocks follow the same lines.
        assertEquals(listOf("one", "two"), NotificationBrowseText.blocks(content).map { it.written("You") })
    }

    @Test
    fun aCombinedBodyDoesNotPrefixASenderThatIsTheTitle() {
        val messages =
            listOf(
                LauncherNotificationMessage("Book club", "one", 1),
                LauncherNotificationMessage("Book club", "two", 2),
            )

        assertEquals(
            "one\ntwo",
            NotificationThreadSummary.combinedText(messages, newestFirst = false, title = "Book club"),
        )
    }
}
