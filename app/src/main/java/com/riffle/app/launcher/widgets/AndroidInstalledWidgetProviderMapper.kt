package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import com.riffle.app.launcher.apps.toAppProfile
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

class AndroidInstalledWidgetProviderMapper {
    internal fun map(provider: AndroidWidgetProvider): InstalledWidgetProvider =
        InstalledWidgetProvider(
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
                    minWidthDp = provider.minWidthDp,
                    minHeightDp = provider.minHeightDp,
                    minResizeWidthDp = provider.minResizeWidthDp,
                    minResizeHeightDp = provider.minResizeHeightDp,
                    targetCellWidth = provider.targetCellWidth,
                    targetCellHeight = provider.targetCellHeight,
                ),
            supportsHorizontalResize =
                provider.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            supportsVerticalResize =
                provider.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
        )
}
