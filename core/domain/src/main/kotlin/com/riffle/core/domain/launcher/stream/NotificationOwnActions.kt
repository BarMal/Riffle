package com.riffle.core.domain.launcher.stream

/**
 * One of the notification's own action buttons (the posting app's "Mark as read", "Archive", "Like"): the id the action
 * gateway performs it by and the title the app gave it. No intent is ever part of it: the platform handle lives in
 * memory beside the listener, and the title is as transient as the rest of a notification's content: neither is
 * stored (the persisted snapshot carries neither).
 */
data class NotificationOwnAction(
    val id: String,
    val title: String,
)

/**
 * Which of a notification's own actions the Stream shows after Reply, Dismiss and Snooze, as pure rules over what the
 * listener read from the platform: the notification's order is kept, at most [MAX] are shown, and an action is left
 * out when the card already has the control or the Stream cannot perform it.
 */
object NotificationOwnActions {
    /** The most own actions a card shows: three fit on the narrowest folded card on a row of their own. */
    const val MAX = 3

    /** `Notification.Action.SEMANTIC_ACTION_REPLY`: the inline reply the card already has. */
    const val SEMANTIC_REPLY = 1

    /** `Notification.Action.SEMANTIC_ACTION_DELETE`: the card's Dismiss. */
    const val SEMANTIC_DELETE = 4

    /** What the listener read of one platform action; [semanticAction] is null when the app set none. */
    data class Candidate(
        val id: String,
        val title: String?,
        val semanticAction: Int? = null,
        /** The action asks for text (a remote input): it is the reply, or needs input the card cannot give. */
        val needsInput: Boolean = false,
    )

    /**
     * [candidates] in the notification's order, less the inline reply and anything that needs input, less a second
     * "reply" and a "delete" (the card's own controls), less an action with no title; the first [MAX] of what is left.
     * "Mark as read", "archive", "thumbs up and down", "mute" and "call" stay.
     */
    fun select(candidates: List<Candidate>): List<NotificationOwnAction> =
        candidates
            .filter { !it.needsInput && it.semanticAction != SEMANTIC_REPLY && it.semanticAction != SEMANTIC_DELETE }
            .mapNotNull { candidate ->
                candidate.title?.trim()?.takeIf { it.isNotEmpty() }?.let { NotificationOwnAction(candidate.id, it) }
            }
            .take(MAX)
}
