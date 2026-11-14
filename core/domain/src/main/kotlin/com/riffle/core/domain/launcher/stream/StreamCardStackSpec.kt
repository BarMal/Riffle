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

    /** The clear space between a peeking card's strip and the card in front of it, so the two fills never touch. */
    const val GAP_DP = 4f

    /** How many cards peek above the front one. One further is on its way out (fading) and is not drawn at rest. */
    const val VISIBLE_PEEKS = 3

    /** Each peeking card is this much smaller than the one in front of it (width and height, from its top centre). */
    const val SCALE_STEP = 0.04f

    /** The front card shrinks to this as it sinks into the Dock while it fades. */
    const val SINK_SCALE = 0.92f

    /** The most cards one stack ever composes: the sinking card, the front card, the peeks and the one fading in. */
    const val MAX_COMPOSED_CARDS: Int = 1 + 1 + VISIBLE_PEEKS + 1
}
