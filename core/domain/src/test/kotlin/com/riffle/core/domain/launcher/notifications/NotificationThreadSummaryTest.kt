package com.riffle.core.domain.launcher.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationThreadSummaryTest {
    @Test
    fun aOneToOneThreadIsTitledByTheSenderAndDoesNotRepeatThemOnEveryLine() {
        val messages =
            listOf(
                message(sender = "Ekin", text = "on my way", at = 1),
                message(sender = "Ekin", text = "five minutes", at = 2),
            )

        assertEquals("Ekin", NotificationThreadSummary.combinedTitle(messages))
        assertEquals(
            "on my way\nfive minutes",
            NotificationThreadSummary.combinedText(messages, newestFirst = false),
        )
    }

    @Test
    fun aGroupThreadHasNoSingleSenderToTitleWithSoEveryLineIsAttributed() {
        // With several people talking there is no one name for the card, so the conversation's own
        // title has to serve -- which means the body is the only place the senders can appear.
        val messages =
            listOf(
                message(sender = "Ekin", text = "on my way", at = 1),
                message(sender = "Sam", text = "same", at = 2),
            )

        assertNull(NotificationThreadSummary.combinedTitle(messages))
        assertEquals(
            "Ekin: on my way\nSam: same",
            NotificationThreadSummary.combinedText(messages, newestFirst = false),
        )
    }

    @Test
    fun newestFirstReversesTheConversationWithoutRelabellingIt() {
        val messages =
            listOf(
                message(sender = "Ekin", text = "on my way", at = 1),
                message(sender = "Ekin", text = "five minutes", at = 2),
            )

        assertEquals(
            "five minutes\non my way",
            NotificationThreadSummary.combinedText(messages, newestFirst = true),
        )
    }

    @Test
    fun messagesAreOrderedByTheirOwnTimestampsRatherThanTrustingArrivalOrder() {
        // The platform hands these over in whatever order the posting app assembled them, which is
        // not reliably chronological; ordering by timestamp is what makes the card read as a
        // conversation either way round.
        val messages =
            listOf(
                message(sender = "Ekin", text = "second", at = 20),
                message(sender = "Ekin", text = "first", at = 10),
            )

        assertEquals("first\nsecond", NotificationThreadSummary.combinedText(messages, newestFirst = false))
    }

    @Test
    fun blankMessagesAreDroppedRatherThanLeftAsEmptyLines() {
        // An empty message still costs a line of card height otherwise, which is the opposite of
        // what combining is for.
        val messages =
            listOf(
                message(sender = "Ekin", text = "on my way", at = 1),
                message(sender = "Ekin", text = "   ", at = 2),
            )

        assertEquals("on my way", NotificationThreadSummary.combinedText(messages, newestFirst = false))
    }

    @Test
    fun aThreadWithNothingToSayCombinesToAnEmptyBody() {
        assertEquals("", NotificationThreadSummary.combinedText(emptyList(), newestFirst = false))
    }

    @Test
    fun aBlankSenderNeverBecomesTheCardsTitle() {
        val messages = listOf(message(sender = "  ", text = "on my way", at = 1))

        assertNull(NotificationThreadSummary.combinedTitle(messages))
    }

    @Test
    fun theNewestMessageDecidesWhereACombinedCardSortsInTheStack() {
        val messages =
            listOf(
                message(sender = "Ekin", text = "first", at = 10),
                message(sender = "Ekin", text = "second", at = 30),
            )

        assertEquals(
            30L,
            NotificationThreadSummary.latestActivityEpochMillis(messages, fallbackEpochMillis = 5L),
        )
    }

    @Test
    fun aThreadWithoutUsableTimestampsSortsByTheNotificationsOwnPostedTime() {
        val messages = listOf(message(sender = "Ekin", text = "on my way", at = 0))

        assertEquals(
            99L,
            NotificationThreadSummary.latestActivityEpochMillis(messages, fallbackEpochMillis = 99L),
        )
    }

    private fun message(
        sender: String,
        text: String,
        at: Long,
    ) = LauncherNotificationMessage(sender = sender, text = text, timestampEpochMillis = at)
}
