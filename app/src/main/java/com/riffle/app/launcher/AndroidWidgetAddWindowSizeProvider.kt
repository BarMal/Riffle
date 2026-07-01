package com.riffle.app.launcher

import android.app.Activity
import androidx.window.layout.WindowMetricsCalculator
import kotlin.math.roundToInt

internal class AndroidWidgetAddWindowSizeProvider(
    private val activity: Activity,
    private val windowMetricsCalculator: WindowMetricsCalculator = WindowMetricsCalculator.getOrCreate(),
) {
    fun windowSize(): LauncherWidgetAddWindowSize {
        val bounds = windowMetricsCalculator.computeCurrentWindowMetrics(activity).bounds

        return launcherWidgetAddWindowSizeFromPixels(
            widthPx = bounds.width(),
            heightPx = bounds.height(),
            density = activity.resources.displayMetrics.density,
        )
    }
}

internal fun launcherWidgetAddWindowSizeFromPixels(
    widthPx: Int,
    heightPx: Int,
    density: Float,
): LauncherWidgetAddWindowSize {
    val safeDensity = density.takeIf { value -> value > 0f } ?: 1f

    return LauncherWidgetAddWindowSize(
        availableWidthDp = (widthPx / safeDensity).roundToInt(),
        availableHeightDp = (heightPx / safeDensity).roundToInt(),
    )
}
