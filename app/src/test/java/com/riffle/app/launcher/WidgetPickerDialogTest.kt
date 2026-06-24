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
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
            )

        assertEquals("com.example.weather - 120x80dp", provider.widgetPickerSummary())
    }

    @Test
    fun requestAddWidgetActionUsesProviderIdentityAndLabel() {
        val provider = widgetProvider(label = "Clock", packageName = "com.example.clock", className = ".ClockWidget")

        assertEquals(
            LauncherShellAction.RequestAddWidget(
                provider = provider.identity,
                label = "Clock",
            ),
            provider.requestAddWidgetAction(),
        )
    }

    @Test
    fun filtersWidgetProvidersByLabelPackageAndClassName() {
        val weather =
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
            )
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock", className = ".ClockWidget")
        val calendar =
            widgetProvider(label = "Agenda", packageName = "com.example.calendar", className = ".MonthWidget")

        val providers = listOf(weather, clock, calendar)

        assertEquals(listOf(weather), providers.filteredWidgetProviders("weather"))
        assertEquals(listOf(clock), providers.filteredWidgetProviders("CLOCK"))
        assertEquals(listOf(calendar), providers.filteredWidgetProviders("month"))
        assertEquals(providers, providers.filteredWidgetProviders(" "))
    }

    private fun widgetProvider(
        label: String,
        packageName: String,
        className: String,
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName(packageName),
                    className = WidgetProviderClassName(className),
                ),
            label = label,
            dimensions = WidgetProviderDimensions(minWidthDp = 120, minHeightDp = 80),
        )
}
