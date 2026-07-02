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
                    hasFoldingFeature = layoutInfo.displayFeatures.any { feature -> feature is FoldingFeature },
                    screenWidthDp = activity.resources.configuration.screenWidthDp,
                    screenHeightDp = activity.resources.configuration.screenHeightDp,
                )
            }
            .distinctUntilChanged()
}
