package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class DockModelTest {
    @Test
    fun capsAvailableSlotsAtZeroWhenDockOverflows() {
        val dock =
            DockModel(
                capacity = 1,
                items = listOf(widgetItem(id = "weather"), widgetItem(id = "calendar")),
            )

        assertEquals(0, dock.availableSlots)
    }

    @Test
    fun reportsAvailableSlotsWhenDockIsUnderCapacity() {
        val dock =
            DockModel(
                capacity = 4,
                items = listOf(widgetItem(id = "weather"), widgetItem(id = "calendar")),
            )

        assertEquals(2, dock.availableSlots)
    }

    private fun widgetItem(id: String): WidgetItem =
        WidgetItem(
            id = LauncherItemId(id),
            appWidgetId = HostedWidgetId(id.hashCode()),
            label = id,
        )
}
