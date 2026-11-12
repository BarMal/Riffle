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

    @Test
    fun theEarlierCardSettingsMapToLiveAppSlots() {
        assertEquals(4, dockLiveAppSlotsFromLegacy(showNotificationCards = true, notificationSlotCount = 4))
        assertEquals(DEFAULT_DOCK_LIVE_APP_SLOTS, dockLiveAppSlotsFromLegacy(true, null))
        assertEquals(MAX_DOCK_LIVE_APP_SLOTS, dockLiveAppSlotsFromLegacy(true, 40))
        assertEquals(0, dockLiveAppSlotsFromLegacy(showNotificationCards = false, notificationSlotCount = 4))
        assertEquals(DEFAULT_DOCK_LIVE_APP_SLOTS, dockLiveAppSlotsFromLegacy(null, null))
    }

    private fun widgetItem(id: String): WidgetItem =
        WidgetItem(
            id = LauncherItemId(id),
            appWidgetId = HostedWidgetId(id.hashCode()),
            label = id,
        )
}
