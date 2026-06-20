package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HomeLayoutDefaultsTest {
    @Test
    fun standardLayoutNamesTheStandardAppDrawerModeExplicitly() {
        val layout = HomeLayoutDefaults.standard()

        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, layout.viewMode)
    }

    @Test
    fun standardLayoutStartsWithOneEmptyHomePage() {
        val layout = HomeLayoutDefaults.standard()

        assertEquals(1, layout.pages.size)
        assertSame(layout.pages.first(), layout.selectedPage)
        assertEquals(0, layout.selectedPageIndex)
        assertEquals(LauncherPageType.Home, layout.selectedPage.type)
        assertEquals(HomeEditMode.Browsing, layout.editMode)
        assertTrue(layout.selectedPage.items.isEmpty())
    }

    @Test
    fun standardHomePageUsesConventionalPhoneGrid() {
        val layout = HomeLayoutDefaults.standard()

        assertEquals(GridDimensions(columns = 4, rows = 5), layout.selectedPage.grid)
        assertEquals(GridDimensions(columns = 4, rows = 5), layout.settings.grid.dimensions)
        assertEquals(20, layout.selectedPage.grid.cellCount)
    }

    @Test
    fun standardDockHasFiveEmptySlots() {
        val layout = HomeLayoutDefaults.standard()

        assertEquals(5, layout.dock.capacity)
        assertEquals(5, layout.dock.availableSlots)
        assertTrue(layout.dock.items.isEmpty())
    }
}
