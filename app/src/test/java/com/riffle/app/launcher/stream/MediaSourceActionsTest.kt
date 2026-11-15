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

/** What the Stream's actions do on a recent player (a media source card): resume, else open; open; forget. */
class MediaSourceActionsTest {
    private val calls = mutableListOf<String>()
    private var resumeResult: StreamActionResult = StreamActionResult.Performed

    private val resumer =
        object : MediaResumer {
            override fun canResume(packageName: String) = true

            override fun resume(packageName: String): StreamActionResult {
                calls += "resume:$packageName"
                return resumeResult
            }
        }

    private val control =
        object : RecentPlayersControl {
            override fun dismiss(key: String) {
                calls += "dismiss:$key"
            }

            override fun clear() {
                calls += "clear"
            }
        }

    private fun gateway(withControl: Boolean = true) =
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
                },
            feedCache = NoopFeedArticleCacheRepository,
            feedChanged = {},
            launch =
                object : ItemLaunchPort {
                    override fun launchActivity(identity: AppIdentity) = true

                    override fun launchPackage(packageName: String) = true.also { calls += "open:$packageName" }

                    override fun launchShortcut(shortcut: AppShortcut) = true

                    override fun openDeepLink(uri: String) = true

                    override fun dismissNotification(key: String) = true
                },
            openClock = { true },
            resumer = resumer,
            recentPlayers = control.takeIf { withControl },
        )

    private fun source(resumable: Boolean = true) =
        MediaContent(
            id = StreamContentId("media:recent:music:personal"),
            app = StreamAppRef("music", "personal"),
            appLabel = "Music",
            title = null,
            artist = null,
            artwork = null,
            icon = null,
            target = ItemTarget.App("music", "personal"),
            actions =
                buildSet {
                    add(MediaAction.OPEN)
                    if (resumable) add(MediaAction.PLAY)
                    add(MediaAction.DISMISS)
                },
            postedAtEpochMillis = 5L,
            privacy = StreamPrivacy.VISIBLE,
            source = true,
        )

    @Test
    fun playResumesThroughTheResumerAndNeverTouchesTheMediaTransport() {
        assertEquals(StreamActionResult.Performed, gateway().perform(source(), StreamAction.Play))
        assertEquals(listOf("resume:music"), calls)
    }

    @Test
    fun playThatTheAppDidNotAnswerInTimeOpensTheAppInstead() {
        resumeResult = StreamActionResult.Failed
        assertEquals(StreamActionResult.Performed, gateway().perform(source(), StreamAction.Play))
        assertEquals(listOf("resume:music", "open:music"), calls)
    }

    @Test
    fun aSourceThatCannotBeResumedIsNotOfferedPlayAndOpenOpensTheApp() {
        assertEquals(StreamActionResult.NotOffered, gateway().perform(source(resumable = false), StreamAction.Play))
        assertEquals(StreamActionResult.Performed, gateway().perform(source(resumable = false), StreamAction.Open))
        assertEquals(listOf("open:music"), calls)
    }

    @Test
    fun dismissForgetsTheAppByItsKeyAndIsUnavailableWithoutAList() {
        assertEquals(StreamActionResult.Performed, gateway().perform(source(), StreamAction.Dismiss))
        assertEquals(listOf("dismiss:music:personal"), calls)
        val bare = gateway(withControl = false)
        assertEquals(StreamActionResult.Unavailable, bare.perform(source(), StreamAction.Dismiss))
    }

    @Test
    fun noResumerMeansEveryPlayIsAnOpen() {
        assertEquals(false, MediaResumer.NONE.canResume("music"))
        assertEquals(StreamActionResult.Unavailable, MediaResumer.NONE.resume("music"))
    }
}
