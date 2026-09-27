package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDockMetricsTest {
    @Test
    fun defaultIconSizeKeepsExistingDockHeight() {
        assertEquals(76, dockCrossAxisDp(iconSizeDp = 44))
    }

    @Test
    fun largerIconSizeIncreasesDockHeight() {
        assertEquals(96, dockCrossAxisDp(iconSizeDp = 64))
    }

    @Test
    fun dockContentViewportUsesOccupiedIconWidthAndSpacing() {
        assertEquals(
            212,
            dockContentViewportMainAxisDp(
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
            dockContentViewportMainAxisDp(
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
            dockContentViewportMainAxisDp(
                slotCount = 5,
                iconSizeDp = 56,
                itemSpacingDp = 24,
                availableDockMainAxisDp = 320,
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
                availableContentMainAxisDp = 280,
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
                availableContentMainAxisDp = 252,
            )

        assertEquals(
            DockSlotRenderMetrics(
                iconSizeDp = 48,
                itemSpacingDp = 3,
                overflowMode = DockOverflowMode.FitByCompaction,
            ),
            metrics,
        )
        assertEquals(252, dockSlotContentMainAxisDp(slotCount = 5, metrics = metrics))
    }

    @Test
    fun dockSlotRenderMetricsCompactsIconSizeForFiveSlotsOnFoldedWidth() {
        val metrics =
            dockSlotRenderMetrics(
                slotCount = 5,
                iconSizeDp = 56,
                itemSpacingDp = 24,
                availableContentMainAxisDp = 252,
            )

        assertEquals(
            DockSlotRenderMetrics(
                iconSizeDp = 50,
                itemSpacingDp = 0,
                overflowMode = DockOverflowMode.FitByCompaction,
            ),
            metrics,
        )
        assertEquals(250, dockSlotContentMainAxisDp(slotCount = 5, metrics = metrics))
    }

    @Test
    fun tooLittleRoomCompactsToTheFloorAndLetsTheRestScroll() {
        // Previously this case gave up and returned the configured, uncompacted size, so a dock
        // that grew slightly too narrow to compact into suddenly showed *bigger* icons than one
        // that just fit. Compacting to the floor and scrolling past it removes that discontinuity.
        val metrics =
            dockSlotRenderMetrics(
                slotCount = 5,
                iconSizeDp = 48,
                itemSpacingDp = 10,
                availableContentMainAxisDp = 159,
            )

        assertEquals(
            DockSlotRenderMetrics(
                iconSizeDp = MIN_DOCK_ICON_SIZE_DP,
                itemSpacingDp = MIN_DOCK_ITEM_SPACING_DP,
                overflowMode = DockOverflowMode.FitByCompaction,
            ),
            metrics,
        )
        assertEquals(160, dockSlotContentMainAxisDp(slotCount = 5, metrics = metrics))
    }

    @Test
    fun dynamicDockContainerCapsAtAvailableWidthWhenContentOverflows() {
        assertEquals(
            320,
            dockContainerMainAxisDp(
                availableMainAxisDp = 320,
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
            dockContainerMainAxisDp(
                availableMainAxisDp = 320,
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
            dockContainerMainAxisDp(
                availableMainAxisDp = 320,
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
            dockContentViewportMainAxisDp(
                slotCount = 0,
                iconSizeDp = 44,
                itemSpacingDp = 12,
            ),
        )
    }

    @Test
    fun normalDockRendersOnlyOccupiedSlotsSoEmptySlotsDoNotShowPlaceholders() {
        assertEquals(
            4,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 4,
                isEditing = false,
            ),
        )
    }

    @Test
    fun normalDockRendersAllPersistedItemsWhenItemsOverflowCapacity() {
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
    fun fixedDockRendersOnlyOccupiedSlotsWhenNotEditing() {
        assertEquals(
            2,
            dockRenderedSlotCount(
                capacity = 5,
                itemCount = 2,
                isEditing = false,
            ),
        )
    }

    @Test
    fun zeroCapacityDockStillRendersPersistedItemsWhenBrowsing() {
        assertEquals(
            4,
            dockRenderedSlotCount(
                capacity = 0,
                itemCount = 4,
                isEditing = false,
            ),
        )
    }

    @Test
    fun zeroCapacityDockWithPersistedItemsStillBuildsABrowsingSurface() {
        val metrics =
            checkNotNull(
                dockSurfaceMetrics(
                    dock = DockModel(capacity = 0, items = listOf(widget("weather", 1))),
                    isEditing = false,
                    availableMainAxisDp = 320,
                ),
            )

        assertEquals(1, metrics.renderedSlotCount)
    }

    @Test
    fun emptyDynamicDockShowsBackgroundDuringRecovery() {
        assertEquals(
            true,
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
    fun everyDockItemRendersWhateverTheCapacityIs() {
        // Capacity no longer truncates. All seven items are laid out; the strip scrolls.
        val items = (1..7).map { index -> widget("widget:$index", index) }
        val dock = DockModel(capacity = 5, items = items)

        assertEquals(
            7,
            dockRenderedSlotCount(capacity = dock.capacity, itemCount = dock.items.size, isEditing = false),
        )
    }

    @Test
    fun theDockSizesToItsCapacityRatherThanToHowManyItemsItHoldsOnceItIsFull() {
        // What makes capacity mean "visible at once": five slots' worth of room whether the dock
        // holds five items or twelve, so adding apps scrolls the strip instead of shrinking every
        // icon in it.
        val dock = DockModel(capacity = 5, items = emptyList())
        val fiveSlots =
            dockContainerMainAxisDp(
                availableMainAxisDp = 400,
                slotCount = 5,
                iconSizeDp = dock.iconSizeDp,
                itemSpacingDp = dock.itemSpacingDp,
                backgroundSizing = dock.backgroundSizing,
            )
        val twelveSlots =
            dockContainerMainAxisDp(
                availableMainAxisDp = 400,
                slotCount = 12,
                iconSizeDp = dock.iconSizeDp,
                itemSpacingDp = dock.itemSpacingDp,
                backgroundSizing = dock.backgroundSizing,
            )

        // Twelve slots would want more room than five, which is exactly the widening the dock must
        // not do now that the surface sizes from capacity instead of from the item count.
        assertEquals(true, twelveSlots > fiveSlots)
    }

    @Test
    fun fixedDockGivesEveryPersistedItemViewportRoomEvenPastCapacity() {
        // Capacity is lowered without deleting anything already pinned past it (see
        // dockRenderedSlotCount's own doc). FIXED ("Full width") must not re-cap the *visible* slot
        // count to the old capacity -- a longer background that still hid items past it would be the
        // same "still scrolls" mismatch the setting exists to fix -- so its content viewport is sized
        // for all seven items, wider than DYNAMIC's, which still only sizes for the five it caps to.
        val items = (1..7).map { index -> widget("widget:$index", index) }
        val fixedMetrics =
            checkNotNull(
                dockSurfaceMetrics(
                    dock = DockModel(capacity = 5, items = items, backgroundSizing = DockBackgroundSizing.FIXED),
                    isEditing = false,
                    availableMainAxisDp = 2000,
                ),
            )
        val dynamicMetrics =
            checkNotNull(
                dockSurfaceMetrics(
                    dock = DockModel(capacity = 5, items = items, backgroundSizing = DockBackgroundSizing.DYNAMIC),
                    isEditing = false,
                    availableMainAxisDp = 2000,
                ),
            )

        assertEquals(true, fixedMetrics.contentViewportMainAxisDp > dynamicMetrics.contentViewportMainAxisDp)
    }

    @Test
    fun aHorizontalDynamicDockStopsAtItsAbsoluteCapHoweverWideTheScreenIs() {
        // The cap exists because screens get wider than an under-filled dock usefully needs to be --
        // it still applies to DYNAMIC (fit content), which is what it was always meant to bound.
        assertEquals(
            560,
            dockContainerMainAxisDp(
                availableMainAxisDp = 1600,
                slotCount = 40,
                iconSizeDp = 48,
                itemSpacingDp = 8,
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
                runsHorizontally = true,
            ),
        )
    }

    @Test
    fun aVerticalDynamicDockStopsAtAShareOfTheHeightItWasOffered() {
        // A fixed dp would crowd a short screen and stop well short of a tall one.
        assertEquals(
            (800 * 0.7f).toInt(),
            dockContainerMainAxisDp(
                availableMainAxisDp = 800,
                slotCount = 40,
                iconSizeDp = 48,
                itemSpacingDp = 8,
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
                runsHorizontally = false,
            ),
        )
        assertEquals(
            (1600 * 0.7f).toInt(),
            dockContainerMainAxisDp(
                availableMainAxisDp = 1600,
                slotCount = 40,
                iconSizeDp = 48,
                itemSpacingDp = 8,
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
                runsHorizontally = false,
            ),
        )
    }

    @Test
    fun aVerticalDockShorterThanItsShareStillSizesToItsItems() {
        // The share is a ceiling, not a length: a two-item dock is two items long on any screen.
        val twoItems =
            dockContainerMainAxisDp(
                availableMainAxisDp = 1600,
                slotCount = 2,
                iconSizeDp = 48,
                itemSpacingDp = 8,
                backgroundSizing = DockBackgroundSizing.DYNAMIC,
                runsHorizontally = false,
            )

        assertEquals(true, twoItems < (1600 * 0.7f).toInt())
    }

    @Test
    fun aFixedHorizontalDockUsesTheFullWidthHoweverWideTheScreenIs() {
        // FIXED ("Full width") is the user asking the dock to actually use the room it has -- the
        // absolute cap that bounds an under-filled DYNAMIC dock would otherwise make this setting do
        // nothing past 560dp, which is exactly the "still scrolls" mismatch it exists to fix.
        assertEquals(
            1600,
            dockContainerMainAxisDp(
                availableMainAxisDp = 1600,
                slotCount = 40,
                iconSizeDp = 48,
                itemSpacingDp = 8,
                backgroundSizing = DockBackgroundSizing.FIXED,
                runsHorizontally = true,
            ),
        )
    }

    @Test
    fun aFixedVerticalDockUsesTheFullHeightHoweverTallTheScreenIs() {
        // Same fix on the vertical axis: FIXED is not capped to 70% of the height it was offered.
        assertEquals(
            800,
            dockContainerMainAxisDp(
                availableMainAxisDp = 800,
                slotCount = 40,
                iconSizeDp = 48,
                itemSpacingDp = 8,
                backgroundSizing = DockBackgroundSizing.FIXED,
                runsHorizontally = false,
            ),
        )
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
    fun dockShelfCollapsesWhenItHasNoContent() {
        assertEquals(
            true,
            dockShelfExpandedStateForContent(isExpanded = true, hasContent = true),
        )
        assertEquals(
            false,
            dockShelfExpandedStateForContent(isExpanded = true, hasContent = false),
        )
        assertEquals(
            false,
            dockShelfExpandedStateForContent(isExpanded = false, hasContent = true),
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
