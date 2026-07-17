package com.riffle.app.launcher

import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

internal fun InstalledWidgetProvider.widgetPickerPreviewAspectRatio(): Float {
    val width = dimensions.targetCellWidth ?: dimensions.minWidthDp
    val height = dimensions.targetCellHeight ?: dimensions.minHeightDp

    return width.toFloat() / height.coerceAtLeast(1).toFloat()
}
