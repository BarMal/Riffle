package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import com.riffle.app.launcher.apps.toAppProfile
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlin.math.ceil

class AndroidInstalledWidgetProviderMapper {
    internal fun map(
        provider: AndroidWidgetProvider,
        density: Float,
    ): InstalledWidgetProvider {
        val minWidthDp = provider.minWidthPx.requiredDp(density)
        val minHeightDp = provider.minHeightPx.requiredDp(density)

        return InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName(provider.packageName),
                    className = WidgetProviderClassName(provider.className),
                    profile = provider.profile?.toAppProfile() ?: AppProfile.personal(),
                ),
            label = provider.label.ifBlank { provider.packageName },
            description = provider.description?.takeIf(String::isNotBlank),
            dimensions =
                WidgetProviderDimensions(
                    minWidthDp = minWidthDp,
                    minHeightDp = minHeightDp,
                    minResizeWidthDp = provider.minResizeWidthPx.optionalDp(density)?.takeIf { it <= minWidthDp },
                    minResizeHeightDp = provider.minResizeHeightPx.optionalDp(density)?.takeIf { it <= minHeightDp },
                    maxResizeWidthDp = provider.maxResizeWidthPx.optionalDp(density)?.takeIf { it >= minWidthDp },
                    maxResizeHeightDp = provider.maxResizeHeightPx.optionalDp(density)?.takeIf { it >= minHeightDp },
                    targetCellWidth = provider.targetCellWidth.validTargetCellCount(),
                    targetCellHeight = provider.targetCellHeight.validTargetCellCount(),
                ),
            supportsHorizontalResize =
                provider.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            supportsVerticalResize =
                provider.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
            widgetCategory = provider.widgetCategory,
            hasConfigurationActivity = provider.hasConfigurationActivity,
            supportsReconfiguration = provider.supportsReconfiguration,
        )
    }
}

private const val MAX_WIDGET_DIMENSION_DP = 10_000
private const val MAX_TARGET_CELL_COUNT = 100

private fun Int.requiredDp(density: Float): Int = optionalDp(density) ?: 1

private fun Int?.optionalDp(density: Float): Int? =
    takeIf { pixels -> pixels != null && pixels > 0 && density.isFinite() && density > 0f }
        ?.let { pixels -> ceil(pixels.toDouble() / density.toDouble()) }
        ?.takeIf { it >= 1.0 && it <= MAX_WIDGET_DIMENSION_DP.toDouble() }
        ?.toInt()

private fun Int?.validTargetCellCount(): Int? = this?.takeIf { it in 1..MAX_TARGET_CELL_COUNT }
