package com.riffle.app.launcher.stream

import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.app.launcher.notifications.NotificationStageActionGateway
import com.riffle.app.launcher.notifications.NotificationStageActionResult
import com.riffle.app.launcher.rss.NoopFeedArticleCacheRepository
import com.riffle.app.launcher.workspace.ItemLaunchPort
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.stream.AppShortcutContent
import com.riffle.core.domain.launcher.stream.ArticleAction
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.MediaAction
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.NotificationOwnAction
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidStreamActionGatewayTest {
    private val calls = mutableListOf<String>()
    private var feedChanged = 0
    private var replyTarget: String? = "provider:0"
    private var notificationResult: NotificationStageActionResult = NotificationStageActionResult.Performed

    private val gateway =
        AndroidStreamActionGateway(
            notifications =
                NotificationStageActionGateway { key, action ->
                    calls += "n:${key.value}:${action.describe()}"
                    notificationResult
                },
            replyActionId = { replyTarget },
            snooze = { key, ms ->
                calls += "snooze:${key.value}:$ms"
                true
            },
            media =
                object : MediaTransport {
                    override fun perform(
                        packageName: String,
                        action: StreamAction,
                    ): StreamActionResult {
                        calls += "m:$packageName:$action"
                        return StreamActionResult.Performed
                    }
                },
            feedCache =
                object : com.riffle.app.launcher.rss.FeedArticleCacheRepository by NoopFeedArticleCacheRepository {
                    override fun markRead(digest: String) {
                        calls += "read:$digest"
                    }
                },
            feedChanged = { feedChanged++ },
            launch =
                object : ItemLaunchPort {
                    override fun launchActivity(identity: AppIdentity) = true.also { calls += "activity" }

                    override fun launchPackage(packageName: String) = true.also { calls += "package:$packageName" }

                    override fun launchShortcut(shortcut: AppShortcut) =
                        true.also { calls += "shortcut:${shortcut.id.value}" }

                    override fun openDeepLink(uri: String) = true.also { calls += "link:$uri" }

                    override fun dismissNotification(key: String) = true
                },
            openClock = { true.also { calls += "clock" } },
        )

    private fun NotificationStageAction.describe() =
        when (this) {
            is NotificationStageAction.ProviderAction -> "provider:$id:$replyText"
            else -> toString()
        }

    private fun notification(
        redacted: Boolean = false,
        actions: Set<NotificationAction> =
            setOf(
                NotificationAction.OPEN,
                NotificationAction.DISMISS,
                NotificationAction.REPLY,
                NotificationAction.SNOOZE,
            ),
    ) = NotificationContent(
        id = StreamContentId("notifications:personal:k1"),
        app = StreamAppRef("chat", "personal"),
        appLabel = "Chat",
        title = if (redacted) null else "Ann",
        text = if (redacted) null else "hi",
        icon = null,
        image = null,
        postedAtEpochMillis = 1,
        category = null,
        priority = null,
        target = ItemTarget.App("chat", "personal"),
        actions = if (redacted) setOf(NotificationAction.HIDE) else actions,
        privacy = if (redacted) StreamPrivacy.REDACTED else StreamPrivacy.VISIBLE,
        notificationKey = if (redacted) null else "k1",
    )

    @Test
    fun replySendsTheTextToTheLiveReplyTarget_andNothingElse() {
        assertEquals(StreamActionResult.Performed, gateway.perform(notification(), StreamAction.Reply("on my way")))
        assertEquals(listOf("n:k1:provider:provider:0:on my way"), calls)
    }

    @Test
    fun aRedactedNotificationCanOnlyBeOpened_neverRepliedToDismissedOrSnoozed() {
        val hidden = notification(redacted = true)
        assertEquals(StreamActionResult.NotOffered, gateway.perform(hidden, StreamAction.Reply("x")))
        assertEquals(StreamActionResult.NotOffered, gateway.perform(hidden, StreamAction.Dismiss))
        assertEquals(StreamActionResult.NotOffered, gateway.perform(hidden, StreamAction.Snooze()))
        assertTrue(calls.isEmpty())
        assertEquals(StreamActionResult.Performed, gateway.perform(hidden, StreamAction.Open))
        assertEquals(listOf("package:chat"), calls)
    }

    @Test
    fun replyWithoutALiveTargetIsUnavailable_andDismissAndSnoozeRoute() {
        replyTarget = null
        assertEquals(StreamActionResult.Unavailable, gateway.perform(notification(), StreamAction.Reply("x")))
        assertEquals(StreamActionResult.Performed, gateway.perform(notification(), StreamAction.Dismiss))
        assertEquals(StreamActionResult.Performed, gateway.perform(notification(), StreamAction.Snooze(5_000)))
        assertEquals(listOf("n:k1:Dismiss", "snooze:k1:5000"), calls)
    }

    private val ownActions =
        listOf(NotificationOwnAction("provider:1", "Mark as read"), NotificationOwnAction("provider:2", "Archive"))

    @Test
    fun anOwnButtonIsSentByItsIdToTheLiveNotification() {
        val content = notification().copy(ownActions = ownActions)
        assertEquals(StreamActionResult.Performed, gateway.perform(content, StreamAction.OwnAction("provider:2")))
        // By id and with the title the notification gave it; no reply text goes with it.
        assertEquals(listOf("n:k1:provider:provider:2:null"), calls)
    }

    @Test
    fun anOwnButtonTheNotificationDoesNotHaveIsNotOfferedAndSendsNothing() {
        val content = notification().copy(ownActions = ownActions)
        assertEquals(StreamActionResult.NotOffered, gateway.perform(content, StreamAction.OwnAction("provider:9")))
        assertEquals(
            StreamActionResult.NotOffered,
            gateway.perform(notification(redacted = true), StreamAction.OwnAction("provider:1")),
        )
        assertTrue(calls.isEmpty())
    }

    @Test
    fun anOwnButtonOfANotificationThatHasGoneIsUnavailableAndOneWhoseIntentWasCancelledFails() {
        val content = notification().copy(ownActions = ownActions)
        notificationResult = NotificationStageActionResult.Unavailable
        assertEquals(StreamActionResult.Unavailable, gateway.perform(content, StreamAction.OwnAction("provider:1")))
        notificationResult = NotificationStageActionResult.Failed
        assertEquals(StreamActionResult.Failed, gateway.perform(content, StreamAction.OwnAction("provider:1")))
        // And nothing launched in their place: an own button is not "Open".
        assertEquals(listOf("n:k1:provider:provider:1:null", "n:k1:provider:provider:1:null"), calls)
    }

    @Test
    fun mediaTransportOnlyRunsWhatTheSessionOffers() {
        val media =
            MediaContent(
                id = StreamContentId("media:personal:k"),
                app = StreamAppRef("player", "personal"),
                appLabel = "Player",
                title = "Song",
                artist = null,
                artwork = null,
                icon = null,
                durationMillis = 1_000,
                target = ItemTarget.App("player", "personal"),
                actions = setOf(MediaAction.OPEN, MediaAction.PAUSE, MediaAction.SEEK),
                privacy = StreamPrivacy.VISIBLE,
            )
        assertEquals(StreamActionResult.Performed, gateway.perform(media, StreamAction.Pause))
        assertEquals(StreamActionResult.Performed, gateway.perform(media, StreamAction.SeekTo(500)))
        assertEquals(StreamActionResult.NotOffered, gateway.perform(media, StreamAction.Next))
        assertEquals(StreamActionResult.NotOffered, gateway.perform(media, StreamAction.SeekTo(5_000)))
        assertEquals(listOf("m:player:Pause", "m:player:SeekTo(positionMillis=500)"), calls)
    }

    @Test
    fun markReadWritesTheReadStateAndTellsTheFeedsToReload() {
        val article =
            ArticleContent(
                id = StreamContentId("rss:f:d1"),
                feedId = "f",
                feedName = "F",
                title = "T",
                summary = null,
                author = null,
                image = null,
                publishedAtEpochMillis = 1,
                stale = false,
                target = ItemTarget.DeepLink("https://example.org/a"),
                actions = setOf(ArticleAction.OPEN, ArticleAction.MARK_READ),
                privacy = StreamPrivacy.VISIBLE,
                digest = "d1",
            )
        assertEquals(StreamActionResult.Performed, gateway.perform(article, StreamAction.MarkRead))
        assertEquals(listOf("read:d1"), calls)
        assertEquals(1, feedChanged)
        assertEquals(StreamActionResult.Performed, gateway.perform(article, StreamAction.Open))
        assertEquals("link:https://example.org/a", calls.last())
        assertEquals(
            StreamActionResult.NotOffered,
            gateway.perform(article.copy(actions = setOf(ArticleAction.OPEN)), StreamAction.MarkRead),
        )
    }

    private fun shortcutCard(
        shortcutId: String?,
        enabled: Boolean = true,
    ) = AppShortcutContent(
        id = StreamContentId("shortcut:files:personal/${shortcutId.orEmpty()}"),
        app = StreamAppRef("files", "personal"),
        appIdentity = AppIdentity(AppPackageName("files"), AppActivityName(".Main")),
        appLabel = "Files",
        shortcutId = shortcutId,
        shortLabel = shortcutId ?: "Files",
        longLabel = null,
        icon = null,
        enabled = enabled,
    )

    @Test
    fun aShortcutCardLaunchesThatShortcutAndTheOpenCardLaunchesTheApp() {
        assertEquals(StreamActionResult.Performed, gateway.perform(shortcutCard("recent"), StreamAction.Open))
        assertEquals(StreamActionResult.Performed, gateway.perform(shortcutCard(null), StreamAction.Open))
        assertEquals(listOf("shortcut:recent", "activity"), calls)
    }

    @Test
    fun aDisabledShortcutIsNotLaunchedAndNothingButOpenIsOffered() {
        assertEquals(
            StreamActionResult.NotOffered,
            gateway.perform(shortcutCard("scan", enabled = false), StreamAction.Open),
        )
        assertEquals(StreamActionResult.NotOffered, gateway.perform(shortcutCard("scan"), StreamAction.Dismiss))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun openingANotificationSendsItsOwnContentIntentAndLaunchesNothingElse() {
        assertEquals(StreamActionResult.Performed, gateway.perform(notification(), StreamAction.Open))
        assertEquals(listOf("n:k1:Open"), calls)
    }

    @Test
    fun aNotificationWhoseContentIntentIsGoneOrCancelledLaunchesTheApp() {
        notificationResult = NotificationStageActionResult.Unavailable
        assertEquals(StreamActionResult.Performed, gateway.perform(notification(), StreamAction.Open))
        notificationResult = NotificationStageActionResult.Failed
        assertEquals(StreamActionResult.Performed, gateway.perform(notification(), StreamAction.Open))
        assertEquals(listOf("n:k1:Open", "package:chat", "n:k1:Open", "package:chat"), calls)
    }

    @Test
    fun aNotificationWithNoKeyLaunchesTheAppWithoutAskingThePlatformForAnIntent() {
        val keyless = notification().copy(notificationKey = null)
        assertEquals(StreamActionResult.Performed, gateway.perform(keyless, StreamAction.Open))
        assertEquals(listOf("package:chat"), calls)
    }
}
