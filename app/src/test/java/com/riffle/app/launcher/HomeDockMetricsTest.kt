package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDockMetricsTest {
    @Test
    fun defaultIconSizeKeepsExistingDockHeight() {
        assertEquals(76, dockHeightDp(iconSizeDp = 44))
    }

    @Test
    fun largerIconSizeIncreasesDockHeight() {
        assertEquals(96, dockHeightDp(iconSizeDp = 64))
    }

    @Test
    fun dockContentViewportUsesOccupiedIconWidthAndSpacing() {
        assertEquals(
            212,
            dockContentViewportWidthDp(
                slotCount = 4,
                iconSizeDp = 44,
                itemSpacingDp = 12,
            ),
        )
    }

    @Test
    fun dockContentViewportCapsAtDockInteriorWidth() {
        assertEquals(
            532,
            dockContentViewportWidthDp(
                slotCount = 20,
                iconSizeDp = 56,
                itemSpacingDp = 24,
            ),
        )
    }

    @Test
    fun dockContentViewportCapsAtAvailableInteriorWidth() {
        assertEquals(
            292,
            dockContentViewportWidthDp(
                slotCount = 5,
                iconSizeDp = 56,
                itemSpacingDp = 24,
                availableDockWidthDp = 320,
            ),
        )
    }

    @Test
    fun dockSlotRenderMetricsPreservesConfiguredSpacingWhenFiveSlotsFit() {
        val metrics =
            dockSlotRenderMetrics(
                slotCount = 5,
                iconSizeDp = 48,
                itemSpacingDp = 10,
                availableContentWidthDp = 280,
            )

        assertEquals(
            DockSlotRenderMetrics(
                iconSizeDp = 48,
                itemSpacingDp = 10,
                overflowMode = DockOverflowMode.Fits,
            ),
            metrics,
        )
        assertEquals(280, (5 * metrics.iconSizeDp) + (4 * metrics.itemSpacingDp))
    }

    @Test
    fun dockSlotRenderMetricsCompactsSpacingForFiveSlotsOnNarrowWidth() {
        val metrics =
            dockSlotRenderMetrics(
                slotCount = 5,
                iconSizeDp = 48,
                itemSpacingDp = 10,
                availableContentWidthDp = 252,
            )

        assertEquals(
            DockSlotRenderMetrics(
                iconSizeDp = 48,
                itemSpacingDp = 3,
                overflowMode = DockOverflowMode.FitByCompaction,
            ),
            metrics,
        )
        assertEquals(252, dockSlotContentWidthDp(slotCount = 5, metrics = metrics))
    }

    @Test
    fun dockSlotRenderMetricsCompactsIconSizeForFiveSlotsOnFoldedWidth() {
        val metrics =
            dockSlotRenderMetrics(
                slotCount = 5,
                iconSizeDp = 56,
                itemSpacingDp = 24,
                availableContentWidthDp = 252,
            )

        assertEquals(
            DockSlotRenderMetrics(
                iconSizeDp = 50,
                itemSpacingDp = 0,
                overflowMode = DockOverflowMode.FitByCompaction,
            ),
            metrics,
        )
        assertEquals(250, dockSlotContentWidthDp(slotCount = 5, metrics = metrics))
    }

    @Test
    fun dockSlotRenderMetricsPreservesConfiguredMetricsWhenHardMinimumCannotFit() {
        val metrics =
            dockSlotRenderMetrics(
                slotCount = 5,
                iconSizeDp = 48,
                itemSpacingDp = 10,
                availableContentWidthDp = 159,
            )

        assertEquals(
            DockSlotRenderMetrics(
                iconSizeDp = 48,
                itemSpacingDp = 10,
                overflowMode = DockOverflowMode.RequiresOverflowNavigation,
            ),
            metrics,
        )
        assertEquals(280, dockSlotContentWidthDp(slotCount = 5, metrics = metrics))
    }

    @Test
    fun dynamicDockContainerCapsAtAvailableWidthWhenContentOverflows() {
        assertEquals(
            320,
            dockContainerWidthDp(
                availableWidthDp = 320,
                slotCount = 5,
                iconSizeDp = 56,
                itemSpacingDp = 24,
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
            ),
        )
    }

    @Test
    fun dynamicDockContainerWrapsContentWhenContentFits() {
        assertEquals(
            240,
            dockContainerWidthDp(
                availableWidthDp = 320,
                slotCount = 4,
                iconSizeDp = 44,
                itemSpacingDp = 12,
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
            ),
        )
    }

    @Test
    fun fixedDockContainerCapsAtAvailableWidth() {
        assertEquals(
            320,
            dockContainerWidthDp(
                availableWidthDp = 320,
                slotCount = 5,
                iconSizeDp = 56,
                itemSpacingDp = 24,
                backgroundSizing = DockBackgroundSizing.FIXED,
            ),
        )
    }

    @Test
    fun emptyDockHasNoContentViewport() {
        assertEquals(
            0,
            dockContentViewportWidthDp(
                slotCount = 0,
                iconSizeDp = 44,
                itemSpacingDp = 12,
            ),
        )
    }

    @Test
    fun normalDockRendersConfiguredCapacitySoDockSettingsAffectWidth() {
        assertEquals(
            5,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 4,
                isEditing = false,
            ),
        )
    }

    @Test
    fun normalDockRendersPersistedItemSlotsAboveCapacity() {
        assertEquals(
            6,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 6,
                isEditing = false,
            ),
        )
    }

    @Test
    fun editingDockRendersCapacitySlots() {
        assertEquals(
            5,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 4,
                isEditing = true,
            ),
        )
    }

    @Test
    fun editingDockRendersConfiguredSlotsAboveSix() {
        assertEquals(
            8,
            dockRenderedSlotCount(
                capacity = 8,
                itemCount = 8,
                isEditing = true,
            ),
        )
    }

    @Test
    fun editingDockRendersPersistedItemSlotsAboveCapacity() {
        assertEquals(
            7,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 7,
                isEditing = true,
            ),
        )
    }

    @Test
    fun emptyDynamicDockRendersNoSlots() {
        assertEquals(
            0,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 0,
                isEditing = false,
            ),
        )
    }

    @Test
    fun fixedDockRendersConfiguredCapacityWhenNotEditing() {
        assertEquals(
            5,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 2,
                isEditing = false,
            ),
        )
    }

    @Test
    fun zeroCapacityDockRendersNoSlots() {
        assertEquals(
            0,
            dockRenderedSlotCount(
                capacity = 0,
                itemCount = 4,
                isEditing = false,
            ),
        )
    }

    @Test
    fun emptyDynamicDockHidesBackground() {
        assertEquals(
            false,
            dockBackgroundVisible(
                capacity = 5,
                itemCount = 0,
                isEditing = false,
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
            ),
        )
    }

    @Test
    fun emptyFixedDockShowsBackground() {
        assertEquals(
            true,
            dockBackgroundVisible(
                capacity = 5,
                itemCount = 0,
                isEditing = false,
                backgroundSizing = DockBackgroundSizing.FIXED,
            ),
        )
    }

    @Test
    fun zeroCapacityFixedDockShowsFullWidthBackground() {
        assertEquals(
            true,
            dockBackgroundVisible(
                capacity = 0,
                itemCount = 0,
                isEditing = false,
                backgroundSizing = DockBackgroundSizing.FIXED,
            ),
        )
    }

    @Test
    fun dockOverflowAffordanceHidesWhenContentDoesNotScroll() {
        assertEquals(
            DockOverflowAffordance(showStart = false, showEnd = false),
            DockOverflowAffordance(
                scrollOffsetPx = 0,
                maxScrollOffsetPx = 0,
            ),
        )
    }

    @Test
    fun dockOverflowAffordanceShowsEndAtScrollStart() {
        assertEquals(
            DockOverflowAffordance(showStart = false, showEnd = true),
            DockOverflowAffordance(
                scrollOffsetPx = 0,
                maxScrollOffsetPx = 72,
            ),
        )
    }

    @Test
    fun dockOverflowAffordanceShowsBothEdgesWhenScrolledBetweenEnds() {
        assertEquals(
            DockOverflowAffordance(showStart = true, showEnd = true),
            DockOverflowAffordance(
                scrollOffsetPx = 36,
                maxScrollOffsetPx = 72,
            ),
        )
    }

    @Test
    fun dockOverflowAffordanceShowsStartAtScrollEnd() {
        assertEquals(
            DockOverflowAffordance(showStart = true, showEnd = false),
            DockOverflowAffordance(
                scrollOffsetPx = 72,
                maxScrollOffsetPx = 72,
            ),
        )
    }

    @Test
    fun dockShelfUsesStandardSpringMotionWhenReducedMotionIsOff() {
        assertEquals(
            DockShelfMotionPolicy.StandardSpring,
            dockShelfMotionPolicy(reducedMotion = false),
        )
    }

    @Test
    fun dockShelfUsesShortTweenMotionWhenReducedMotionIsOn() {
        assertEquals(
            DockShelfMotionPolicy.ReducedShortTween,
            dockShelfMotionPolicy(reducedMotion = true),
        )
        assertEquals(80, REDUCED_MOTION_DOCK_SHELF_DURATION_MILLIS)
    }

    @Test
    fun dockOverflowRequiresMoreItemsThanCapacity() {
        assertEquals(true, dockHasOverflow(capacity = 5, itemCount = 6))
        assertEquals(false, dockHasOverflow(capacity = 5, itemCount = 5))
        assertEquals(false, dockHasOverflow(capacity = 0, itemCount = 1))
    }

    @Test
    fun expandedDockSplitsPrimaryAndOverflowShelfItemsOnTheMainDockGrid() {
        val items = (1..7).map { index -> widget("widget:$index", index) }
        val dock = DockModel(capacity = 5, items = items)

        assertEquals(items.take(5), dock.primaryDock(showShelf = true).items)
        assertEquals(items.drop(5), dock.overflowShelfDock().items)
        assertEquals(5, dock.overflowShelfDock().capacity)
    }

    @Test
    fun collapsedPrimaryDockKeepsAllItemsScrollable() {
        val items = (1..7).map { index -> widget("widget:$index", index) }
        val dock = DockModel(capacity = 5, items = items)

        assertEquals(items, dock.primaryDock(showShelf = false).items)
    }

    @Test
    fun dockShelfGestureExpandsOnDominantSwipeUpAndCollapsesOnDominantSwipeDown() {
        assertEquals(
            true,
            dockShelfGestureExpandedState(isExpanded = false, horizontalDragPx = 10f, verticalDragPx = -90f),
        )
        assertEquals(
            false,
            dockShelfGestureExpandedState(isExpanded = true, horizontalDragPx = 10f, verticalDragPx = 90f),
        )
        assertEquals(
            null,
            dockShelfGestureExpandedState(isExpanded = false, horizontalDragPx = 90f, verticalDragPx = -90f),
        )
        assertEquals(
            null,
            dockShelfGestureExpandedState(isExpanded = true, horizontalDragPx = 0f, verticalDragPx = -90f),
        )
    }

    @Test
    fun dockShelfBackgroundTapDismissesExpandedShelfOnly() {
        assertEquals(false, dockShelfExpandedStateAfterBackgroundTap(isExpanded = true))
        assertEquals(false, dockShelfExpandedStateAfterBackgroundTap(isExpanded = false))
    }

    @Test
    fun dockShelfCollapsesWhenDockNoLongerOverflows() {
        assertEquals(
            true,
            dockShelfExpandedStateForOverflow(isExpanded = true, hasOverflow = true),
        )
        assertEquals(
            false,
            dockShelfExpandedStateForOverflow(isExpanded = true, hasOverflow = false),
        )
        assertEquals(
            false,
            dockShelfExpandedStateForOverflow(isExpanded = false, hasOverflow = true),
        )
    }

    private fun widget(
        id: String,
        hostedWidgetId: Int,
    ): WidgetItem =
        WidgetItem(
            id = LauncherItemId(id),
            appWidgetId = HostedWidgetId(hostedWidgetId),
            label = id,
        )
}
