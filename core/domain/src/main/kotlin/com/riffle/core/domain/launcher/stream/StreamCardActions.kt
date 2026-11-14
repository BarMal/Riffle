package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.OpenCardPlacement

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

    /** "Open in calendar" (an event), "Open clock" (an alarm). */
    data class OpenEvent(val kind: EventKind) : CardAction

    data object Complete : CardAction
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
            is NotificationContent, is MediaContent, is EventContent, is TaskContent -> true
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
            is MediaContent -> media(content, queueShown)
            is EventContent ->
                listOfNotNull(
                    CardAction.OpenEvent(content.kind).takeIf { StreamActionPolicy.offers(content, StreamAction.Open) },
                )
            is TaskContent ->
                listOfNotNull(CardAction.Complete.takeIf { StreamActionPolicy.offers(content, StreamAction.Complete) })
            is ArticleContent, is RecentAppContent, is AppShortcutContent -> emptyList()
        }

    private fun notification(content: NotificationContent): List<CardAction> =
        listOfNotNull(
            CardAction.Reply.takeIf { StreamActionPolicy.offers(content, StreamAction.Reply("x")) },
            CardAction.Dismiss.takeIf { StreamActionPolicy.offers(content, StreamAction.Dismiss) },
            CardAction.Snooze.takeIf { StreamActionPolicy.offers(content, StreamAction.Snooze()) },
        )

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
            is CardAction.OpenEvent -> StreamAction.Open
            CardAction.Complete -> StreamAction.Complete
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

    /** Where an open card sits: "grows out of the Dock" is drawn as just above the Dock for now (deferred). */
    fun effectiveCardPlacement(setting: OpenCardPlacement): OpenCardPlacement =
        if (setting == OpenCardPlacement.GROWS_OUT_OF_DOCK) OpenCardPlacement.ABOVE_DOCK else setting
}
