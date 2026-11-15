package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import com.riffle.core.domain.launcher.settings.CardActionsPlacement

/**
 * One control of an open card's actions (ADR 0003, Amendment 1, "Stream: browse, then open"): what the Dock's actions
 * row holds while a card is open, and what the card holds itself when the actions sit on the card. Named apart from
 * [StreamAction] because some of them are not an action on the content ([ToggleQueue] only shows or hides the queue,
 * [Reply] needs the text the user typed).
 */
sealed interface CardAction {
    /** The inline reply field and its send button. */
    data object Reply : CardAction

    data object Dismiss : CardAction

    data object Snooze : CardAction

    data object Previous : CardAction

    /** Play when paused, pause when playing: [playing] is what the player is doing now. */
    data class PlayPause(val playing: Boolean) : CardAction

    data object Next : CardAction

    /** Shows or hides the queue on the open player. */
    data class ToggleQueue(val shown: Boolean) : CardAction

    /** "Open" (the notification's app) or "Open player" ([player]): the content's own open action. */
    data class OpenContent(val player: Boolean) : CardAction

    /** "Open in calendar" (an event), "Open clock" (an alarm). */
    data class OpenEvent(val kind: EventKind) : CardAction

    data object Complete : CardAction

    /** One of the notification's own action buttons: a text button with its [title], performed by its [id]. */
    data class Own(val id: String, val title: String) : CardAction
}

/**
 * Which controls a piece of content puts in its open card's actions, in the order they are laid out, and which
 * content opens in place at all. Pure: the Dock's row and the card's own row both read it, and the content's
 * [StreamActionPolicy] decides what is offered (a control is listed only when its action is offered).
 */
object StreamCardActions {
    /**
     * Whether tapping the front card of [content] opens it in place: a conversation, the player with its queue, an
     * event's detail, a task. A recent app and a launcher shortcut have no further content (their card is their
     * action: a tap launches them), and an article card already shows what it has (its tap opens the article).
     */
    fun opensInPlace(content: StreamContent): Boolean =
        when (content) {
            is MediaContent -> !content.source
            is NotificationContent, is EventContent, is TaskContent -> true
            is ArticleContent, is RecentAppContent, is AppShortcutContent -> false
        }

    /**
     * The controls of the open card of [content]. [queueShown] is whether an open player shows its queue now (a player
     * with no queue has no queue control).
     */
    fun forContent(
        content: StreamContent,
        queueShown: Boolean = true,
    ): List<CardAction> =
        when (content) {
            is NotificationContent -> notification(content)
            is MediaContent -> if (content.source) emptyList() else media(content, queueShown)
            is EventContent ->
                listOfNotNull(
                    CardAction.OpenEvent(content.kind).takeIf { StreamActionPolicy.offers(content, StreamAction.Open) },
                )
            is TaskContent ->
                listOfNotNull(CardAction.Complete.takeIf { StreamActionPolicy.offers(content, StreamAction.Complete) })
            is ArticleContent, is RecentAppContent, is AppShortcutContent -> emptyList()
        }

    /**
     * The controls the browse card of [content] carries itself: the own action buttons of a live notification (a trip's
     * "Next", a download's "Pause"), so they can be tapped without opening the card. Every other control, and every
     * other notification's buttons, are on the open card.
     */
    fun onBrowseCard(content: StreamContent): List<CardAction.Own> =
        if (content is NotificationContent && content.live) {
            content.ownActions
                .filter { StreamActionPolicy.offers(content, StreamAction.OwnAction(it.id)) }
                .map { CardAction.Own(it.id, it.title) }
        } else {
            emptyList()
        }

    private fun notification(content: NotificationContent): List<CardAction> =
        listOfNotNull(
            CardAction.Reply.takeIf { StreamActionPolicy.offers(content, StreamAction.Reply("x")) },
            CardAction.Dismiss.takeIf { StreamActionPolicy.offers(content, StreamAction.Dismiss) },
            CardAction.Snooze.takeIf { StreamActionPolicy.offers(content, StreamAction.Snooze()) },
            CardAction.OpenContent(player = false).takeIf { StreamActionPolicy.offers(content, StreamAction.Open) },
        ) + ownActionsOfOpenCard(content)

    /**
     * The notification's own buttons on its open card, less those that only repeat a control Riffle already offers:
     * Riffle's own Snooze and Dismiss are kept (they are the same in every app) and an app's "Snooze", "Close",
     * "Dismiss" and the like are dropped, so no action appears twice on a card.
     */
    fun ownActionsOfOpenCard(content: NotificationContent): List<CardAction.Own> {
        val snooze = StreamActionPolicy.offers(content, StreamAction.Snooze())
        val dismiss = StreamActionPolicy.offers(content, StreamAction.Dismiss)
        return content.ownActions
            .filter { StreamActionPolicy.offers(content, StreamAction.OwnAction(it.id)) }
            .filterNot { own ->
                val title = own.title.trim().lowercase()
                (snooze && title in SNOOZE_TITLES) || (dismiss && title in DISMISS_TITLES)
            }
            .map { CardAction.Own(it.id, it.title) }
    }

    private val SNOOZE_TITLES = setOf("snooze", "remind me", "remind me later")
    private val DISMISS_TITLES = setOf("dismiss", "close", "clear")

    private fun media(
        content: MediaContent,
        queueShown: Boolean,
    ): List<CardAction> {
        val playing = content.isPlaying == true
        val toggle = if (playing) StreamAction.Pause else StreamAction.Play
        return listOfNotNull(
            CardAction.Previous.takeIf { StreamActionPolicy.offers(content, StreamAction.Previous) },
            CardAction.PlayPause(playing).takeIf { StreamActionPolicy.offers(content, toggle) },
            CardAction.Next.takeIf { StreamActionPolicy.offers(content, StreamAction.Next) },
            CardAction.ToggleQueue(queueShown).takeIf { content.queue.isNotEmpty() },
            CardAction.OpenContent(player = true).takeIf { StreamActionPolicy.offers(content, StreamAction.Open) },
        )
    }

    /**
     * The [StreamAction] [action] performs on the content, or null for a control that does not act on it
     * ([CardAction.ToggleQueue], and [CardAction.Reply], which needs [replyText]).
     */
    fun streamActionFor(
        action: CardAction,
        replyText: String = "",
    ): StreamAction? =
        when (action) {
            CardAction.Reply -> StreamAction.Reply(replyText)
            CardAction.Dismiss -> StreamAction.Dismiss
            CardAction.Snooze -> StreamAction.Snooze()
            CardAction.Previous -> StreamAction.Previous
            is CardAction.PlayPause -> if (action.playing) StreamAction.Pause else StreamAction.Play
            CardAction.Next -> StreamAction.Next
            is CardAction.ToggleQueue -> null
            is CardAction.OpenEvent, is CardAction.OpenContent -> StreamAction.Open
            CardAction.Complete -> StreamAction.Complete
            is CardAction.Own -> StreamAction.OwnAction(action.id)
        }

    /**
     * Where the actions really sit: the Dock's row only where the Dock can hold one, which is a Dock along the top
     * or bottom edge. A Dock down a side is a strip one icon wide: its card carries the actions instead (this slice).
     * Without a Dock to hold them (an expanded posture's Stream beside Home, a surface with no Dock link) they are on
     * the card too.
     */
    fun effectiveActionsPlacement(
        setting: CardActionsPlacement,
        dockPosition: DockPosition,
        hasDock: Boolean = true,
    ): CardActionsPlacement =
        if (setting == CardActionsPlacement.IN_DOCK && hasDock && dockPosition.isHorizontalEdge) {
            CardActionsPlacement.IN_DOCK
        } else {
            CardActionsPlacement.ON_CARD
        }
}
