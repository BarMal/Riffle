package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy

/**
 * The geometry of a page's vertical card stack (ADR 0003, Views), taken from Calm's card pages: its `CalmCardSpec` is a
 * 168 dp card stacked with a 56 dp step, and its default tuning shows the focused card, two cards ahead and two
 * behind with the focused
 * card sitting high in the page (20 percent). Cards in front are the first (newest) item, as in Calm.
 *
 * Only a bounded number of cards is composed whatever the page holds ([MAX_COMPOSED_CARDS] at most).
 */
object StreamCardStackSpec {
    /** The base card height; media, an article with a lead image and the tasks card are taller (their own content). */
    const val CARD_HEIGHT_DP = 168f

    /** Distance between neighbouring cards' tops: how much of the next card peeks out below the focused one. */
    const val PITCH_DP = 56f

    /** How many cards stay visible after the focused one, and how many already scrolled past (Calm's defaults). */
    const val VISIBLE_AHEAD = 2
    const val VISIBLE_BEHIND = 2

    /**
     * Where the focused card sits in the page's stack area, as a fraction of the free height. Calm uses 20 percent of
     * the viewport; here it is 12 percent, because the page's own header already sits above.
     */
    const val PEAK_FRACTION = 0.12f

    /** Cards past the visible range that are composed so the next one is already there while a drag is live. */
    const val COMPOSED_MARGIN = 1

    /** Each card behind the focused one is a little smaller and fainter (the existing stack defaults). */
    const val SCALE_STEP = 0.04f
    const val ALPHA_STEP = 0.14f

    /** The most cards one stack ever composes. */
    const val MAX_COMPOSED_CARDS: Int = 1 + VISIBLE_AHEAD + VISIBLE_BEHIND + 2 * COMPOSED_MARGIN

    val policy: CardStackLayoutPolicy =
        CardStackLayoutPolicy(
            maxVisibleDepth = VISIBLE_AHEAD,
            scaleStep = SCALE_STEP,
            offsetStep = 0f,
            offsetDirection = 0f,
            alphaStep = ALPHA_STEP,
            verticalOffsetStep = PITCH_DP,
            reducedMotionScaleStep = 0f,
            reducedMotionOffsetStep = 0f,
            aboveFocusDepth = VISIBLE_BEHIND,
            composedDepthMargin = COMPOSED_MARGIN,
        )
}
