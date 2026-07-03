package com.riffle.app.launcher

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class AndroidHomeLayoutDeviceClassObserver(
    private val activity: Activity,
) {
    fun deviceClassSelections(): Flow<HomeLayoutDeviceClassSelection?> =
        WindowInfoTracker
            .getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                homeLayoutDeviceClassSelectionFromWindowLayout(
                    foldablePosture =
                        layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .foldablePosture(hasFoldableHardware = activity.hasFoldableHardware()),
                    screenWidthDp = activity.resources.configuration.screenWidthDp,
                    screenHeightDp = activity.resources.configuration.screenHeightDp,
                )
            }
            .distinctUntilChanged()
}

private fun Activity.hasFoldableHardware(): Boolean = packageManager.hasSystemFeature(FEATURE_SENSOR_HINGE_ANGLE)

private fun List<FoldingFeature>.foldablePosture(hasFoldableHardware: Boolean): HomeLayoutFoldablePosture =
    homeLayoutFoldablePosture(
        hasFoldableHardware = hasFoldableHardware,
        hasFoldingFeature = isNotEmpty(),
        hasUnfoldedFoldingFeature =
            any { feature -> feature.state == FoldingFeature.State.FLAT || feature.isSeparating },
    )

private const val FEATURE_SENSOR_HINGE_ANGLE = "android.hardware.sensor.hinge_angle"
