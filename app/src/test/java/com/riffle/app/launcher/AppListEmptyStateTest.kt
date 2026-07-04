package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListEmptyStateTest {
    @Test
    fun describesEmptyDrawerWhenNoAppsAreAvailable() {
        assertEquals(
            "No launchable apps found",
            appListEmptyText(
                surface = AppListSurface.DRAWER,
                query = "",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
    }

    @Test
    fun describesEmptySearchWhenNoAppsAreAvailable() {
        assertEquals(
            "No apps found",
            appListEmptyText(
                surface = AppListSurface.SEARCH,
                query = "",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
    }

    @Test
    fun describesEmptyAppListWhenQueryHidesMatches() {
        AppListSurface.entries.forEach { surface ->
            assertEquals(
                "No matching apps",
                appListEmptyText(
                    surface = surface,
                    query = "camera",
                    profileFilter = AppDrawerProfileFilter.ALL,
                ),
            )
        }
    }

    @Test
    fun describesEmptyAppListWhenProfileFilterHidesMatches() {
        AppListSurface.entries.forEach { surface ->
            assertEquals(
                "No matching apps",
                appListEmptyText(
                    surface = surface,
                    query = "",
                    profileFilter = AppDrawerProfileFilter.WORK,
                ),
            )
        }
    }

    @Test
    fun summarizesFullAppListCounts() {
        assertEquals(
            "3 apps available",
            appListSummaryText(
                totalAppCount = 3,
                resultCount = 3,
                query = "",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
    }

    @Test
    fun summarizesFilteredAppListCounts() {
        assertEquals(
            "1 app matching, 3 apps total",
            appListSummaryText(
                totalAppCount = 3,
                resultCount = 1,
                query = "camera",
                profileFilter = AppDrawerProfileFilter.WORK,
            ),
        )
    }
}
