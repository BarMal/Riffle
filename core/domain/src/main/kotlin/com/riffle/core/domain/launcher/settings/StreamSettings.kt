package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.designsystem.CardTint

/**
 * Durable Stream presentation preferences (ADR 0003, Amendment 1, "Settings this adds (Stream)"). The settings page for
 * them is a later slice; the model, its defaults and its codec are here so the Stream already reads them.
 */
data class StreamSettings(
    /** How much of its app's icon colour a card's fill takes, in percent (see [CardTint]). */
    val cardTintPercent: Int = CardTint.DEFAULT_STRENGTH_PERCENT,
    /** Whether a card also shows its app's colour as a soft glow along its top edge. Off by default. */
    val cardTopGlow: Boolean = false,
    /** Where an open card sits. */
    val openCardPlacement: OpenCardPlacement = OpenCardPlacement.CENTRE,
    /** Where an open card's actions sit. */
    val cardActionsPlacement: CardActionsPlacement = CardActionsPlacement.IN_DOCK,
)

/** Where an open card sits on the screen (the first is the default). */
enum class OpenCardPlacement {
    /** The card grows where it is in the stack's area and the stack's room is centred around it. */
    CENTRE,

    /** The card sits just above the Dock. */
    ABOVE_DOCK,

    /**
     * The card grows out of the Dock. Deferred: it is drawn as [ABOVE_DOCK] until the Dock-origin growth is built (the
     * choice is stored so it is kept when it is).
     */
    GROWS_OUT_OF_DOCK,
}

/** Where an open card's actions sit (the first is the default). */
enum class CardActionsPlacement {
    /** A row above the Dock's icons. */
    IN_DOCK,

    /** On the card itself. */
    ON_CARD,
}

fun StreamSettings.coerced(): StreamSettings = copy(cardTintPercent = CardTint.coercedStrength(cardTintPercent))
