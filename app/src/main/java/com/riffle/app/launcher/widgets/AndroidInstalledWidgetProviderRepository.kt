package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.UserHandle
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository

class AndroidInstalledWidgetProviderRepository(
    private val appWidgetManager: AppWidgetManager,
    private val mapper: AndroidInstalledWidgetProviderMapper = AndroidInstalledWidgetProviderMapper(),
) : InstalledWidgetProviderRepository {
    constructor(context: Context) : this(AppWidgetManager.getInstance(context))

    override fun installedWidgetProviders(): List<InstalledWidgetProvider> =
        appWidgetManager.installedProviders
            .map { provider -> provider.toAndroidWidgetProvider() }
            .map(mapper::map)
}

private fun AppWidgetProviderInfo.toAndroidWidgetProvider(): AndroidWidgetProvider =
    AndroidWidgetProvider(
        packageName = provider.packageName,
        className = provider.className,
        profile = profile,
        label = label.orEmpty(),
        description = null,
        minWidthDp = minWidth,
        minHeightDp = minHeight,
        minResizeWidthDp = minResizeWidth.takeIf { value -> value > 0 },
        minResizeHeightDp = minResizeHeight.takeIf { value -> value > 0 },
        resizeMode = resizeMode,
    )

internal data class AndroidWidgetProvider(
    val packageName: String,
    val className: String,
    val profile: UserHandle?,
    val label: String,
    val description: String?,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val minResizeWidthDp: Int?,
    val minResizeHeightDp: Int?,
    val resizeMode: Int,
)
