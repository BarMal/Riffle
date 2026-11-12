package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.ItemTarget

/**
 * One thing the user can do with a piece of [StreamContent], named independently of its type. P4 calls
 * [StreamActionGateway.perform] with the content it drew and one of these; the gateway refuses anything the
 * content does not offer.
 */
sealed interface StreamAction {
    /** Launch the content's target (the app, the player, the event, the article, the clock). */
    data object Open : StreamAction

    data object Dismiss : StreamAction

    /** Reply inline; [text] is user input and is never stored or logged. */
    data class Reply(val text: String) : StreamAction

    data class Snooze(val durationMillis: Long = DEFAULT_SNOOZE_MILLIS) : StreamAction {
        init {
            require(durationMillis > 0L) { "Snooze duration must be positive." }
        }
    }

    data object Play : StreamAction

    data object Pause : StreamAction

    data object Previous : StreamAction

    data object Next : StreamAction

    data class SeekTo(val positionMillis: Long) : StreamAction {
        init {
            require(positionMillis >= 0L) { "Seek position cannot be negative." }
        }
    }

    data object MarkRead : StreamAction

    companion object {
        const val DEFAULT_SNOOZE_MILLIS = 60L * 60L * 1000L
    }
}

sealed interface StreamActionResult {
    data object Performed : StreamActionResult

    /** The content does not offer this action (redacted, the session cannot do it, or the type has no such action). */
    data object NotOffered : StreamActionResult

    /** The platform handle is gone (the notification was removed, the player closed). */
    data object Unavailable : StreamActionResult

    data object Failed : StreamActionResult
}

/**
 * The one entry point for acting on Stream content. The app implementation routes to the platform (notification
 * PendingIntents, media controllers, the RSS cache, launching); it must call [StreamActionPolicy.check] first so
 * the rule about what is offered lives in one tested place. Not for Hide (an exclusion rule needs the user's choice
 * of what to hide) or for Tasks (no source yet). Called off the main thread for anything that blocks.
 */
fun interface StreamActionGateway {
    fun perform(
        content: StreamContent,
        action: StreamAction,
    ): StreamActionResult
}

/** Which [StreamAction]s a piece of content offers. Pure; the single source of truth for gateways and UI. */
object StreamActionPolicy {
    /** [StreamActionResult.NotOffered] when [content] does not offer [action], otherwise null. */
    fun check(
        content: StreamContent,
        action: StreamAction,
    ): StreamActionResult? = if (offers(content, action)) null else StreamActionResult.NotOffered

    fun offers(
        content: StreamContent,
        action: StreamAction,
    ): Boolean =
        redactionAllows(content, action) &&
            when (content) {
                is NotificationContent -> notification(content, action)
                is MediaContent -> media(content, action)
                is EventContent -> action == StreamAction.Open && EventAction.OPEN in content.actions
                is ArticleContent -> article(content, action)
                is RecentAppContent -> action == StreamAction.Open && RecentAppAction.LAUNCH in content.actions
                is TaskContent -> false
            }

    /** A redacted value shows "Hidden content": it can be opened (and a media card still has its transport). */
    private fun redactionAllows(
        content: StreamContent,
        action: StreamAction,
    ): Boolean =
        content.privacy != StreamPrivacy.REDACTED ||
            action == StreamAction.Open ||
            (content is MediaContent && isTransport(action))

    private fun article(
        content: ArticleContent,
        action: StreamAction,
    ): Boolean =
        when (action) {
            StreamAction.Open -> ArticleAction.OPEN in content.actions
            StreamAction.MarkRead -> ArticleAction.MARK_READ in content.actions && content.digest != null
            else -> false
        }

    private fun notification(
        content: NotificationContent,
        action: StreamAction,
    ): Boolean {
        val keyed = content.notificationKey != null
        return when (action) {
            // Opening needs no content, only the app: a redacted notification still opens its app.
            StreamAction.Open -> NotificationAction.OPEN in content.actions || content.target is ItemTarget.App
            StreamAction.Dismiss -> keyed && NotificationAction.DISMISS in content.actions
            is StreamAction.Reply -> keyed && NotificationAction.REPLY in content.actions && action.text.isNotBlank()
            is StreamAction.Snooze -> keyed && NotificationAction.SNOOZE in content.actions
            else -> false
        }
    }

    private fun media(
        content: MediaContent,
        action: StreamAction,
    ): Boolean =
        when (action) {
            StreamAction.Open -> MediaAction.OPEN in content.actions
            StreamAction.Play -> MediaAction.PLAY in content.actions
            StreamAction.Pause -> MediaAction.PAUSE in content.actions
            StreamAction.Previous -> MediaAction.PREVIOUS in content.actions
            StreamAction.Next -> MediaAction.NEXT in content.actions
            is StreamAction.SeekTo ->
                MediaAction.SEEK in content.actions &&
                    (content.durationMillis == null || action.positionMillis <= content.durationMillis)
            else -> false
        }

    private fun isTransport(action: StreamAction): Boolean =
        action is StreamAction.Play || action is StreamAction.Pause || action is StreamAction.Previous ||
            action is StreamAction.Next || action is StreamAction.SeekTo
}
