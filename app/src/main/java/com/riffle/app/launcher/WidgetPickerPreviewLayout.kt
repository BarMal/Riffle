package com.riffle.app.launcher

import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

internal fun InstalledWidgetProvider.widgetPickerPreviewAspectRatio(): Float {
    val width = dimensions.targetCellWidth ?: dimensions.minWidthDp
    val height = dimensions.targetCellHeight ?: dimensions.minHeightDp

    return (width.toFloat() / height.coerceAtLeast(1).toFloat())
        .coerceIn(MIN_WIDGET_PREVIEW_ASPECT_RATIO, MAX_WIDGET_PREVIEW_ASPECT_RATIO)
}

private const val MIN_WIDGET_PREVIEW_ASPECT_RATIO = 0.75f
private const val MAX_WIDGET_PREVIEW_ASPECT_RATIO = 2.25f
