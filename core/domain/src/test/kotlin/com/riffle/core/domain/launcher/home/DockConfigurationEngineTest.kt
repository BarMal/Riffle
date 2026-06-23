package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DockConfigurationEngineTest {
    private val engine = DockConfigurationEngine()

    @Test
    fun updatesDockVisibilityWithoutChangingItems() {
        val phone = appShortcut(id = "phone")
        val layout = layoutWithDockItems(phone)

        val result = engine.setDockEnabled(layout = layout, enabled = false)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(false, updated.layout.dock.isEnabled)
        assertEquals(listOf(phone.id), updated.layout.dock.items.map { item -> item.id })
    }

    @Test
    fun updatesDockCapacity() {
        val result = engine.setDockCapacity(layout = HomeLayoutDefaults.standard(), capacity = 7)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(7, updated.layout.dock.capacity)
    }

    @Test
    fun rejectsNegativeDockCapacity() {
        val result = engine.setDockCapacity(layout = HomeLayoutDefaults.standard(), capacity = -1)

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.INVALID_CAPACITY, rejected.reason)
    }

    @Test
    fun rejectsDockCapacityBelowCurrentItemCount() {
        val layout = layoutWithDockItems(appShortcut(id = "phone"), appShortcut(id = "camera"))

        val result = engine.setDockCapacity(layout = layout, capacity = 1)

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.CAPACITY_BELOW_ITEM_COUNT, rejected.reason)
    }

    @Test
    fun updatesDockIconSize() {
        val result = engine.setDockIconSize(layout = HomeLayoutDefaults.standard(), sizeDp = 52)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(52, updated.layout.dock.iconSizeDp)
    }

    @Test
    fun rejectsDockIconSizeBelowMinimum() {
        val result = engine.setDockIconSize(layout = HomeLayoutDefaults.standard(), sizeDp = MIN_DOCK_ICON_SIZE_DP - 1)

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.INVALID_ICON_SIZE, rejected.reason)
    }

    @Test
    fun rejectsDockIconSizeAboveMaximum() {
        val result = engine.setDockIconSize(layout = HomeLayoutDefaults.standard(), sizeDp = MAX_DOCK_ICON_SIZE_DP + 1)

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.INVALID_ICON_SIZE, rejected.reason)
    }

    @Test
    fun updatesDockBackgroundAlpha() {
        val result = engine.setDockBackgroundAlpha(layout = HomeLayoutDefaults.standard(), alphaPercent = 85)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(85, updated.layout.dock.backgroundAlphaPercent)
    }

    @Test
    fun rejectsDockBackgroundAlphaBelowMinimum() {
        val result =
            engine.setDockBackgroundAlpha(
                layout = HomeLayoutDefaults.standard(),
                alphaPercent = MIN_DOCK_BACKGROUND_ALPHA_PERCENT - 1,
            )

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.INVALID_BACKGROUND_ALPHA, rejected.reason)
    }

    @Test
    fun rejectsDockBackgroundAlphaAboveMaximum() {
        val result =
            engine.setDockBackgroundAlpha(
                layout = HomeLayoutDefaults.standard(),
                alphaPercent = MAX_DOCK_BACKGROUND_ALPHA_PERCENT + 1,
            )

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.INVALID_BACKGROUND_ALPHA, rejected.reason)
    }

    @Test
    fun updatesDockItemSpacing() {
        val result = engine.setDockItemSpacing(layout = HomeLayoutDefaults.standard(), spacingDp = 14)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(14, updated.layout.dock.itemSpacingDp)
    }

    @Test
    fun rejectsDockItemSpacingBelowMinimum() {
        val result =
            engine.setDockItemSpacing(
                layout = HomeLayoutDefaults.standard(),
                spacingDp = MIN_DOCK_ITEM_SPACING_DP - 1,
            )

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.INVALID_ITEM_SPACING, rejected.reason)
    }

    @Test
    fun rejectsDockItemSpacingAboveMaximum() {
        val result =
            engine.setDockItemSpacing(
                layout = HomeLayoutDefaults.standard(),
                spacingDp = MAX_DOCK_ITEM_SPACING_DP + 1,
            )

        val rejected = assertIs<DockEditResult.Rejected>(result)
        assertEquals(DockEditRejectionReason.INVALID_ITEM_SPACING, rejected.reason)
    }

    private fun layoutWithDockItems(vararg items: AppShortcutItem): HomeLayout =
        HomeLayoutDefaults.standard().copy(
            dock = DockModel(capacity = 5, items = items.toList()),
        )

    private fun appShortcut(id: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
        )
}
