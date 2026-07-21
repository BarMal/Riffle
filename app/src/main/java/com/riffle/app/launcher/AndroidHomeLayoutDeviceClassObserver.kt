package com.riffle.app.launcher

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

internal class AndroidHomeLayoutDeviceClassObserver(
    private val activity: Activity,
    private val windowMetricsCalculator: WindowMetricsCalculator = WindowMetricsCalculator.getOrCreate(),
) {
    fun currentDeviceClassEvent(source: String): HomeLayoutDeviceClassEvent? =
        deviceClassEvent(
            source = source,
            foldingFeatures = emptyList(),
        )

    fun currentDeviceClassSelection(): HomeLayoutDeviceClassSelection? = currentDeviceClassEvent("current")?.selection

    fun deviceClassEvents(): Flow<HomeLayoutDeviceClassEvent?> =
        WindowInfoTracker
            .getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                deviceClassEvent(
                    source = "window-info",
                    foldingFeatures = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>(),
                )
            }
            .distinctUntilChanged()

    fun deviceClassSelections(): Flow<HomeLayoutDeviceClassSelection?> =
        deviceClassEvents()
            .map { event -> event?.selection }
            .distinctUntilChanged()

    private fun deviceClassEvent(
        source: String,
        foldingFeatures: List<FoldingFeature>,
    ): HomeLayoutDeviceClassEvent? {
        val windowSize = currentWindowSize()
        val configurationClass =
            homeLayoutDeviceClassFromConfiguration(
                screenWidthDp = windowSize.screenWidthDp,
                screenHeightDp = windowSize.screenHeightDp,
            )
        val hasFoldableHardware = activity.hasFoldableHardware()
        val foldablePosture =
            foldingFeatures.foldablePosture(
                hasFoldableHardware = hasFoldableHardware,
                configurationClass = configurationClass,
            )
        val selection =
            homeLayoutDeviceClassSelectionFromWindowLayout(
                foldablePosture = foldablePosture,
                screenWidthDp = windowSize.screenWidthDp,
                screenHeightDp = windowSize.screenHeightDp,
            ) ?: return null

        return HomeLayoutDeviceClassEvent(
            source = source,
            selection = selection,
            windowSize = windowSize,
            hasFoldableHardware = hasFoldableHardware,
            configurationClass = configurationClass,
            foldablePosture = foldablePosture,
            foldingFeatures =
                foldingFeatures.map { feature ->
                    HomeLayoutFoldingFeatureDebug(
                        state = feature.state.toString(),
                        orientation = feature.orientation.toString(),
                        isSeparating = feature.isSeparating,
                    )
                },
            timeScapeWindowLayout =
                timeScapeWindowLayoutFromPixels(
                    widthPx = windowMetricsCalculator.computeCurrentWindowMetrics(activity).bounds.width(),
                    heightPx = windowMetricsCalculator.computeCurrentWindowMetrics(activity).bounds.height(),
                    density = activity.resources.displayMetrics.density,
                    separatingHingeBounds =
                        foldingFeatures
                            .filter { feature -> feature.isSeparating }
                            .map { feature -> feature.bounds },
                ),
        )
    }

    private fun currentWindowSize(): HomeLayoutWindowSize {
        val bounds = windowMetricsCalculator.computeCurrentWindowMetrics(activity).bounds
        return homeLayoutWindowSizeFromPixels(
            widthPx = bounds.width(),
            heightPx = bounds.height(),
            density = activity.resources.displayMetrics.density,
        )
    }
}

internal data class HomeLayoutDeviceClassEvent(
    val source: String,
    val selection: HomeLayoutDeviceClassSelection,
    val windowSize: HomeLayoutWindowSize,
    val hasFoldableHardware: Boolean,
    val configurationClass: HomeLayoutDeviceClass?,
    val foldablePosture: HomeLayoutFoldablePosture,
    val foldingFeatures: List<HomeLayoutFoldingFeatureDebug>,
    val timeScapeWindowLayout: TimeScapeWindowLayout = TimeScapeWindowLayout(widthDp = 0, heightDp = 0),
) {
    val logText: String
        get() =
            "source=$source " +
                "active=${selection.activeDeviceClass} " +
                "available=${selection.availableDeviceClasses.sorted()} " +
                "window=${windowSize.screenWidthDp}x${windowSize.screenHeightDp}dp " +
                "posture=$foldablePosture " +
                "configurationClass=$configurationClass " +
                "hasFoldableHardware=$hasFoldableHardware " +
                "foldingFeatures=$foldingFeatures"
}

internal data class HomeLayoutFoldingFeatureDebug(
    val state: String,
    val orientation: String,
    val isSeparating: Boolean,
)

internal data class HomeLayoutWindowSize(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
)

internal fun homeLayoutWindowSizeFromPixels(
    widthPx: Int,
    heightPx: Int,
    density: Float,
): HomeLayoutWindowSize {
    val safeDensity = density.takeIf { value -> value > 0f } ?: 1f

    return HomeLayoutWindowSize(
        screenWidthDp = (widthPx / safeDensity).roundToInt(),
        screenHeightDp = (heightPx / safeDensity).roundToInt(),
    )
}

private fun Activity.hasFoldableHardware(): Boolean = packageManager.hasSystemFeature(FEATURE_SENSOR_HINGE_ANGLE)

private fun List<FoldingFeature>.foldablePosture(
    hasFoldableHardware: Boolean,
    configurationClass: HomeLayoutDeviceClass?,
): HomeLayoutFoldablePosture =
    homeLayoutFoldablePosture(
        hasFoldableHardware = hasFoldableHardware,
        hasFoldingFeature = isNotEmpty(),
        hasHalfOpenedFoldingFeature = any { feature -> feature.state == FoldingFeature.State.HALF_OPENED },
        hasUnfoldedFoldingFeature =
            any { feature -> feature.state == FoldingFeature.State.FLAT || feature.isSeparating },
        configurationClass = configurationClass,
    )

private const val FEATURE_SENSOR_HINGE_ANGLE = "android.hardware.sensor.hinge_angle"
