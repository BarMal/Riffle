package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which edges a layout can actually put its persistent strip on. One setting drives two surfaces,
 * and they do not place the same set.
 */
class PlaceableDockPositionsTest {
    @Test
    fun aCardsLayoutPlacesItsRailOnEveryEdge() {
        assertEquals(
            DockPosition.entries.toList(),
            LauncherViewMode.CARD_INTERFACE.placeableDockPositions,
        )
    }

    @Test
    fun everyOtherViewModePlacesTheHomeDockOnThreeEdges() {
        listOf(LauncherViewMode.STANDARD_APP_DRAWER, LauncherViewMode.HOME_SCREEN_LIBRARY).forEach { viewMode ->
            val placeable = viewMode.placeableDockPositions

            assertFalse(DockPosition.TOP in placeable, "$viewMode should not offer the top edge")
            assertTrue(DockPosition.LEADING in placeable)
            assertTrue(DockPosition.TRAILING in placeable)
            assertTrue(DockPosition.BOTTOM in placeable)
        }
    }

    @Test
    fun everyViewModeCanAlwaysPlaceTheBottomEdge() {
        // It is the fallback the home dock lands on, so no layout may be unable to choose it.
        LauncherViewMode.entries.forEach { viewMode ->
            assertTrue(DockPosition.BOTTOM in viewMode.placeableDockPositions, "$viewMode should offer the bottom edge")
        }
    }
}
