package com.riffle.core.domain.launcher.widgets

import com.riffle.core.domain.launcher.apps.AppPackageName
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetProviderCatalogTest {
    private val catalog = WidgetProviderCatalog()

    @Test
    fun sortsProvidersByLabelThenIdentity() {
        val calendar = provider(label = "Calendar", packageName = "com.example.calendar")
        val clock = provider(label = "Clock", packageName = "com.example.clock")
        val alternateClock = provider(label = "Clock", packageName = "com.example.clock.alt")

        val sorted = catalog.sortedProviders(listOf(clock, calendar, alternateClock))

        assertEquals(listOf(calendar, clock, alternateClock), sorted)
    }

    private fun provider(
        label: String,
        packageName: String,
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName(packageName),
                    className = WidgetProviderClassName(".WidgetProvider"),
                ),
            label = label,
            dimensions = WidgetProviderDimensions(minWidthDp = 100, minHeightDp = 50),
        )
}
