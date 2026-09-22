package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [HomePageEngine.updateGridDimensions] split out of `HomePageEngineTest` so that file doesn't
 * grow past detekt's size limit -- this one case needs a dock-carrying layout the rest of that
 * file's fixtures don't.
 */
class HomePageEngineGridDimensionsTest {
    private val engine = HomePageEngine()

    @Test
    fun updateGridDimensionsTreatsTheInputAsTheVisibleGridWithASideDock() {
        val layout = HomeLayoutDefaults.standard()
        val layoutWithSideDock =
            layout.copy(
                dock = layout.dock.copy(position = DockPosition.LEFT),
                pages = listOf(LauncherPage(id = LauncherPageId("home"), grid = GridDimensions(columns = 4, rows = 5))),
            )

        val result =
            engine.updateGridDimensions(
                layout = layoutWithSideDock,
                dimensions = GridDimensions(columns = 5, rows = 6),
            )

        val updated = assertIs<HomePageEditResult.Updated>(result)
        // Every page is sized to exactly what was asked for -- that's what's on screen.
        assertEquals(
            listOf(GridDimensions(columns = 5, rows = 6)),
            updated.layout.pages.map { page -> page.grid },
        )
        // The dock still owes itself a column, so the stored (pre-dock) number is one column
        // wider than what's visible -- reading it back through workspaceGrid reproduces the 5
        // columns the user just asked for, instead of silently losing the dock's reservation.
        assertEquals(GridDimensions(columns = 6, rows = 6), updated.layout.settings.grid.dimensions)
        assertEquals(GridDimensions(columns = 5, rows = 6), updated.layout.workspaceGrid)
    }
}
