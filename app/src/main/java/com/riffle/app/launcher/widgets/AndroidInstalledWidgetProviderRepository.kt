package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.UserHandle
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

private fun AppWidgetProviderInfo.toAndroidWidgetProvider(): AndroidWidgetProvider =
    AndroidWidgetProvider(
        packageName = provider.packageName,
        className = provider.className,
        profile = profile,
        label = label.orEmpty(),
        description = null,
        minWidthPx = minWidth,
        minHeightPx = minHeight,
        minResizeWidthPx = minResizeWidth.takeIf { value -> value > 0 },
        minResizeHeightPx = minResizeHeight.takeIf { value -> value > 0 },
        maxResizeWidthPx = maxResizeWidthCompat,
        maxResizeHeightPx = maxResizeHeightCompat,
        targetCellWidth = targetCellWidthCompat,
        targetCellHeight = targetCellHeightCompat,
        resizeMode = resizeMode,
        hasConfigurationActivity = configure != null,
        supportsReconfiguration = supportsReconfigurationCompat,
    )

internal data class AndroidWidgetProvider(
    val packageName: String,
    val className: String,
    val profile: UserHandle?,
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
    val hasConfigurationActivity: Boolean = false,
    val supportsReconfiguration: Boolean = false,
)

private val AppWidgetProviderInfo.targetCellWidthCompat: Int?
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            targetCellWidth.takeIf { value -> value > 0 }
        } else {
            null
        }

private val AppWidgetProviderInfo.targetCellHeightCompat: Int?
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            targetCellHeight.takeIf { value -> value > 0 }
        } else {
            null
        }

private val AppWidgetProviderInfo.maxResizeWidthCompat: Int?
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            maxResizeWidth.takeIf { value -> value > 0 }
        } else {
            null
        }

private val AppWidgetProviderInfo.maxResizeHeightCompat: Int?
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            maxResizeHeight.takeIf { value -> value > 0 }
        } else {
            null
        }

private val AppWidgetProviderInfo.supportsReconfigurationCompat: Boolean
    get() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
