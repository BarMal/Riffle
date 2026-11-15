package com.riffle.core.domain.launcher.stream

/**
 * The geometry of a page's card stack (ADR 0003, Amendment 1, "Browse", and Amendment 3, "Motion"), in dp. The stack is
 * anchored to the Dock: the front card sits nearest the thumb and older cards peek above it, each one's top strip
 * showing. The geometry as a function of position is [StackGeometry]; this holds the tokens it is built from, kept here
 * and never as literals in the UI.
 *
 * The card height and the pitch are Calm's card pages (`CalmCardSpec`: 168 dp cards, a 56 dp step).
 */
object StreamCardStackSpec {
    /** The base card height; media, an article with a lead image and the tasks card are taller (their own content). */
    const val CARD_HEIGHT_DP = 168f

    /** Distance between neighbouring cards' tops: how much of the next card peeks out above the one in front. */
    const val PITCH_DP = 56f

    /**
     * The clear space between a peeking card's strip and the card in front of it: none. A strip tucks under the card
     * in front (it is drawn down to and around that card's rounded top and cut along its outline), so the stack reads
     * as overlapping cards and not as sliced slabs; the two fills still never overlap, since the cut is a clip.
     */
    const val GAP_DP = 0f

    /**
     * How much of a card its strip shows down its middle: the pitch (less the gap), from the card's top edge. Every
     * card's header (an icon or avatar and a one-line title) lies wholly inside it, whatever the kind of card. Beside
     * the rounded top corners of the card in front a little more of the card shows.
     */
    const val STRIP_HEIGHT_DP = PITCH_DP - GAP_DP

    /** How many cards peek above the front one. One further is on its way out (fading) and is not drawn at rest. */
    const val VISIBLE_PEEKS = 3

    /** Each peeking card is this much smaller than the one in front of it (width and height, from its top centre). */
    const val SCALE_STEP = 0.04f

    /** The front card shrinks to this as it sinks into the Dock while it fades. */
    const val SINK_SCALE = 0.92f

    /** The most cards one stack ever composes: the sinking card, the front card, the peeks and the one fading in. */
    const val MAX_COMPOSED_CARDS: Int = 1 + 1 + VISIBLE_PEEKS + 1
}

/**
 * The sizes a stack of cards is drawn at on a posture (batch 7): the folded phone's are the ones the stack has always
 * had ([FOLDED]); the unfolded column, which is as tall as the Dock, draws bigger cards with more of the cards behind
 * showing ([UNFOLDED]). Pure, so the rules are tested and the UI only reads them.
 */
data class StreamCardSizes(
    /** The distance between neighbouring cards' tops: how much of the next card peeks out above the one in front. */
    val pitchDp: Float,
    /** The most cards that peek above the front one. */
    val maxPeeks: Int,
    /** Whether the number of peeks is what fits the stack's height (up to [maxPeeks]) rather than always [maxPeeks]. */
    val peeksFitHeight: Boolean,
    /** Each peeking card is this much narrower than the one in front of it. */
    val scaleStep: Float,
    /** The text of a card is this much bigger than the theme's (the typography the card is drawn in). */
    val textScale: Float,
    /** The most a text-driven card (a notification, an event, an article with no image) may be tall, against folded. */
    val maxHeightFactor: Float,
    /** The height of a card with fixed content (the player, an article with an image, the checklist) against folded. */
    val fixedHeightFactor: Float,
    /** The most lines of body a notification's browse card shows, and the most messages of a conversation. */
    val maxBodyLines: Int,
    val maxThreadLines: Int,
) {
    /** How many cards peek above the front card in a stack of [count] cards in [areaHeightDp] of room. */
    fun peeksFor(
        count: Int,
        areaHeightDp: Float,
        frontHeightDp: Float,
        bottomInsetDp: Float,
    ): Int {
        val behind = (count - 1).coerceAtLeast(0)
        if (!peeksFitHeight) return behind.coerceAtMost(maxPeeks)
        // At rest, the strips stand above the tallest card; with a card open they stand above it, and it needs at
        // least [OPEN_CARD_MIN_DP] of room under them (below that the open card hides them instead).
        val restRoom = areaHeightDp - bottomInsetDp - frontHeightDp - TOP_ROOM_DP
        val openRoom = areaHeightDp - OPEN_TOP_PADDING_DP - bottomInsetDp - OPEN_CARD_MIN_DP
        val room = minOf(restRoom, openRoom)
        val fits = if (room <= 0f) 0 else (room / pitchDp).toInt()
        return behind.coerceAtMost(fits.coerceIn(StreamCardStackSpec.VISIBLE_PEEKS.coerceAtMost(maxPeeks), maxPeeks))
    }

    /** The height of a card whose folded height is [baseDp]: [textDriven] cards grow to the larger maximum. */
    fun cardHeightDp(
        baseDp: Float,
        textDriven: Boolean,
    ): Float = baseDp * if (textDriven) maxHeightFactor else fixedHeightFactor

    companion object {
        /** What is left above the strips: a little air under the top of the area. */
        const val TOP_ROOM_DP = 16f

        /** The least room an open card is given before the cards behind it stop peeking out above it. */
        const val OPEN_CARD_MIN_DP = 240f

        /** What the open card's layer keeps above its area (the page's own top padding). */
        const val OPEN_TOP_PADDING_DP = 8f

        /** The folded phone's sizes: exactly what the stack always drew. */
        val FOLDED =
            StreamCardSizes(
                pitchDp = StreamCardStackSpec.PITCH_DP,
                maxPeeks = StreamCardStackSpec.VISIBLE_PEEKS,
                peeksFitHeight = false,
                scaleStep = StreamCardStackSpec.SCALE_STEP,
                textScale = 1f,
                maxHeightFactor = 1f,
                fixedHeightFactor = 1f,
                maxBodyLines = BrowseCardHeight.MAX_BODY_LINES,
                maxThreadLines = BrowseCardHeight.MAX_THREAD_LINES,
            )

        /** The unfolded column's: a larger front card, a taller strip, more lines, up to six strips as they fit. */
        val UNFOLDED =
            StreamCardSizes(
                pitchDp = 64f,
                maxPeeks = 6,
                peeksFitHeight = true,
                scaleStep = 0.025f,
                textScale = 1.15f,
                maxHeightFactor = 1.35f,
                fixedHeightFactor = 1.15f,
                maxBodyLines = 6,
                maxThreadLines = 4,
            )

        fun forPosture(unfolded: Boolean): StreamCardSizes = if (unfolded) UNFOLDED else FOLDED
    }
}
