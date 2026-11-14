package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.ItemImageHandle

/**
 * What the Stream read out of a notification that draws itself with a layout of its own (custom views) and fills in no
 * title or text: the layout's texts, as a title and lines, so the card is drawn in Riffle's own style. As transient as
 * the rest of a notification's content: it lives in memory beside the listener for as long as the notification is
 * posted and is never stored (the persisted snapshot has no field for it).
 */
data class CustomNotificationContent(
    /** The largest (then boldest, then first) text of the layout; null when the layout has no text. */
    val title: String? = null,
    /** The other texts of the compact layout, in drawing order: the browse card's lines. */
    val lines: List<String> = emptyList(),
    /** The other texts of the expanded layout (the compact one's when there is none): the open card's lines. */
    val bigLines: List<String> = emptyList(),
    /** The largest picture of the layout, sized down; null when it has none. */
    val thumbnail: ItemImageHandle? = null,
    /**
     * The layouts have no text at all (a map, a drawn timeline): the card embeds the app's own layout instead, since
     * there is nothing to draw in Riffle's style.
     */
    val embed: Boolean = false,
    /** Counts the notification's updates, so an embedded layout is applied again when the app posts a new one. */
    val revision: Long = 0L,
) {
    /** Whether there is any text to draw. */
    val hasText: Boolean get() = title != null || lines.isNotEmpty() || bigLines.isNotEmpty()
}

/** What a notification's card draws under its header: one of the three shapes a notification's content takes. */
sealed interface NotificationBody {
    /** Nothing but the header. */
    data object None : NotificationBody

    /** The notification's text (its big text, or an inbox style's lines). */
    data class Text(val text: String) : NotificationBody

    /** A conversation's messages, oldest first. */
    data class Thread(val messages: List<NotificationMessage>) : NotificationBody

    /** A custom layout: its extracted lines, or the app's own layout embedded. */
    data class Custom(val content: CustomNotificationContent) : NotificationBody
}

/** One text a custom layout shows, as the platform side collected it by walking the inflated views in drawing order. */
data class CollectedLayoutText(
    val text: String,
    val sizeSp: Float,
    val bold: Boolean = false,
    /** The text is a button of the layout: an action, not content. */
    val clickable: Boolean = false,
)

/**
 * The pure half of reading a custom layout: the texts collected from the view tree become a title and lines.
 * The title is the largest text (a bold one wins a tie, then the first in drawing order); the lines are the other
 * texts in drawing order, without repeats and without the title, at most [maxLines]. A layout's buttons are its
 * actions (the notification's own action buttons are offered beside the card), so a clickable text is no line.
 */
object CustomNotificationMapping {
    /** The most lines the browse card takes from the compact layout. */
    const val MAX_LINES = 3

    /** The most lines the open card takes from the expanded layout. */
    const val MAX_BIG_LINES = 5

    /** The longest text kept for one line; a longer one is cut (the card ellipsizes what it draws anyway). */
    const val MAX_TEXT_LENGTH = 200

    data class Extracted(
        val title: String?,
        val lines: List<String>,
    ) {
        val isEmpty: Boolean get() = title == null && lines.isEmpty()
    }

    fun map(
        texts: List<CollectedLayoutText>,
        maxLines: Int = MAX_BIG_LINES,
    ): Extracted {
        val content =
            texts
                .filter { !it.clickable }
                .map { it.copy(text = it.text.trim().replace(WHITESPACE, " ").take(MAX_TEXT_LENGTH)) }
                .filter { it.text.isNotEmpty() }
        val title = content.maxWithOrNull(compareBy<CollectedLayoutText> { it.sizeSp }.thenBy { it.bold })
        // maxWith keeps the last of equals; the first in drawing order is the title.
        val first = title?.let { best -> content.first { it.sizeSp == best.sizeSp && it.bold == best.bold } }
        val lines =
            content
                .filter { it !== first }
                .map { it.text }
                .filter { it != first?.text }
                .distinct()
                .take(maxLines.coerceAtLeast(0))
        return Extracted(first?.text, lines)
    }

    private val WHITESPACE = Regex("\\s+")
}
