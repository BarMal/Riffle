package com.riffle.app.screenshots

import com.riffle.app.launcher.stream.OpenCardCover
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device fixes, round 7, item 4: the collapsed front card gives way only to a layer that really draws it. */
internal class OpenCardCoverTest {
    @Test
    fun aLayerWithNoCardsDrawsNothingHere() {
        assertFalse(OpenCardCover.drawsHere(layerOwnedHere = true, layerCardIds = emptyList()))
    }

    @Test
    fun aLayerOfAnotherStackDrawsNothingHere() {
        assertFalse(OpenCardCover.drawsHere(layerOwnedHere = false, layerCardIds = listOf("a")))
    }

    @Test
    fun aLayerOfThisStackWithCardsDrawsHere() {
        assertTrue(OpenCardCover.drawsHere(layerOwnedHere = true, layerCardIds = listOf("a")))
    }

    @Test
    fun theFrontCardIsHiddenOnlyWhileTheLayerDrawsThatCardWithSomeOpacity() {
        val ids = listOf("a", "b")
        assertTrue(OpenCardCover.hidesFront(true, ids, "a", 1f))
        assertTrue(OpenCardCover.hidesFront(true, ids, "b", 0.1f))
        assertFalse("another card id", OpenCardCover.hidesFront(true, ids, "c", 1f))
        assertFalse("not drawn (alpha 0)", OpenCardCover.hidesFront(true, ids, "a", 0f))
        assertFalse("no layer here", OpenCardCover.hidesFront(false, ids, "a", 1f))
        assertFalse("empty layer", OpenCardCover.hidesFront(true, emptyList(), "a", 1f))
    }
}
