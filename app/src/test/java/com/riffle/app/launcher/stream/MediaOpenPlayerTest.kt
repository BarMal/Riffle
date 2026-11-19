package com.riffle.app.launcher.stream

import com.riffle.app.launcher.notifications.NotificationStageActionGateway
import com.riffle.app.launcher.notifications.NotificationStageActionResult
import com.riffle.app.launcher.rss.NoopFeedArticleCacheRepository
import com.riffle.app.launcher.workspace.ItemLaunchPort
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.stream.MediaAction
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Open player" on an active player: the session's own activity, then the app's launcher entry, then any main activity,
 * and only then a failure (which the Stream says out loud). Each step is a fallback for the one before it.
 */
class MediaOpenPlayerTest {
    private val calls = mutableListOf<String>()
    private var session: StreamActionResult = StreamActionResult.Performed
    private var launcherEntry = true
    private var mainActivity = true

    private val gateway =
        AndroidStreamActionGateway(
            notifications = NotificationStageActionGateway { _, _ -> NotificationStageActionResult.Performed },
            replyActionId = { null },
            snooze = { _, _ -> true },
            media =
                object : MediaTransport {
                    override fun perform(
                        packageName: String,
                        action: StreamAction,
                    ): StreamActionResult {
                        calls += "transport:$packageName:$action"
                        return StreamActionResult.Performed
                    }

                    override fun openSession(packageName: String): StreamActionResult {
                        calls += "session:$packageName"
                        return session
                    }
                },
            feedCache = NoopFeedArticleCacheRepository,
            feedChanged = {},
            launch =
                object : ItemLaunchPort {
                    override fun launchActivity(identity: AppIdentity) = true

                    override fun launchPackage(packageName: String) =
                        launcherEntry.also { calls += "entry:$packageName" }

                    override fun launchAnyMainActivity(packageName: String) =
                        mainActivity.also { calls += "main:$packageName" }

                    override fun launchShortcut(shortcut: AppShortcut) = true

                    override fun openDeepLink(uri: String) = true

                    override fun dismissNotification(key: String) = true
                },
            openClock = { true },
        )

    private val player =
        MediaContent(
            id = StreamContentId("media:music:personal"),
            app = StreamAppRef("com.example.music", "personal"),
            appLabel = "Music",
            title = "Morning Tune",
            artist = "Example Band",
            artwork = null,
            icon = null,
            isPlaying = true,
            target = ItemTarget.App("com.example.music", "personal"),
            actions = setOf(MediaAction.OPEN, MediaAction.PAUSE),
            privacy = StreamPrivacy.VISIBLE,
        )

    @Test
    fun theSessionsOwnActivityIsTheFirstChoiceAndNothingElseIsStarted() {
        assertEquals(StreamActionResult.Performed, gateway.perform(player, StreamAction.Open))
        assertEquals(listOf("session:com.example.music"), calls)
    }

    @Test
    fun aSessionThatNamesNoActivityOpensTheLauncherEntry() {
        session = StreamActionResult.Unavailable
        assertEquals(StreamActionResult.Performed, gateway.perform(player, StreamAction.Open))
        assertEquals(listOf("session:com.example.music", "entry:com.example.music"), calls)
    }

    @Test
    fun aRefusedSendOpensTheLauncherEntryToo() {
        session = StreamActionResult.Failed
        assertEquals(StreamActionResult.Performed, gateway.perform(player, StreamAction.Open))
        assertEquals(listOf("session:com.example.music", "entry:com.example.music"), calls)
    }

    @Test
    fun anAppWithNoLauncherEntryOpensAnyMainActivity() {
        session = StreamActionResult.Unavailable
        launcherEntry = false
        assertEquals(StreamActionResult.Performed, gateway.perform(player, StreamAction.Open))
        assertEquals(
            listOf("session:com.example.music", "entry:com.example.music", "main:com.example.music"),
            calls,
        )
    }

    @Test
    fun whenNothingStartsTheActionFails_soTheStreamCanSayCouldNotOpen() {
        session = StreamActionResult.Unavailable
        launcherEntry = false
        mainActivity = false
        assertEquals(StreamActionResult.Failed, gateway.perform(player, StreamAction.Open))
        assertEquals(
            StreamFailure("Music"),
            StreamFailure.of(player, StreamAction.Open),
        )
        assertEquals(StreamFailure(), StreamFailure.of(player, StreamAction.Next))
    }

    @Test
    fun theTransportIsNeverAskedToOpen() {
        gateway.perform(player, StreamAction.Pause)
        assertEquals(listOf("transport:com.example.music:Pause"), calls)
    }
}
