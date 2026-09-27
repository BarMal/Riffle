package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import org.junit.Assert.assertEquals
import org.junit.Test

// Split out of HomeDockMetricsTest (which grew past detekt's LargeClass threshold): the dock
// shelf's reduced-motion policy and its frame-rate gateway are their own self-contained concern,
// unrelated to that file's dock sizing/capacity/rendering tests, and shared no state with them.
class DockShelfMotionAndFrameRateTest {
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
}
