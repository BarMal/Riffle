package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Placing a widget on the dock's panel. The panel is a grid like a home page, but it is not one of
 * the layout's pages, so it has its own entry point into the same placement search.
 */
class WidgetEngineDockPanelTest {
    private val engine = WidgetEngine()

    @Test
    fun placesAWidgetInThePanelsFirstFreeCell() {
        val result =
            engine.addWidgetToDockPanel(
                layout = panelLayout(),
                hostedWidgetId = HostedWidgetId(42),
                label = "Clock",
            )

        val panel = assertIs<WidgetEditResult.Updated>(result).layout.dock.panel
        val widget = assertIs<WidgetItem>(panel?.items?.single())
        assertEquals(HostedWidgetId(42), widget.appWidgetId)
        assertEquals(GridCell(column = 0, row = 0), widget.placement?.cell)
    }

    @Test
    fun honoursARequestedCell() {
        val result =
            engine.addWidgetToDockPanel(
                layout = panelLayout(),
                hostedWidgetId = HostedWidgetId(42),
                label = "Clock",
                targetCell = GridCell(column = 2, row = 1),
            )

        val panel = assertIs<WidgetEditResult.Updated>(result).layout.dock.panel
        assertEquals(GridCell(column = 2, row = 1), panel?.items?.single()?.placement?.cell)
    }

    @Test
    fun shrinksAWidgetThatIsTallerThanThePanel() {
        // The panel is two rows deep, so a three-row preference has to come down or go nowhere.
        val result =
            engine.addWidgetToDockPanel(
                layout = panelLayout(),
                hostedWidgetId = HostedWidgetId(42),
                label = "Clock",
                preferredSpan = GridSpan(columns = 2, rows = 3),
            )

        val updated = assertIs<WidgetEditResult.Updated>(result)
        assertEquals(GridSpan(columns = 2, rows = 2), updated.placedSpan)
        assertEquals(GridSpan(columns = 2, rows = 2), updated.layout.dock.panel?.items?.single()?.placement?.span)
    }

    @Test
    fun leavesTheDocksOwnSlotsAndThePagesAlone() {
        val layout = panelLayout()

        val result = engine.addWidgetToDockPanel(layout = layout, hostedWidgetId = HostedWidgetId(42), label = "Clock")

        val updated = assertIs<WidgetEditResult.Updated>(result).layout
        assertEquals(layout.dock.items, updated.dock.items)
        assertEquals(layout.pages, updated.pages)
    }

    @Test
    fun rejectsADockWithNoPanel() {
        val result =
            engine.addWidgetToDockPanel(
                layout = HomeLayoutDefaults.standard(),
                hostedWidgetId = HostedWidgetId(42),
                label = "Clock",
            )

        assertEquals(
            PlacementRejectionReason.ITEM_NOT_FOUND,
            assertIs<WidgetEditResult.Rejected>(result).reason,
        )
        assertNull(HomeLayoutDefaults.standard().dock.panel)
    }

    @Test
    fun rejectsAWidgetTheLayoutAlreadyHostsElsewhere() {
        // Including one already on the panel -- the same host ID must not be rendered twice.
        val layout = panelLayout()
        val seeded =
            assertIs<WidgetEditResult.Updated>(
                engine.addWidgetToDockPanel(layout = layout, hostedWidgetId = HostedWidgetId(42), label = "Clock"),
            ).layout

        val result = engine.addWidgetToDockPanel(layout = seeded, hostedWidgetId = HostedWidgetId(42), label = "Clock")

        assertEquals(
            PlacementRejectionReason.DUPLICATE_ITEM_ID,
            assertIs<WidgetEditResult.Rejected>(result).reason,
        )
    }

    @Test
    fun rejectsAPanelWithNoRoomLeft() {
        val full =
            (0 until PANEL_COLUMNS * PANEL_ROWS).fold(panelLayout()) { layout, index ->
                assertIs<WidgetEditResult.Updated>(
                    engine.addWidgetToDockPanel(
                        layout = layout,
                        hostedWidgetId = HostedWidgetId(index),
                        label = "Filler",
                    ),
                ).layout
            }

        val result = engine.addWidgetToDockPanel(layout = full, hostedWidgetId = HostedWidgetId(99), label = "Clock")

        assertEquals(
            PlacementRejectionReason.NO_AVAILABLE_CELL,
            assertIs<WidgetEditResult.Rejected>(result).reason,
        )
    }

    private fun panelLayout(): HomeLayout =
        HomeLayoutDefaults.standard().let { defaults ->
            defaults.copy(
                dock =
                    defaults.dock.copy(
                        panel =
                            LauncherPage(
                                id = LauncherPageId("dock-panel"),
                                grid = GridDimensions(columns = PANEL_COLUMNS, rows = PANEL_ROWS),
                            ),
                    ),
            )
        }

    private companion object {
        private const val PANEL_COLUMNS = 4
        private const val PANEL_ROWS = 2
    }
}
