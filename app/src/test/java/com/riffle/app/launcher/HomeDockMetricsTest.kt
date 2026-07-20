package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
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
    fun motionPerformanceTargetsCycleThroughSupportedRefreshRates() {
        assertEquals(MotionPerformanceTargetFps.FPS_90, MotionPerformanceTargetFps.FPS_60.next())
        assertEquals(MotionPerformanceTargetFps.FPS_120, MotionPerformanceTargetFps.FPS_90.next())
        assertEquals(MotionPerformanceTargetFps.FPS_60, MotionPerformanceTargetFps.FPS_120.next())
    }

    @Test
    fun frameRateGatewaySkipsUnsupportedPlatformCapabilities() {
        val platform = FakeDockShelfFrameRatePlatform(initialFrameRate = null)

        assertEquals(null, DockShelfFrameRateGateway(platform).acquire(MotionPerformanceTargetFps.FPS_90))
        assertEquals(emptyList<Float>(), platform.requestedFrameRates)
    }

    @Test
    fun frameRateGatewayFallsBackWhenPlatformRejectsTarget() {
        val platform = FakeDockShelfFrameRatePlatform(initialFrameRate = 60f, acceptsRequests = false)

        assertEquals(null, DockShelfFrameRateGateway(platform).acquire(MotionPerformanceTargetFps.FPS_90))
        assertEquals(60f, platform.currentFrameRate)
        assertEquals(listOf(90f), platform.requestedFrameRates)
    }

    @Test
    fun frameRateGatewayRestoresPreviousPreferenceWhenLeaseEnds() {
        val platform = FakeDockShelfFrameRatePlatform(initialFrameRate = 60f)
        val lease = DockShelfFrameRateGateway(platform).acquire(MotionPerformanceTargetFps.FPS_90)

        assertEquals(90f, platform.currentFrameRate)
        lease?.restore()

        assertEquals(60f, platform.currentFrameRate)
        assertEquals(listOf(90f, 60f), platform.requestedFrameRates)
    }

    @Test
    fun frameRateGatewayUsesTheLowestSupportedRateAtOrAboveTheTarget() {
        val platform =
            FakeDockShelfFrameRatePlatform(
                initialFrameRate = 60f,
                supportedFrameRates = listOf(60f, 144f),
            )

        val lease = DockShelfFrameRateGateway(platform).acquire(MotionPerformanceTargetFps.FPS_120)

        assertEquals(144f, platform.currentFrameRate)
        lease?.restore()
        assertEquals(60f, platform.currentFrameRate)
        assertEquals(listOf(144f, 60f), platform.requestedFrameRates)
    }

    @Test
    fun frameRateGatewayUsesFractionalSupportedModesForMatchingTarget() {
        val platform =
            FakeDockShelfFrameRatePlatform(
                initialFrameRate = 59.94f,
                supportedFrameRates = listOf(59.94f, 119.88f),
            )
        val gateway = DockShelfFrameRateGateway(platform)
        val availability = gateway.availability(MotionPerformanceTargetFps.FPS_120)

        assertEquals(
            MotionPerformanceTargetFps.FPS_120,
            availability.effectiveChoice?.targetFps,
        )
        gateway.acquire(MotionPerformanceTargetFps.FPS_120)

        assertEquals(listOf(119.88f), platform.requestedFrameRates)
    }

    @Test
    fun frameRateAvailabilityFallsBackToHighestAvailableTarget() {
        val availability =
            dockShelfFrameRateAvailability(
                requestedTargetFps = MotionPerformanceTargetFps.FPS_120,
                supportedFrameRates =
                    listOf(
                        59.94f,
                        89.9f,
                    ),
            )

        assertEquals(
            listOf(MotionPerformanceTargetFps.FPS_60, MotionPerformanceTargetFps.FPS_90),
            availability.choices.map(DockShelfFrameRateChoice::targetFps),
        )
        assertEquals(
            MotionPerformanceTargetFps.FPS_90,
            availability.effectiveChoice?.targetFps,
        )
        assertEquals(true, availability.usesFallback)
    }

    @Test
    fun frameRateAvailabilityIsUnavailableWithoutDisplayModes() {
        val availability =
            dockShelfFrameRateAvailability(
                MotionPerformanceTargetFps.FPS_120,
                supportedFrameRates = null,
            )

        assertEquals(emptyList<DockShelfFrameRateChoice>(), availability.choices)
        assertEquals(null, availability.effectiveChoice)
    }

    @Test
    fun frameRateTargetCyclingSkipsUnsupportedChoices() {
        val choices =
            listOf(
                DockShelfFrameRateChoice(MotionPerformanceTargetFps.FPS_60, 59.94f),
                DockShelfFrameRateChoice(MotionPerformanceTargetFps.FPS_120, 119.88f),
            )

        assertEquals(
            MotionPerformanceTargetFps.FPS_120,
            nextDockShelfFrameRateTarget(MotionPerformanceTargetFps.FPS_60, choices),
        )
        assertEquals(
            MotionPerformanceTargetFps.FPS_60,
            nextDockShelfFrameRateTarget(MotionPerformanceTargetFps.FPS_120, choices),
        )
        assertEquals(
            MotionPerformanceTargetFps.FPS_90,
            nextDockShelfFrameRateTarget(MotionPerformanceTargetFps.FPS_90, emptyList()),
        )
    }

    @Test
    fun dockOverflowRequiresMoreItemsThanCapacity() {
        assertEquals(true, dockHasOverflow(capacity = 5, itemCount = 6))
        assertEquals(false, dockHasOverflow(capacity = 5, itemCount = 5))
        assertEquals(false, dockHasOverflow(capacity = 0, itemCount = 1))
    }

    private class FakeDockShelfFrameRatePlatform(
        initialFrameRate: Float?,
        private val supportedFrameRates: List<Float>? = listOf(60f, 90f, 120f),
        private val acceptsRequests: Boolean = true,
    ) : DockShelfFrameRatePlatform {
        var currentFrameRate = initialFrameRate
            private set
        val requestedFrameRates = mutableListOf<Float>()

        override fun preferredFrameRate(): Float? = currentFrameRate

        override fun supportedFrameRates(): List<Float>? = supportedFrameRates

        override fun setPreferredFrameRate(frameRate: Float): Boolean {
            requestedFrameRates += frameRate
            if (!acceptsRequests) return false

            currentFrameRate = frameRate
            return true
        }
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
    fun collapsedPrimaryDockKeepsOverflowItemsForTheExpandedShelf() {
        val items = (1..7).map { index -> widget("widget:$index", index) }
        val dock = DockModel(capacity = 5, items = items)

        assertEquals(items, dock.primaryDock(showShelf = false).items)
        assertEquals(7, dockRenderedSlotCount(capacity = dock.capacity, itemCount = dock.items.size, isEditing = false))
    }

    @Test
    fun expandedDockRowsKeepTheirOwnOccupiedSlotCounts() {
        val items = (1..7).map { index -> widget("widget:$index", index) }
        val dock = DockModel(capacity = 5, items = items)
        val primaryDock = dock.primaryDock(showShelf = true)
        val overflowDock = dock.overflowShelfDock()
        val primaryRenderedSlotCount =
            dockRenderedSlotCount(
                capacity = primaryDock.capacity,
                itemCount = primaryDock.items.size,
                isEditing = false,
            )
        val overflowRenderedSlotCount =
            dockRenderedSlotCount(
                capacity = overflowDock.capacity,
                itemCount = overflowDock.items.size,
                isEditing = false,
            )

        assertEquals(5, primaryRenderedSlotCount)
        assertEquals(2, overflowRenderedSlotCount)

        val primaryWidth =
            dockContainerWidthDp(
                availableWidthDp = 320,
                slotCount = primaryRenderedSlotCount,
                iconSizeDp = primaryDock.iconSizeDp,
                itemSpacingDp = primaryDock.itemSpacingDp,
                backgroundSizing = primaryDock.backgroundSizing,
            )
        val overflowWidth =
            dockContainerWidthDp(
                availableWidthDp = 320,
                slotCount = overflowRenderedSlotCount,
                iconSizeDp = overflowDock.iconSizeDp,
                itemSpacingDp = overflowDock.itemSpacingDp,
                backgroundSizing = overflowDock.backgroundSizing,
            )
        val primaryViewportWidth =
            dockContentViewportWidthDp(
                slotCount = primaryRenderedSlotCount,
                iconSizeDp = primaryDock.iconSizeDp,
                itemSpacingDp = primaryDock.itemSpacingDp,
                availableDockWidthDp = primaryWidth,
            )
        val overflowViewportWidth =
            dockContentViewportWidthDp(
                slotCount = overflowRenderedSlotCount,
                iconSizeDp = overflowDock.iconSizeDp,
                itemSpacingDp = overflowDock.itemSpacingDp,
                availableDockWidthDp = overflowWidth,
            )

        assertEquals(308, primaryWidth)
        assertEquals(134, overflowWidth)
        assertEquals(280, primaryViewportWidth)
        assertEquals(106, overflowViewportWidth)
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
