package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPickerDialogTest {
    @Test
    fun providerSummaryIncludesPackageAndMinimumDimensions() {
        val provider =
            InstalledWidgetProvider(
                identity =
                    WidgetProviderIdentity(
                        packageName = AppPackageName("com.example.weather"),
                        className = WidgetProviderClassName(".WeatherWidget"),
                    ),
                label = "Weather",
                dimensions = WidgetProviderDimensions(minWidthDp = 120, minHeightDp = 80),
            )

        assertEquals("com.example.weather - 120x80dp", provider.widgetPickerSummary())
    }

    @Test
    fun requestAddWidgetActionUsesProviderIdentityAndLabel() {
        val provider =
            InstalledWidgetProvider(
                identity =
                    WidgetProviderIdentity(
                        packageName = AppPackageName("com.example.clock"),
                        className = WidgetProviderClassName(".ClockWidget"),
                    ),
                label = "Clock",
                dimensions = WidgetProviderDimensions(minWidthDp = 80, minHeightDp = 80),
            )

        assertEquals(
            LauncherShellAction.RequestAddWidget(
                provider = provider.identity,
                label = "Clock",
            ),
            provider.requestAddWidgetAction(),
        )
    }
}
