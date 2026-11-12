package com.riffle.app.launcher.stream

import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.app.launcher.notifications.NotificationStageActionGateway
import com.riffle.app.launcher.notifications.NotificationStageActionResult
import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.app.launcher.workspace.ItemImageKeyParser
import com.riffle.app.launcher.workspace.ItemLaunchPort
import com.riffle.app.launcher.workspace.ParsedImageKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.EventKind
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionPolicy
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.ItemTarget

/**
 * The one place the Stream acts on content: P4 passes the content it drew and one of its actions. What is offered is
 * decided by [StreamActionPolicy] (so a redacted or sensitive value can only be opened, and a reply needs text and a
 * live reply target); this only routes to the platform. Reply text and everything else about the content is used for
 * the call and dropped: nothing is stored, logged or put in diagnostics. Blocking calls (media controllers, the RSS
 * cache) belong off the main thread.
 *
 * Hide is not an action here: it writes an exclusion rule from the user's explicit choice of what to hide.
 */
@Suppress("LongParameterList") // One seam over the platform pieces the actions route to.
internal class AndroidStreamActionGateway(
    private val notifications: NotificationStageActionGateway,
    private val replyActionId: (LauncherNotificationKey) -> String?,
    private val snooze: (LauncherNotificationKey, Long) -> Boolean,
    private val media: MediaTransport,
    private val feedCache: FeedArticleCacheRepository,
    /** Told after read state changed, so the Feeds section re-reads. */
    private val feedChanged: () -> Unit,
    private val launch: ItemLaunchPort,
    private val openClock: () -> Boolean,
) : StreamActionGateway {
    override fun perform(
        content: StreamContent,
        action: StreamAction,
    ): StreamActionResult =
        StreamActionPolicy.check(content, action)
            ?: runCatching { route(content, action) }.getOrDefault(StreamActionResult.Failed)

    private fun route(
        content: StreamContent,
        action: StreamAction,
    ): StreamActionResult =
        when (content) {
            is NotificationContent -> notification(content, action)
            is MediaContent -> mediaAction(content, action)
            is EventContent -> if (content.kind == EventKind.ALARM) openClock().result() else openTarget(content.target)
            is ArticleContent -> article(content, action)
            is RecentAppContent -> launchApp(content)
            is TaskContent -> StreamActionResult.NotOffered
        }

    private fun notification(
        content: NotificationContent,
        action: StreamAction,
    ): StreamActionResult {
        val key = content.notificationKey?.let(::LauncherNotificationKey)
        return when {
            action == StreamAction.Open -> {
                val result = key?.let { notifications.perform(it, NotificationStageAction.Open) }
                if (result == NotificationStageActionResult.Performed) {
                    StreamActionResult.Performed
                } else {
                    launch.launchPackage(content.app.packageName).result()
                }
            }
            key == null -> StreamActionResult.NotOffered
            action == StreamAction.Dismiss -> notifications.perform(key, NotificationStageAction.Dismiss).result()
            action is StreamAction.Reply -> {
                val id = replyActionId(key) ?: return StreamActionResult.Unavailable
                notifications.perform(key, NotificationStageAction.ProviderAction(id, "Reply", action.text)).result()
            }
            action is StreamAction.Snooze -> snooze(key, action.durationMillis).result()
            else -> StreamActionResult.NotOffered
        }
    }

    private fun mediaAction(
        content: MediaContent,
        action: StreamAction,
    ): StreamActionResult {
        val result = media.perform(content.app.packageName, action)
        return if (action == StreamAction.Open && result != StreamActionResult.Performed) {
            launch.launchPackage(content.app.packageName).result()
        } else {
            result
        }
    }

    private fun article(
        content: ArticleContent,
        action: StreamAction,
    ): StreamActionResult =
        when (action) {
            StreamAction.MarkRead -> {
                feedCache.markRead(requireNotNull(content.digest))
                feedChanged()
                StreamActionResult.Performed
            }
            else -> openTarget(content.target)
        }

    private fun launchApp(content: RecentAppContent): StreamActionResult {
        val identity = (content.icon?.let(ItemImageKeyParser::parse) as? ParsedImageKey.ActivityIcon)?.identity
        return if (identity != null) {
            launch.launchActivity(identity).result()
        } else {
            launch.launchPackage(content.app.packageName).result()
        }
    }

    private fun openTarget(target: ItemTarget): StreamActionResult =
        when (target) {
            is ItemTarget.DeepLink -> launch.openDeepLink(target.uri).result()
            is ItemTarget.App -> launch.launchPackage(target.packageName).result()
            else -> StreamActionResult.Unavailable
        }

    private fun Boolean.result() = if (this) StreamActionResult.Performed else StreamActionResult.Failed

    private fun NotificationStageActionResult.result() =
        when (this) {
            NotificationStageActionResult.Performed -> StreamActionResult.Performed
            NotificationStageActionResult.Unavailable -> StreamActionResult.Unavailable
            NotificationStageActionResult.Failed -> StreamActionResult.Failed
        }
}
