package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.workspaceGrid
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePageIdsTest {
    @Test
    fun newHomePageMatchesTheWorkspaceWidthRatherThanTheStoredPreDockWidth() {
        // A side dock reserves a column from the pages beside it, so a new page created at the
        // raw, pre-dock width would overflow the width actually available to it.
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(position = DockPosition.LEFT),
            )

        val page = layout.newHomePage()

        assertEquals(layout.workspaceGrid, page.grid)
        assertEquals(layout.settings.grid.dimensions.columns - 1, page.grid.columns)
    }

    @Test
    fun newHomePageMatchesTheFullGridWhenTheDockDoesNotReserveAColumn() {
        val layout = HomeLayoutDefaults.standard()

        val page = layout.newHomePage()

        assertEquals(layout.settings.grid.dimensions, page.grid)
    }
}
