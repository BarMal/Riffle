package com.riffle.app.launcher

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class AndroidHomeLayoutDeviceClassObserver(
    private val activity: Activity,
) {
    fun deviceClasses(): Flow<HomeLayoutDeviceClass?> =
        WindowInfoTracker
            .getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                homeLayoutDeviceClassFromWindowLayout(
                    hasFoldingFeature = layoutInfo.displayFeatures.any { feature -> feature is FoldingFeature },
                    screenWidthDp = activity.resources.configuration.screenWidthDp,
                    screenHeightDp = activity.resources.configuration.screenHeightDp,
                )
            }
            .distinctUntilChanged()
}
