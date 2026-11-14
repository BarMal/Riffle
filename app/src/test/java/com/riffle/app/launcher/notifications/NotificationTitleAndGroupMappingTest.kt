package com.riffle.app.launcher.notifications

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.riffle.core.domain.launcher.apps.AppProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the listener's mapper makes of real platform notifications (device fixes, round 6, item 1): the notification's
 * own title wins over the app's name, a real conversation keeps its person or group, a notification with no title and
 * no text still maps (blank: the card falls back to the app's name, never to a hidden state), and a group's summary
 * with children is not listed while every child is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [com.riffle.app.screenshots.ScreenshotDevices.SDK])
class NotificationTitleAndGroupMappingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun mapper(appLabel: String?) =
        StatusBarNotificationMapper(profileIdForUser = { AppProfile.personal().id }, appLabelFor = { appLabel })

    private fun posted(
        id: Int,
        notification: Notification,
        packageName: String = "com.example.app",
    ): StatusBarNotification =
        StatusBarNotification(
            packageName,
            packageName,
            id,
            null,
            1000,
            1000,
            0,
            notification,
            Process.myUserHandle(),
            1_000L,
        )

    private fun builder() = Notification.Builder(context, "channel").setSmallIcon(android.R.drawable.ic_menu_send)

    @Test
    fun theNotificationsOwnTitleIsKeptWhenTheAppIsCalledSomethingElse() {
        val mapped =
            mapper("Claude").map(
                posted(1, builder().setContentTitle("Claude has a question").setContentText("Tap to answer").build()),
            )
        assertEquals("Claude has a question", mapped.title)
        assertEquals("Tap to answer", mapped.text)
    }

    @Test
    fun aBigTitleWinsOverATitleThatIsOnlyTheAppsName() {
        val notification =
            builder()
                .setContentTitle("Claude")
                .setStyle(
                    Notification.BigTextStyle().setBigContentTitle("Claude has a question").bigText("Which file?"),
                )
                .build()
        assertEquals("Claude has a question", mapper("Claude").map(posted(1, notification)).title)
    }

    @Test
    fun aMessagingStyleFromTheAppItselfTakesItsMessageAndARealPersonKeepsTheirName() {
        fun messaging(
            sender: String,
            text: String,
            group: String? = null,
        ): Notification {
            val person = Person.Builder().setName(sender).build()
            val style = Notification.MessagingStyle(Person.Builder().setName("Me").build())
            style.addMessage(text, 10L, person)
            group?.let {
                style.conversationTitle = it
                style.isGroupConversation = true
            }
            return builder().setStyle(style).build()
        }
        assertEquals(
            "Claude has a question",
            mapper("Claude").map(posted(1, messaging("Claude", "Claude has a question"))).title,
        )
        assertEquals("Maya", mapper("WhatsApp").map(posted(2, messaging("Maya", "On my way"))).title)
        assertEquals(
            "Family",
            mapper("Signal").map(posted(3, messaging("Maya", "Dinner at 7", group = "Family"))).title,
        )
    }

    @Test
    fun aNotificationWithNoTitleAndNoTextStillMapsWithABlankTitle() {
        val mapped = mapper("Citymapper").map(posted(1, builder().setOngoing(true).build()))
        assertEquals("", mapped.title)
        assertEquals("", mapped.text)
    }

    @Test
    fun theLockScreenVisibilityOfANotificationHidesNothing() {
        val secret =
            builder().setContentTitle(
                "Code 1234",
            ).setContentText("Your code").setVisibility(Notification.VISIBILITY_SECRET)
                .build()
        val mapped = mapper("Bank").map(posted(1, secret))
        assertEquals("Code 1234", mapped.title)
        assertEquals("Your code", mapped.text)
    }

    @Test
    fun aGroupSummaryWithChildrenIsNotListedAndEachChildIs() {
        val summary = posted(1, builder().setContentTitle("Chat").setGroup("all").setGroupSummary(true).build())
        val maya = posted(2, builder().setContentTitle("Maya").setGroup("all").build())
        val sam = posted(3, builder().setContentTitle("Sam").setGroup("all").build())
        val other = posted(4, builder().setContentTitle("Other").build(), packageName = "com.example.other")
        val listed = mapper(null).listed(arrayOf(summary, maya, sam, other))
        assertEquals(listOf(maya.key, sam.key, other.key), listed.map { it.key })
    }

    @Test
    fun aGroupSummaryWithNoChildrenIsListed() {
        val summary = posted(1, builder().setContentTitle("3 new mails").setGroup("all").setGroupSummary(true).build())
        assertEquals(listOf(summary.key), mapper(null).listed(arrayOf(summary)).map { it.key })
    }
}
