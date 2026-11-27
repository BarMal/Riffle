package com.riffle.app.launcher.summon

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummonFavouritesRowTest {
    @Test
    fun aFewFavouritesKeepTheirFullSize() {
        assertEquals(SUMMON_FAVOURITE_ICON_SIZE, summonFavouriteIconSize(availableWidth = 360.dp, count = 4))
    }

    @Test
    fun manyFavouritesShrinkSoTheWholeRowFitsTheScreen() {
        val available = 328.dp
        val count = 7
        val size = summonFavouriteIconSize(available, count)
        assertTrue(size < SUMMON_FAVOURITE_ICON_SIZE)
        assertTrue(summonFavouritesRowWidth(size, count) <= available)
    }

    @Test
    fun noFavouritesNeedsNoRoom() {
        assertEquals(SUMMON_FAVOURITE_ICON_SIZE, summonFavouriteIconSize(availableWidth = 0.dp, count = 0))
    }
}
