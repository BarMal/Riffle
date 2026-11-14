package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.notifications.NotificationTitleRule.Input
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTitleRuleTest {
    @Test
    fun theNotificationsOwnTitleIsItsTitle() {
        val resolved =
            NotificationTitleRule.resolve(
                Input(title = "Pilot has a question", text = "Tap to answer", appLabel = "Pilot"),
            )
        assertEquals("Pilot has a question", resolved.title)
        assertEquals("Tap to answer", resolved.text)
    }

    @Test
    fun aTitleThatIsOnlyTheAppsNameGivesWayToTheBigTitle() {
        val resolved =
            NotificationTitleRule.resolve(
                Input(title = "Pilot", titleBig = "Pilot has a question", appLabel = "Pilot"),
            )
        assertEquals("Pilot has a question", resolved.title)
    }

    @Test
    fun aBigTitleIsUsedWhenThereIsNoTitle() {
        assertEquals(
            "Trip to Paris",
            NotificationTitleRule.resolve(Input(titleBig = "Trip to Paris", appLabel = "Maps")).title,
        )
    }

    @Test
    fun aMessagingStyleWhosePersonIsTheAppItselfTakesItsMessageAsTheTitle() {
        val resolved =
            NotificationTitleRule.resolve(
                Input(
                    title = "Pilot",
                    messagingStyle = true,
                    lastSender = "Pilot",
                    text = "Pilot has a question",
                    appLabel = "Pilot",
                ),
            )
        assertEquals("Pilot has a question", resolved.title)
        // Not said twice.
        assertEquals("", resolved.text)
    }

    @Test
    fun aRealConversationKeepsThePersonsName() {
        val resolved =
            NotificationTitleRule.resolve(
                Input(
                    title = "Maya",
                    messagingStyle = true,
                    lastSender = "Maya",
                    text = "On my way",
                    appLabel = "WhatsApp",
                ),
            )
        assertEquals("Maya", resolved.title)
        assertEquals("On my way", resolved.text)
    }

    @Test
    fun aGroupConversationKeepsTheGroupsName() {
        val resolved =
            NotificationTitleRule.resolve(
                Input(
                    title = "Family: Maya",
                    conversationTitle = "Family",
                    messagingStyle = true,
                    lastSender = "Maya",
                    text = "Dinner at 7",
                    appLabel = "Signal",
                ),
            )
        assertEquals("Family", resolved.title)
    }

    @Test
    fun aMessagingStyleWithNoTitleTakesTheNewestSender() {
        val resolved =
            NotificationTitleRule.resolve(
                Input(messagingStyle = true, lastSender = "Sam", appLabel = "Signal"),
            )
        assertEquals("Sam", resolved.title)
    }

    @Test
    fun noTitleAtAllIsBlankSoTheCardFallsBackToTheAppsName() {
        assertEquals("", NotificationTitleRule.resolve(Input(appLabel = "Citymapper")).title)
        // The text is not promoted when the app gave no title: the app's name leads and the text stays the text.
        val withText = NotificationTitleRule.resolve(Input(text = "Download complete", appLabel = "Files"))
        assertEquals("", withText.title)
        assertEquals("Download complete", withText.text)
    }

    @Test
    fun anAppNameTitleWithALongTextStaysTheAppsName() {
        val long = "x".repeat(NotificationTitleRule.MAX_PROMOTED_TITLE_LENGTH + 1)
        val resolved = NotificationTitleRule.resolve(Input(title = "Mail", text = long, appLabel = "Mail"))
        assertEquals("Mail", resolved.title)
        assertEquals(long, resolved.text)
    }

    @Test
    fun onlyTheFirstLineOfTheTextIsPromotedAndTheWholeTextStays() {
        val resolved =
            NotificationTitleRule.resolve(
                Input(title = "Mail", text = "Two new\nfrom Sam", appLabel = "mail"),
            )
        assertEquals("Two new", resolved.title)
        assertEquals("Two new\nfrom Sam", resolved.text)
    }
}
