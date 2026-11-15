package com.riffle.core.domain.launcher.stream

/**
 * How tall a notification's browse card is: as tall as what it shows, between a minimum and a maximum, instead of one
 * fixed height with empty room under a short message. The numbers are the card's own layout, in dp: the header (4 dp
 * above an icon row of 48), 8 dp between its parts, the body (measured by the caller: up to [MAX_BODY_LINES] lines, or
 * an embedded layout), a trailing thumbnail beside the body, the row of a live notification's buttons, and 8 dp under
 * it all. Pure, so the rule is tested; the stack's geometry takes the result as the card's height.
 */
object BrowseCardHeight {
    /** The header: its 4 dp top padding and its 48 dp row. It is what a strip shows. */
    const val HEADER_DP = 52f

    /** Between the header, the body and the buttons. */
    const val SPACING_DP = 8f

    /** Under the last part. */
    const val BOTTOM_DP = 8f

    /** One line of body text at the default font size. */
    const val LINE_DP = 20f

    /** The trailing picture thumbnail, when the notification carries a picture. */
    const val THUMBNAIL_DP = 64f

    /** The row of a live notification's own buttons: 48 dp targets. */
    const val ACTIONS_DP = 48f

    /** The most lines of body the browse card shows; the open card shows the rest. */
    const val MAX_BODY_LINES = 4

    /** The most messages of a conversation shown as "Sender: text" lines. */
    const val MAX_THREAD_LINES = 3

    /** The least a card is tall: its header and one line, so the stack never collapses to a row of strips. */
    const val MIN_DP = HEADER_DP + SPACING_DP + LINE_DP + BOTTOM_DP

    /** The most a card is tall: the stack's card height (four lines fit it). */
    const val MAX_DP = StreamCardStackSpec.CARD_HEIGHT_DP

    /** The most a card with buttons is tall: four lines and the row of buttons. */
    const val MAX_WITH_ACTIONS_DP = MAX_DP + SPACING_DP + ACTIONS_DP - BOTTOM_DP

    /**
     * The height of a card whose body is [bodyDp] tall as drawn (0: none). [scale] is the font scale the fixed parts
     * grow with (the body is measured, so it already has it).
     */
    fun notification(
        bodyDp: Float,
        hasThumbnail: Boolean = false,
        hasActions: Boolean = false,
        scale: Float = 1f,
        sizes: StreamCardSizes = StreamCardSizes.FOLDED,
    ): Float {
        val s = scale.coerceAtLeast(1f)
        val middle = maxOf(bodyDp.coerceAtLeast(0f), if (hasThumbnail) THUMBNAIL_DP else 0f)
        val wanted =
            HEADER_DP * s +
                (if (middle > 0f) SPACING_DP + middle else 0f) +
                (if (hasActions) SPACING_DP + ACTIONS_DP * s else 0f) +
                BOTTOM_DP
        val max = (if (hasActions) MAX_WITH_ACTIONS_DP else MAX_DP) * s * sizes.maxHeightFactor
        return wanted.coerceIn(MIN_DP * s, max)
    }
}

/**
 * What a notification's browse card writes under its header, as blocks of text each with the most lines it may take:
 * the last few messages of a conversation one line each ("Sender: text"), else the notification's text (its big text,
 * or an inbox style's lines) up to [BrowseCardHeight.MAX_BODY_LINES] lines, else the lines read out of its own layout
 * one line each. Empty when there is nothing to write (or the layout is embedded instead). Whatever does not fit is
 * ellipsized; the open card shows it all.
 */
object NotificationBrowseText {
    data class Block(
        val text: String,
        val maxLines: Int,
        /** A message of a conversation: [sender] is who wrote it (null: the user), said before the text. */
        val message: Boolean = false,
        val sender: String? = null,
    )

    fun blocks(
        content: NotificationContent,
        sizes: StreamCardSizes = StreamCardSizes.FOLDED,
    ): List<Block> =
        when (val body = content.body) {
            is NotificationBody.Thread ->
                body.messages.takeLast(sizes.maxThreadLines).map { message ->
                    Block(text = message.text, maxLines = 1, message = true, sender = message.sender)
                }
            is NotificationBody.Text -> listOf(Block(body.text, sizes.maxBodyLines))
            is NotificationBody.Custom ->
                if (body.content.embed) {
                    emptyList()
                } else {
                    body.content.lines.take(sizes.maxBodyLines).map { Block(it, maxLines = 1) }
                }
            NotificationBody.None -> emptyList()
        }
}
