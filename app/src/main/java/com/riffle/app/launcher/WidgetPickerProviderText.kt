package com.riffle.app.launcher

import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

internal fun InstalledWidgetProvider.widgetPickerSummary(): String =
    listOfNotNull(
        identity.profile.drawerProfilePrefix(),
        identity.packageName.value,
        "${dimensions.minWidthDp}x${dimensions.minHeightDp}dp",
        widgetPickerResizeLabel(),
    ).joinToString(" - ")

internal fun InstalledWidgetProvider.widgetPickerResizeLabel(): String? =
    when {
        supportsHorizontalResize && supportsVerticalResize -> "Resizable"
        supportsHorizontalResize -> "Horizontal resize"
        supportsVerticalResize -> "Vertical resize"
        else -> null
    }

internal fun InstalledWidgetProvider.widgetPickerPreviewLabel(): String =
    listOfNotNull(
        dimensions.targetCellWidth,
        dimensions.targetCellHeight,
    )
        .takeIf { cells -> cells.size == 2 }
        ?.joinToString(separator = " x ")
        ?: "${dimensions.minWidthDp} x ${dimensions.minHeightDp}"

internal val InstalledWidgetProvider.widgetPickerKey: String
    get() = "${identity.profile.id.value}:${identity.packageName.value}/${identity.className.value}"
