package com.riffle.app.launcher.stream

/**
 * When the collapsed front card gives way to the open card's layer (device fixes, round 7, item 4): only while the
 * layer really draws that card. A layer that has no cards, that belongs to another stack or page, or whose card is not
 * drawn (alpha 0) must leave the front card where it is, with its neighbours as strips, or the page shows tabs and no
 * card at all. Pure, so the rule is tested without a screen.
 */
internal object OpenCardCover {
    /**
     * Whether the layer of this stack draws cards at all: [layerOwnedHere] (the published layer is this stack's) and
     * [layerCardIds] not empty. The tabs slim, the stack moves to the card's frame and takes no input only then.
     */
    fun drawsHere(
        layerOwnedHere: Boolean,
        layerCardIds: List<String>,
    ): Boolean = layerOwnedHere && layerCardIds.isNotEmpty()

    /** Whether the front card [frontId] is hidden: the layer draws that very card, with some opacity. */
    fun hidesFront(
        drawsHere: Boolean,
        layerCardIds: List<String>,
        frontId: String,
        layerAlpha: Float,
    ): Boolean = drawsHere && frontId in layerCardIds && layerAlpha > 0f
}
