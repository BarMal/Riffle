package com.riffle.core.domain.launcher.widgets

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile

data class InstalledWidgetProvider(
    val identity: WidgetProviderIdentity,
    val label: String,
    val appLabel: String = label,
    val description: String? = null,
    val dimensions: WidgetProviderDimensions,
    val supportsHorizontalResize: Boolean = false,
    val supportsVerticalResize: Boolean = false,
    val widgetCategory: Int = 0,
    val hasConfigurationActivity: Boolean = false,
    val supportsReconfiguration: Boolean = false,
)

data class WidgetProviderIdentity(
    val packageName: AppPackageName,
    val className: WidgetProviderClassName,
    val profile: AppProfile = AppProfile.personal(),
)

@JvmInline
value class WidgetProviderClassName(val value: String)

data class WidgetProviderDimensions(
    val minWidthDp: Int,
    val minHeightDp: Int,
    val minResizeWidthDp: Int? = null,
    val minResizeHeightDp: Int? = null,
    val maxResizeWidthDp: Int? = null,
    val maxResizeHeightDp: Int? = null,
    val targetCellWidth: Int? = null,
    val targetCellHeight: Int? = null,
)

fun interface InstalledWidgetProviderRepository {
    fun installedWidgetProviders(): List<InstalledWidgetProvider>
}
