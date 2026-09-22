package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class GridDimensionsDockAdjustmentTest {
    private val dimensions = GridDimensions(columns = 5, rows = 6)

    @Test
    fun sideDockReservesOneColumnFromTheVisibleGrid() {
        assertEquals(
            GridDimensions(columns = 4, rows = 6),
            dimensions.workspaceGridFor(dock(position = DockPosition.LEFT)),
        )
        assertEquals(
            GridDimensions(columns = 4, rows = 6),
            dimensions.workspaceGridFor(dock(position = DockPosition.RIGHT)),
        )
    }

    @Test
    fun horizontalOrDisabledOrUnsetDockLeavesTheGridUntouched() {
        assertEquals(dimensions, dimensions.workspaceGridFor(dock(position = DockPosition.TOP)))
        assertEquals(dimensions, dimensions.workspaceGridFor(dock(position = DockPosition.BOTTOM)))
        assertEquals(dimensions, dimensions.workspaceGridFor(dock(position = null)))
        assertEquals(
            dimensions,
            dimensions.workspaceGridFor(dock(position = DockPosition.LEFT, isEnabled = false)),
        )
    }

    @Test
    fun rawGridForIsTheExactInverseOfWorkspaceGridFor() {
        DockPosition.entries.plus(null).forEach { position ->
            listOf(true, false).forEach { isEnabled ->
                val candidate = dock(position = position, isEnabled = isEnabled)

                val visible = dimensions.workspaceGridFor(candidate)
                val roundTripped = visible.rawGridFor(candidate)

                assertEquals(
                    dimensions,
                    roundTripped,
                    "raw -> visible -> raw should round-trip for position=$position isEnabled=$isEnabled",
                )
            }
        }
    }

    @Test
    fun rawGridForAddsBackTheColumnASideDockReserves() {
        val visibleWithSideDock = GridDimensions(columns = 4, rows = 6)

        assertEquals(
            GridDimensions(columns = 5, rows = 6),
            visibleWithSideDock.rawGridFor(dock(position = DockPosition.LEFT)),
        )
    }

    private fun dock(
        position: DockPosition?,
        isEnabled: Boolean = true,
    ): DockModel =
        DockModel(
            capacity = 5,
            position = position,
            isEnabled = isEnabled,
        )
}
