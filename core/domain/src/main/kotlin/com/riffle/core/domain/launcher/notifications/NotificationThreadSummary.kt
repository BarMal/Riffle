package com.riffle.core.domain.launcher.notifications

/**
 * Folds one notification's per-message history into the title and body of a single card.
 *
 * A messaging notification that carries message history can be presented two ways: one card per
 * message, or one card per thread. Per message spreads a conversation across a stack the user has
 * to page through, and gives each card a single short line to show -- a card sized for a
 * notification, carrying a sentence. Per thread puts the conversation on one card, which is both
 * fewer cards and more on each of them.
 *
 * This owns only that fold. Whether it happens at all is the caller's choice, and the ordering is
 * passed in rather than read here so that this stays independent of the settings package -- which
 * already depends on this one.
 */
object NotificationThreadSummary {
    /**
     * The sender to title a combined card with, or `null` when the messages do not agree on one.
     *
     * A group chat has many senders and no single one to name, so the notification's own title --
     * the conversation's name -- is the honest label there, and the per-message senders move into
     * the body instead. A one-to-one thread does agree, and naming the person is more use than
     * repeating the app's own conversation title.
     */
    fun combinedTitle(messages: List<LauncherNotificationMessage>): String? =
        messages
            .map { message -> message.sender.trim() }
            .filter { sender -> sender.isNotEmpty() }
            .distinct()
            .singleOrNull()

    /**
     * The messages as one body, one per line, in [newestFirst] order.
     *
     * Each line carries its sender only when the thread has more than one, because a single-sender
     * thread already names them in the card's title and repeating it on every line is noise. Blank
     * messages are dropped rather than rendered as empty lines -- a notification that supplies an
     * empty message still supplies a line's worth of height otherwise.
     */
    fun combinedText(
        messages: List<LauncherNotificationMessage>,
        newestFirst: Boolean,
    ): String {
        val meaningful = messages.filter { message -> message.text.isNotBlank() }
        if (meaningful.isEmpty()) return ""
        val ordered =
            meaningful
                .sortedBy { message -> message.timestampEpochMillis }
                .let { sorted -> if (newestFirst) sorted.asReversed() else sorted }
        val attributesSenders = combinedTitle(meaningful) == null
        return ordered.joinToString(separator = "\n") { message ->
            val sender = message.sender.trim()
            if (attributesSenders && sender.isNotEmpty()) "$sender: ${message.text}" else message.text
        }
    }

    /**
     * When the newest message in [messages] arrived, for ordering a combined card against the rest
     * of the stack. Falls back to [fallbackEpochMillis] -- the notification's own posted time -- for
     * a thread whose messages carry no usable timestamps.
     */
    fun latestActivityEpochMillis(
        messages: List<LauncherNotificationMessage>,
        fallbackEpochMillis: Long,
    ): Long =
        messages
            .maxOfOrNull { message -> message.timestampEpochMillis }
            ?.takeIf { latest -> latest > 0L }
            ?: fallbackEpochMillis
}
