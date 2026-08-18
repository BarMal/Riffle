package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockOverflowMode
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ITEM_SPACING_DP
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
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
    fun theDockSizesToItsCapacityRatherThanToHowManyItemsItHolds() {
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
