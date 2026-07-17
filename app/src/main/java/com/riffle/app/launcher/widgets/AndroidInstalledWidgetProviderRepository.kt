package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.riffle.app.launcher.apps.toAppProfile
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository

class AndroidInstalledWidgetProviderRepository(
    private val appWidgetManager: AppWidgetManager,
    private val densityProvider: () -> Float = { 1f },
    private val mapper: AndroidInstalledWidgetProviderMapper = AndroidInstalledWidgetProviderMapper(),
    private val packageManager: PackageManager? = null,
) : InstalledWidgetProviderRepository {
    constructor(context: Context) : this(
        appWidgetManager = AppWidgetManager.getInstance(context),
        densityProvider = { context.resources.displayMetrics.density },
        packageManager = context.packageManager,
    )

    override fun installedWidgetProviders(): List<InstalledWidgetProvider> =
        appWidgetManager.installedProviders
            .map { provider -> provider.toAndroidWidgetProvider(packageManager) }
            .map { provider -> mapper.map(provider, density = densityProvider()) }
}

internal fun AppWidgetProviderInfo.toAndroidWidgetProvider(
    packageManager: PackageManager? = null,
): AndroidWidgetProvider =
    AndroidWidgetProvider(
        packageName = provider.packageName,
        className = provider.className,
        profile = profile?.toAppProfile() ?: AppProfile.personal(),
        label = label.orEmpty(),
        appLabel = packageManager?.applicationLabelFor(provider.packageName).orEmpty(),
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
        widgetCategory = widgetCategory,
        hasConfigurationActivity = configure != null,
        supportsReconfiguration = supportsReconfigurationCompat,
    )

internal data class AndroidWidgetProvider(
    val packageName: String,
    val className: String,
    val profile: AppProfile,
    val label: String,
    val appLabel: String = label,
    val description: String? = null,
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

private fun PackageManager.applicationLabelFor(packageName: String): String? =
    runCatching {
        getApplicationLabel(getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

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
