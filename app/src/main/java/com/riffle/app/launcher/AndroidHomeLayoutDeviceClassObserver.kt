package com.riffle.app.launcher

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

internal class AndroidHomeLayoutDeviceClassObserver(
    private val activity: Activity,
    private val windowMetricsCalculator: WindowMetricsCalculator = WindowMetricsCalculator.getOrCreate(),
) {
    fun currentDeviceClassSelection(): HomeLayoutDeviceClassSelection? {
        val windowSize = currentWindowSize()
        val configurationClass =
            homeLayoutDeviceClassFromConfiguration(
                screenWidthDp = windowSize.screenWidthDp,
                screenHeightDp = windowSize.screenHeightDp,
            )

        return homeLayoutDeviceClassSelectionFromWindowLayout(
            foldablePosture =
                emptyList<FoldingFeature>().foldablePosture(
                    hasFoldableHardware = activity.hasFoldableHardware(),
                    configurationClass = configurationClass,
                ),
            screenWidthDp = windowSize.screenWidthDp,
            screenHeightDp = windowSize.screenHeightDp,
        )
    }

    fun deviceClassSelections(): Flow<HomeLayoutDeviceClassSelection?> =
        WindowInfoTracker
            .getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                val windowSize = currentWindowSize()
                val screenWidthDp = windowSize.screenWidthDp
                val screenHeightDp = windowSize.screenHeightDp
                val configurationClass =
                    homeLayoutDeviceClassFromConfiguration(
                        screenWidthDp = screenWidthDp,
                        screenHeightDp = screenHeightDp,
                    )

                homeLayoutDeviceClassSelectionFromWindowLayout(
                    foldablePosture =
                        layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .foldablePosture(
                                hasFoldableHardware = activity.hasFoldableHardware(),
                                configurationClass = configurationClass,
                            ),
                    screenWidthDp = screenWidthDp,
                    screenHeightDp = screenHeightDp,
                )
            }
            .distinctUntilChanged()

    private fun currentWindowSize(): HomeLayoutWindowSize {
        val bounds = windowMetricsCalculator.computeCurrentWindowMetrics(activity).bounds
        return homeLayoutWindowSizeFromPixels(
            widthPx = bounds.width(),
            heightPx = bounds.height(),
            density = activity.resources.displayMetrics.density,
        )
    }
}

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
        hasUnfoldedFoldingFeature =
            any { feature -> feature.state == FoldingFeature.State.FLAT || feature.isSeparating },
        configurationClass = configurationClass,
    )

private const val FEATURE_SENSOR_HINGE_ANGLE = "android.hardware.sensor.hinge_angle"
