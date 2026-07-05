package com.riffle.core.domain.launcher.widgets

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
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

    @Test
    fun sortsEqualProviderIdentitiesByStableProfileId() {
        val workClock =
            provider(
                label = "Clock",
                packageName = "com.example.clock",
                className = ".ClockWidget",
                profile = AppProfile(id = AppProfileId("profile-a"), type = AppProfileType.WORK),
            )
        val personalClock =
            provider(
                label = "Clock",
                packageName = "com.example.clock",
                className = ".ClockWidget",
                profile = AppProfile(id = AppProfileId("profile-b"), type = AppProfileType.PERSONAL),
            )

        val sorted = catalog.sortedProviders(listOf(personalClock, workClock))

        assertEquals(listOf(workClock, personalClock), sorted)
    }

    private fun provider(
        label: String,
        packageName: String,
        className: String = ".WidgetProvider",
        profile: AppProfile = AppProfile.personal(),
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName(packageName),
                    className = WidgetProviderClassName(className),
                    profile = profile,
                ),
            label = label,
            dimensions = WidgetProviderDimensions(minWidthDp = 100, minHeightDp = 50),
        )
}
