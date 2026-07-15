package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import com.riffle.app.launcher.apps.toAppProfile
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository

class AndroidInstalledWidgetProviderRepository(
    private val appWidgetManager: AppWidgetManager,
    private val densityProvider: () -> Float = { 1f },
    private val mapper: AndroidInstalledWidgetProviderMapper = AndroidInstalledWidgetProviderMapper(),
) : InstalledWidgetProviderRepository {
    constructor(context: Context) : this(
        appWidgetManager = AppWidgetManager.getInstance(context),
        densityProvider = { context.resources.displayMetrics.density },
    )

    override fun installedWidgetProviders(): List<InstalledWidgetProvider> =
        appWidgetManager.installedProviders
            .map { provider -> provider.toAndroidWidgetProvider() }
            .map { provider -> mapper.map(provider, density = densityProvider()) }
}

internal fun AppWidgetProviderInfo.toAndroidWidgetProvider(sdkInt: Int = Build.VERSION.SDK_INT): AndroidWidgetProvider =
    AndroidWidgetProvider(
        packageName = provider.packageName,
        className = provider.className,
        profile = profile?.toAppProfile() ?: AppProfile.personal(),
        label = label.orEmpty(),
        description = null,
        minWidthPx = minWidth,
        minHeightPx = minHeight,
        minResizeWidthPx = minResizeWidth.takeIf { value -> value > 0 },
        minResizeHeightPx = minResizeHeight.takeIf { value -> value > 0 },
        maxResizeWidthPx = maxResizeWidthCompat(sdkInt),
        maxResizeHeightPx = maxResizeHeightCompat(sdkInt),
        targetCellWidth = targetCellWidthCompat(sdkInt),
        targetCellHeight = targetCellHeightCompat(sdkInt),
        resizeMode = resizeMode,
        widgetCategory = widgetCategory,
        hasConfigurationActivity = configure != null,
        supportsReconfiguration = supportsReconfigurationCompat(sdkInt),
    )

internal data class AndroidWidgetProvider(
    val packageName: String,
    val className: String,
    val profile: AppProfile,
    val label: String,
    val description: String?,
    val minWidthPx: Int,
    val minHeightPx: Int,
    val minResizeWidthPx: Int?,
    val minResizeHeightPx: Int?,
    val maxResizeWidthPx: Int? = null,
    val maxResizeHeightPx: Int? = null,
    val targetCellWidth: Int?,
    val targetCellHeight: Int?,
    val resizeMode: Int,
    val widgetCategory: Int,
    val hasConfigurationActivity: Boolean = false,
    val supportsReconfiguration: Boolean = false,
)

private fun AppWidgetProviderInfo.targetCellWidthCompat(sdkInt: Int): Int? =
    if (sdkInt >= Build.VERSION_CODES.S) {
        targetCellWidth.takeIf { value -> value > 0 }
    } else {
        null
    }

private fun AppWidgetProviderInfo.targetCellHeightCompat(sdkInt: Int): Int? =
    if (sdkInt >= Build.VERSION_CODES.S) {
        targetCellHeight.takeIf { value -> value > 0 }
    } else {
        null
    }

private fun AppWidgetProviderInfo.maxResizeWidthCompat(sdkInt: Int): Int? =
    if (sdkInt >= Build.VERSION_CODES.S) {
        maxResizeWidth.takeIf { value -> value > 0 }
    } else {
        null
    }

private fun AppWidgetProviderInfo.maxResizeHeightCompat(sdkInt: Int): Int? =
    if (sdkInt >= Build.VERSION_CODES.S) {
        maxResizeHeight.takeIf { value -> value > 0 }
    } else {
        null
    }

private fun AppWidgetProviderInfo.supportsReconfigurationCompat(sdkInt: Int): Boolean =
    sdkInt >= Build.VERSION_CODES.S &&
        widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
