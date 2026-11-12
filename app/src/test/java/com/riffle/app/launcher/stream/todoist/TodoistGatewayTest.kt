package com.riffle.app.launcher.stream.todoist

import com.riffle.app.launcher.notifications.NotificationStageActionGateway
import com.riffle.app.launcher.notifications.NotificationStageActionResult
import com.riffle.app.launcher.rss.NoopFeedArticleCacheRepository
import com.riffle.app.launcher.stream.AndroidStreamActionGateway
import com.riffle.app.launcher.stream.MediaTransport
import com.riffle.app.launcher.workspace.ItemLaunchPort
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.TaskActionOutcome
import com.riffle.core.domain.launcher.stream.TaskActions
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.ItemTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoistGatewayTest {
    private val calls = ArrayList<String>()
    private var appInstalled = true
    private var outcome = TaskActionOutcome.DONE

    private val tasks =
        object : TaskActions {
            override fun complete(task: TaskContent): TaskActionOutcome =
                outcome.also { calls += "complete:${task.id.value}" }

            override fun add(text: String): TaskActionOutcome = outcome

            override fun refresh() = Unit
        }

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
                    ) = StreamActionResult.Performed
                },
            feedCache = NoopFeedArticleCacheRepository,
            feedChanged = {},
            launch =
                object : ItemLaunchPort {
                    override fun launchActivity(identity: AppIdentity) = true

                    override fun launchPackage(packageName: String) = true

                    override fun launchShortcut(shortcut: AppShortcut) = true

                    override fun openDeepLink(uri: String): Boolean {
                        calls += "link:$uri"
                        return appInstalled || uri.startsWith("https")
                    }

                    override fun dismissNotification(key: String) = true
                },
            openClock = { true },
            tasks = tasks,
        )

    private val task =
        TaskContent(
            StreamContentId("todoist:42"),
            "Pay rent",
            target = ItemTarget.DeepLink("https://app.todoist.com/app/task/42"),
        )

    @Test
    fun completeGoesToTheSourceAndReportsItsOutcome() {
        assertEquals(StreamActionResult.Performed, gateway.perform(task, StreamAction.Complete))
        outcome = TaskActionOutcome.OFFLINE
        assertEquals(StreamActionResult.Failed, gateway.perform(task, StreamAction.Complete))
        assertEquals(listOf("complete:todoist:42", "complete:todoist:42"), calls)
    }

    @Test
    fun openPrefersTheAppThenTheWeb() {
        assertEquals(StreamActionResult.Performed, gateway.perform(task, StreamAction.Open))
        assertEquals(listOf("link:todoist://task?id=42"), calls)
        calls.clear()
        appInstalled = false
        assertEquals(StreamActionResult.Performed, gateway.perform(task, StreamAction.Open))
        assertEquals(listOf("link:todoist://task?id=42", "link:https://app.todoist.com/app/task/42"), calls)
    }

    @Test
    fun aHiddenTaskOffersNeitherCompleteNorAnythingButOpen() {
        val hidden = task.copy(title = null, privacy = StreamPrivacy.REDACTED, actions = emptySet())
        assertEquals(StreamActionResult.NotOffered, gateway.perform(hidden, StreamAction.Complete))
        assertEquals(StreamActionResult.NotOffered, gateway.perform(hidden, StreamAction.Open))
        assertEquals(emptyList<String>(), calls)
    }
}
