package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun updatesDockNotificationCardVisibilityWithoutChangingItems() {
        val phone = appShortcut(id = "phone")
        val layout = layoutWithDockItems(phone)

        val result = engine.setDockNotificationCardsEnabled(layout = layout, enabled = false)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(false, updated.layout.dock.showNotificationCards)
        assertEquals(listOf(phone.id), updated.layout.dock.items.map { item -> item.id })
    }

    @Test
    fun updatesDockExpandabilityWithoutChangingItems() {
        val phone = appShortcut(id = "phone")
        val layout = layoutWithDockItems(phone)

        val result = engine.setDockExpandable(layout = layout, expandable = false)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(false, updated.layout.dock.isExpandable)
        assertEquals(listOf(phone.id), updated.layout.dock.items.map { item -> item.id })
    }

    @Test
    fun updatesDockExpandAffordance() {
        val result =
            engine.setDockExpandAffordance(
                layout = HomeLayoutDefaults.standard(),
                affordance = DockExpandAffordance.BUTTON,
            )

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(DockExpandAffordance.BUTTON, updated.layout.dock.expandAffordance)
    }

    @Test
    fun aDockExpandsBySwipeUntilTheUserSaysOtherwise() {
        // Both defaults exist to leave today's dock exactly as it was: it expands, and the swipe
        // is how you do it.
        val dock = HomeLayoutDefaults.standard().dock

        assertEquals(true, dock.isExpandable)
        assertEquals(DockExpandAffordance.GESTURE, dock.expandAffordance)
    }

    @Test
    fun aSeededPanelTakesTheLayoutsOwnGridWidth() {
        // So it reads as a short home page rather than a differently-proportioned thing.
        val layout = HomeLayoutDefaults.standard()

        val result = engine.setDockPanelEnabled(layout = layout, enabled = true)

        val panel = assertNotNull(assertIs<DockEditResult.Updated>(result).layout.dock.panel)
        assertEquals(layout.settings.grid.dimensions.columns, panel.grid.columns)
        assertEquals(2, panel.grid.rows)
        assertEquals(emptyList(), panel.items)
    }

    @Test
    fun enablingAPanelThatAlreadyExistsLeavesItsContentsAlone() {
        // Re-running the enable path must not quietly wipe what the user placed.
        val seeded =
            assertIs<DockEditResult.Updated>(
                engine.setDockPanelEnabled(layout = HomeLayoutDefaults.standard(), enabled = true),
            ).layout
        val placed = appShortcut(id = "clock")
        val withContents = seeded.copy(dock = seeded.dock.copy(panel = seeded.dock.panel?.copy(items = listOf(placed))))

        val result = engine.setDockPanelEnabled(layout = withContents, enabled = true)

        assertEquals(
            listOf(placed.id),
            assertIs<DockEditResult.Updated>(result).layout.dock.panel?.items?.map { item -> item.id },
        )
    }

    @Test
    fun disablingThePanelRemovesIt() {
        val seeded =
            assertIs<DockEditResult.Updated>(
                engine.setDockPanelEnabled(layout = HomeLayoutDefaults.standard(), enabled = true),
            ).layout

        val result = engine.setDockPanelEnabled(layout = seeded, enabled = false)

        assertNull(assertIs<DockEditResult.Updated>(result).layout.dock.panel)
    }

    @Test
    fun aDockHasNoPanelUntilOneIsAskedFor() {
        assertNull(HomeLayoutDefaults.standard().dock.panel)
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
    fun updatesDockCapacityBelowCurrentItemCount() {
        val layout = layoutWithDockItems(appShortcut(id = "phone"), appShortcut(id = "camera"))

        val result = engine.setDockCapacity(layout = layout, capacity = 1)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(1, updated.layout.dock.capacity)
        assertEquals(layout.dock.items, updated.layout.dock.items)
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
    fun updatesDockVisualEffectWithoutChangingItems() {
        val phone = appShortcut(id = "phone")
        val result = engine.setDockVisualEffect(layout = layoutWithDockItems(phone), effect = DockVisualEffect.OUTLINED)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(DockVisualEffect.OUTLINED, updated.layout.dock.visualEffect)
        assertEquals(listOf(phone.id), updated.layout.dock.items.map { item -> item.id })
    }

    @Test
    fun updatesDockBackgroundSizing() {
        val result =
            engine.setDockBackgroundSizing(
                layout = HomeLayoutDefaults.standard(),
                sizing = DockBackgroundSizing.FIXED,
            )

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(DockBackgroundSizing.FIXED, updated.layout.dock.backgroundSizing)
    }

    @Test
    fun updatesDockAlignment() {
        val result =
            engine.setDockAlignment(
                layout = HomeLayoutDefaults.standard(),
                alignment = DockAlignment.END,
            )

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(DockAlignment.END, updated.layout.dock.alignment)
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
    fun updatesDockCornerRadius() {
        val result = engine.setDockCornerRadius(layout = HomeLayoutDefaults.standard(), cornerRadiusDp = 20)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(20, updated.layout.dock.cornerRadiusDp)
    }

    @Test
    fun updatesGridToDockControlsSpacing() {
        val result = engine.setDockHomeControlsSpacing(layout = HomeLayoutDefaults.standard(), spacingDp = 16)

        val updated = assertIs<DockEditResult.Updated>(result)
        assertEquals(16, updated.layout.dock.homeControlsSpacingDp)
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
