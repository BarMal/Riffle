package com.riffle.app.launcher.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.stream.NotificationOwnAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the listener captures of a notification's own action buttons (device fixes, round 5, item 3): ids and titles in
 * memory, filtered (the inline reply, a second reply, a delete and an action that needs input are left out), capped at
 * three, in the notification's order; and sending one by id through the registry that holds the intents, with the
 * registry's handling of a notification that has gone and of an intent that was cancelled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [com.riffle.app.screenshots.ScreenshotDevices.SDK])
class NotificationOwnActionsCaptureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = "0|com.riffle.chat|1|null|1000"

    @After
    fun clear() {
        AndroidNotificationStageActionGateway.clear()
    }

    private fun intentFor(name: String): PendingIntent =
        PendingIntent.getBroadcast(context, name.hashCode(), Intent(name), PendingIntent.FLAG_IMMUTABLE)

    private fun action(
        title: String?,
        name: String = "act.${title.orEmpty()}",
        semantic: Int? = null,
        reply: Boolean = false,
        withIntent: Boolean = true,
    ): Notification.Action {
        val builder =
            Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_menu_send),
                title,
                if (withIntent) intentFor(name) else null,
            )
        semantic?.let(builder::setSemanticAction)
        if (reply) builder.addRemoteInput(RemoteInput.Builder("reply").build())
        return builder.build()
    }

    private fun capture(vararg actions: Notification.Action) = ownActionsOf(arrayOf(*actions))

    @Test
    fun theInlineReplyIsSkippedAndTheOthersKeepTheNotificationsOrder() {
        val shown =
            capture(
                action("Reply", reply = true, semantic = Notification.Action.SEMANTIC_ACTION_REPLY),
                action("Mark as read", semantic = Notification.Action.SEMANTIC_ACTION_MARK_AS_READ),
                action("Archive", semantic = Notification.Action.SEMANTIC_ACTION_ARCHIVE),
            )
        assertEquals(
            listOf(NotificationOwnAction("provider:1", "Mark as read"), NotificationOwnAction("provider:2", "Archive")),
            shown,
        )
    }

    @Test
    fun aSecondReplyAndADeleteAreSkippedButThumbsMuteAndCallStay() {
        val shown =
            capture(
                action("Quick reply", semantic = Notification.Action.SEMANTIC_ACTION_REPLY),
                action("Delete", semantic = Notification.Action.SEMANTIC_ACTION_DELETE),
                action("Thumbs up", semantic = Notification.Action.SEMANTIC_ACTION_THUMBS_UP),
                action("Mute", semantic = Notification.Action.SEMANTIC_ACTION_MUTE),
                action("Call", semantic = Notification.Action.SEMANTIC_ACTION_CALL),
            )
        assertEquals(listOf("Thumbs up", "Mute", "Call"), shown.map { it.title })
        assertEquals(listOf("provider:2", "provider:3", "provider:4"), shown.map { it.id })
    }

    @Test
    fun atMostThreeAreCapturedInOrderAndAnActionWithoutAnIntentIsNotListed() {
        val shown =
            capture(
                action("No intent", withIntent = false),
                action("A"),
                action("B"),
                action("C"),
                action("D"),
            )
        assertEquals(listOf("A", "B", "C"), shown.map { it.title })
        // The ids are the registry's own, by position, so sending one finds the right intent.
        assertEquals(listOf("provider:1", "provider:2", "provider:3"), shown.map { it.id })
    }

    @Test
    fun anActionThatAsksForTextIsSkippedAndNoActionsGiveNothing() {
        assertEquals(emptyList<NotificationOwnAction>(), capture(action("Answer", reply = true)))
        assertEquals(emptyList<NotificationOwnAction>(), ownActionsOf(null))
    }

    // Through the registry --------------------------------------------------------------------------------------

    private fun statusBarNotification(vararg actions: Notification.Action): StatusBarNotification {
        val notification =
            Notification.Builder(context, "channel")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .apply { actions.forEach { addAction(it) } }
                .build()
        // The platform's own constructor, which is not part of the SDK: reflection, as the framework builds it.
        val constructor =
            StatusBarNotification::class.java.getConstructor(
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Notification::class.java,
                android.os.UserHandle::class.java,
                String::class.java,
                Long::class.javaPrimitiveType,
            )
        return constructor.newInstance(
            "com.riffle.chat", "com.riffle.chat", 1, null, 1000, 0, notification, Process.myUserHandle(), null, 0L,
        )
    }

    @Test
    fun theRegistryHoldsTheOwnButtonsAndSendsOneByIdThroughItsIntent() {
        val sbn =
            statusBarNotification(
                action("Reply", reply = true),
                action("Mark as read", name = "act.mark"),
                action("Archive", name = "act.archive"),
            )
        AndroidNotificationStageActionGateway.replace(context, sbn)
        val notificationKey = LauncherNotificationKey(sbn.key)
        assertEquals(
            listOf("provider:1" to "Mark as read", "provider:2" to "Archive"),
            AndroidNotificationStageActionGateway.ownActionsFor(notificationKey).map { it.id to it.title },
        )
        val result =
            AndroidNotificationStageActionGateway.perform(
                notificationKey,
                NotificationStageAction.ProviderAction("provider:2", "Archive"),
            )
        assertEquals(NotificationStageActionResult.Performed, result)
        // The Archive intent was the one sent: it is the broadcast the platform recorded.
        val sent = org.robolectric.Shadows.shadowOf(context as android.app.Application).broadcastIntents
        assertTrue("sent: ${sent.map { it.action }}", sent.any { it.action == "act.archive" })
        assertTrue("and not the other: ${sent.map { it.action }}", sent.none { it.action == "act.mark" })
    }

    @Test
    fun aCancelledIntentFailsAndANotificationThatHasGoneIsUnavailableAndHasNoButtons() {
        val sbn = statusBarNotification(action("Mark as read", name = "act.cancelled"))
        AndroidNotificationStageActionGateway.replace(context, sbn)
        val notificationKey = LauncherNotificationKey(sbn.key)
        intentFor("act.cancelled").cancel()
        assertEquals(
            NotificationStageActionResult.Failed,
            AndroidNotificationStageActionGateway.perform(
                notificationKey,
                NotificationStageAction.ProviderAction("provider:0", "Mark as read"),
            ),
        )
        AndroidNotificationStageActionGateway.remove(sbn.key)
        assertEquals(
            emptyList<NotificationOwnAction>(),
            AndroidNotificationStageActionGateway.ownActionsFor(notificationKey),
        )
        assertEquals(
            NotificationStageActionResult.Unavailable,
            AndroidNotificationStageActionGateway.perform(
                notificationKey,
                NotificationStageAction.ProviderAction("provider:0", "Mark as read"),
            ),
        )
    }
}
