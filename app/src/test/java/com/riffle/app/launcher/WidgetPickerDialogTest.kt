package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
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
    fun providerSummaryOmitsRedundantWorkProfilePrefix() {
        val provider =
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
                profile = AppProfile.work(),
            )

        assertEquals("com.example.weather - 120x80dp", provider.widgetPickerSummary())
    }

    @Test
    fun providerSummaryIncludesResizeCapabilities() {
        assertEquals(
            "com.example.weather - 120x80dp - Resizable",
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
                supportsHorizontalResize = true,
                supportsVerticalResize = true,
            ).widgetPickerSummary(),
        )
        assertEquals(
            "Horizontal resize",
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
                supportsHorizontalResize = true,
            ).widgetPickerResizeLabel(),
        )
        assertEquals(
            "Vertical resize",
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
                supportsVerticalResize = true,
            ).widgetPickerResizeLabel(),
        )
        assertEquals(
            null,
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
            ).widgetPickerResizeLabel(),
        )
    }

    @Test
    fun resultSummaryShowsAvailableWidgetCount() {
        assertEquals(
            "2 widgets available",
            widgetPickerResultSummaryText(totalProviderCount = 2, resultCount = 2, query = ""),
        )
        assertEquals(
            "1 widget available",
            widgetPickerResultSummaryText(totalProviderCount = 1, resultCount = 1, query = " "),
        )
    }

    @Test
    fun resultSummaryShowsFilteredWidgetCount() {
        assertEquals(
            "1 widget matching, 3 widgets total",
            widgetPickerResultSummaryText(totalProviderCount = 3, resultCount = 1, query = "clock"),
        )
    }

    @Test
    fun emptyMessageDistinguishesUnavailableWidgetsFromFilteredResults() {
        assertEquals(
            "No widgets available",
            widgetPickerEmptyMessageText(totalProviderCount = 0, query = ""),
        )
        assertEquals(
            "No widgets found for \"clock\"",
            widgetPickerEmptyMessageText(totalProviderCount = 3, query = " clock "),
        )
        assertEquals(
            "No matching widgets",
            widgetPickerEmptyMessageText(totalProviderCount = 3, query = ""),
        )
    }

    @Test
    fun requestAddWidgetActionUsesProviderIdentityAndLabel() {
        val provider = widgetProvider(label = "Clock", packageName = "com.example.clock", className = ".ClockWidget")

        assertEquals(
            LauncherShellAction.RequestAddWidget(
                provider = provider.identity,
                label = "Clock",
                dimensions = provider.dimensions,
            ),
            provider.requestAddWidgetAction(),
        )
    }

    @Test
    fun requestAddWidgetActionCanTargetDock() {
        val provider = widgetProvider(label = "Clock", packageName = "com.example.clock", className = ".ClockWidget")

        assertEquals(
            LauncherShellAction.RequestAddWidget(
                provider = provider.identity,
                label = "Clock",
                dimensions = provider.dimensions,
                target = WidgetAddTarget.DOCK,
            ),
            provider.requestAddWidgetAction(WidgetAddTarget.DOCK),
        )
    }

    @Test
    fun previewLabelPrefersTargetCellSizeWhenAvailable() {
        val provider =
            widgetProvider(label = "Clock", packageName = "com.example.clock", className = ".ClockWidget").copy(
                dimensions =
                    WidgetProviderDimensions(
                        minWidthDp = 120,
                        minHeightDp = 80,
                        targetCellWidth = 3,
                        targetCellHeight = 2,
                    ),
            )

        assertEquals("3 x 2", provider.widgetPickerPreviewLabel())
    }

    @Test
    fun previewLabelFallsBackToMinimumDimensions() {
        val provider = widgetProvider(label = "Clock", packageName = "com.example.clock", className = ".ClockWidget")

        assertEquals("120 x 80", provider.widgetPickerPreviewLabel())
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

    @Test
    fun filtersWidgetProvidersByMultipleTokensAndDimensions() {
        val weather =
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
            )
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock", className = ".ClockWidget")

        assertEquals(listOf(weather), listOf(weather, clock).filteredWidgetProviders("weather 120x80"))
        assertEquals(listOf(weather), listOf(weather, clock).filteredWidgetProviders("weather 120x80dp"))
    }

    @Test
    fun normalizesWidgetSearchWhitespaceAndCase() {
        val googleWeather =
            widgetProvider(
                label = "Google Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
            )
        val googleClock =
            widgetProvider(
                label = "Google Clock",
                packageName = "com.example.clock",
                className = ".ClockWidget",
            )

        assertEquals(
            listOf(googleWeather),
            listOf(googleWeather, googleClock).filteredWidgetProviders("  GOOGLE   weather "),
        )
    }

    @Test
    fun filtersWidgetProvidersByTargetCellSize() {
        val weather =
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
                dimensions = widgetDimensions(targetCellWidth = 3, targetCellHeight = 2),
            )
        val clock =
            widgetProvider(
                label = "Clock",
                packageName = "com.example.clock",
                className = ".ClockWidget",
                dimensions = widgetDimensions(targetCellWidth = 2, targetCellHeight = 2),
            )

        assertEquals(listOf(weather), listOf(weather, clock).filteredWidgetProviders("3x2"))
    }

    @Test
    fun filtersWidgetProvidersByMultipleTokensAndTargetCellSize() {
        val weather =
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
                dimensions = widgetDimensions(targetCellWidth = 3, targetCellHeight = 2),
            )
        val clock =
            widgetProvider(
                label = "Clock",
                packageName = "com.example.clock",
                className = ".ClockWidget",
                dimensions = widgetDimensions(targetCellWidth = 3, targetCellHeight = 2),
            )
        val agenda =
            widgetProvider(
                label = "Agenda",
                packageName = "com.example.calendar",
                className = ".MonthWidget",
                dimensions = widgetDimensions(targetCellWidth = 4, targetCellHeight = 2),
            )

        assertEquals(listOf(weather), listOf(weather, clock, agenda).filteredWidgetProviders("weather 3x2"))
    }

    @Test
    fun filtersWidgetProvidersByLabelAcronym() {
        val weather =
            widgetProvider(
                label = "Google Weather",
                packageName = "com.example.weather",
                className = ".WeatherWidget",
            )
        val clock =
            widgetProvider(
                label = "Google Clock",
                packageName = "com.example.clock",
                className = ".ClockWidget",
            )

        assertEquals(listOf(weather), listOf(weather, clock).filteredWidgetProviders("gw"))
        assertEquals(listOf(clock), listOf(weather, clock).filteredWidgetProviders("gc"))
    }

    @Test
    fun filtersWidgetProvidersByProfileLabel() {
        val personal = widgetProvider(label = "Weather", packageName = "com.example.weather", className = ".Weather")
        val work =
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather.work",
                className = ".Weather",
                profile = AppProfile.work(),
            )

        assertEquals(listOf(work), listOf(personal, work).filteredWidgetProviders("work"))
    }

    @Test
    fun filtersWidgetProvidersByResizableLabel() {
        val weather =
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".Weather",
                supportsHorizontalResize = true,
                supportsVerticalResize = true,
            )
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock", className = ".Clock")

        assertEquals(listOf(weather), listOf(weather, clock).filteredWidgetProviders("resizable"))
    }

    @Test
    fun filtersWidgetProvidersByDirectionalResizeLabels() {
        val weather =
            widgetProvider(
                label = "Weather",
                packageName = "com.example.weather",
                className = ".Weather",
                supportsHorizontalResize = true,
            )
        val agenda =
            widgetProvider(
                label = "Agenda",
                packageName = "com.example.agenda",
                className = ".Agenda",
                supportsVerticalResize = true,
            )

        assertEquals(listOf(weather), listOf(weather, agenda).filteredWidgetProviders("horizontal resize"))
        assertEquals(listOf(agenda), listOf(weather, agenda).filteredWidgetProviders("vertical resize"))
    }

    private fun widgetProvider(
        label: String,
        packageName: String,
        className: String,
        profile: AppProfile = AppProfile.personal(),
        dimensions: WidgetProviderDimensions = WidgetProviderDimensions(minWidthDp = 120, minHeightDp = 80),
        supportsHorizontalResize: Boolean = false,
        supportsVerticalResize: Boolean = false,
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName(packageName),
                    className = WidgetProviderClassName(className),
                    profile = profile,
                ),
            label = label,
            dimensions = dimensions,
            supportsHorizontalResize = supportsHorizontalResize,
            supportsVerticalResize = supportsVerticalResize,
        )

    private fun widgetDimensions(
        minWidthDp: Int = 120,
        minHeightDp: Int = 80,
        targetCellWidth: Int? = null,
        targetCellHeight: Int? = null,
    ): WidgetProviderDimensions =
        WidgetProviderDimensions(
            minWidthDp = minWidthDp,
            minHeightDp = minHeightDp,
            targetCellWidth = targetCellWidth,
            targetCellHeight = targetCellHeight,
        )
}
