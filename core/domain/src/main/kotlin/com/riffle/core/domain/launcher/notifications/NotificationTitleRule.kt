package com.riffle.core.domain.launcher.notifications

/**
 * Which of the texts a notification publishes is its title, and what is left as its text. Pure, over what the listener
 * read from the platform extras, so the rules are tested without a device.
 *
 * - A messaging style: the conversation's title (a group's name), else the title the platform wrote (the sender), else
 *   the newest message's sender. A person or a group keeps its name.
 * - Anything else: the title, else the big title (a big-text or big-picture style may set only that one).
 * - A candidate that is only the app's own name says nothing the card does not already say (the app's name is the
 *   card's second line): the next candidate is taken, and when there is none the first short line of the text is the
 *   title ("Claude has a question" and not "Claude").
 * - With no title at all the result is blank: the card then shows the app's name as its title, never a hidden state.
 */
object NotificationTitleRule {
    /** The longest first line of the text that may stand in as a title. */
    const val MAX_PROMOTED_TITLE_LENGTH = 80

    data class Input(
        val title: String? = null,
        val titleBig: String? = null,
        val conversationTitle: String? = null,
        val messagingStyle: Boolean = false,
        /** The newest message's sender, for a messaging style. */
        val lastSender: String? = null,
        val text: String? = null,
        val appLabel: String? = null,
    )

    data class Resolved(
        val title: String,
        val text: String,
    )

    fun resolve(input: Input): Resolved {
        val text = input.text?.trim().orEmpty()
        val candidates =
            if (input.messagingStyle) {
                listOf(input.conversationTitle, input.title, input.lastSender, input.titleBig)
            } else {
                listOf(input.title, input.titleBig)
            }.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        val own = candidates.firstOrNull { !it.isAppName(input.appLabel) }
        if (own != null) return Resolved(own, text)
        // Only the app's name, or nothing: a short first line of the text says more.
        val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
        val promoted =
            firstLine.takeIf {
                it.isNotEmpty() && it.length <= MAX_PROMOTED_TITLE_LENGTH && candidates.isNotEmpty()
            }
        return if (promoted != null) {
            Resolved(promoted, if (text == promoted) "" else text)
        } else {
            Resolved(candidates.firstOrNull().orEmpty(), text)
        }
    }

    private fun String.isAppName(appLabel: String?): Boolean =
        appLabel != null && appLabel.isNotBlank() && equals(appLabel.trim(), ignoreCase = true)
}
