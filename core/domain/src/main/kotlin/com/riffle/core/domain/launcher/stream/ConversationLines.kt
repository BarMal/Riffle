package com.riffle.core.domain.launcher.stream

/**
 * Who a line of a conversation says wrote it, so the card never repeats what its own title already says (device
 * fixes, round 7, item 3: a group whose app names the group as the sender of every message read "Book club: ..." on
 * every line, so the title was said four times and the message was cut off). Pure.
 *
 * - A sender that is the card's title, or the conversation's (compared trimmed, ignoring case), is never said: the line
 *   is the text alone.
 * - In a group, a line is prefixed with the person's name when the platform gives one.
 * - In a one-to-one chat the other person's lines are the text alone (the card's title is them). The user's own lines
 *   (no sender) keep their "You" so that who said what stays clear.
 */
object ConversationLines {
    /**
     * [thread] with each message marked [NotificationMessage.showSender] or not, given the [titles] the card is called
     * by (its headline, the conversation's title: blank and null ones are ignored).
     */
    fun resolve(
        thread: List<NotificationMessage>,
        titles: List<String?>,
    ): List<NotificationMessage> {
        val known = titles.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        if (known.isEmpty()) return thread
        val speakers =
            thread.mapNotNull { it.sender?.trim()?.takeIf(String::isNotEmpty) }.distinctBy { it.lowercase() }
        // One other person and a title that names them (whole, or by their first name): a one-to-one chat.
        val oneToOne = speakers.size == 1 && known.any { namesTheSamePerson(it, speakers.single()) }
        return thread.map { message ->
            val sender = message.sender?.trim()?.takeIf(String::isNotEmpty)
            val repeatsTitle = sender != null && (known.any { it.equals(sender, ignoreCase = true) } || oneToOne)
            if (repeatsTitle) message.copy(showSender = false) else message
        }
    }

    private fun namesTheSamePerson(
        title: String,
        sender: String,
    ): Boolean {
        val a = title.lowercase().split(' ').first()
        val b = sender.lowercase().split(' ').first()
        return title.equals(sender, ignoreCase = true) || (a == b && (title.contains(' ') || sender.contains(' ')))
    }
}

/** The line as a card writes it: "Name: text", "You: text" for the user's own, or the text alone. */
fun NotificationMessage.written(you: String): String =
    when {
        !showSender -> text
        else -> "${sender ?: you}: $text"
    }
