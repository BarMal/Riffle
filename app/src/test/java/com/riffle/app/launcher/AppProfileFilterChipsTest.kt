package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppProfileFilterChipsTest {
    @Test
    fun showsEveryProfileFilter() {
        assertEquals(
            listOf(
                AppDrawerProfileFilter.ALL,
                AppDrawerProfileFilter.PERSONAL,
                AppDrawerProfileFilter.WORK,
                AppDrawerProfileFilter.PRIVATE,
            ),
            appProfileFiltersFor(),
        )
    }

    @Test
    fun labelsProfileFiltersWithCounts() {
        assertEquals(
            listOf("All (3)", "Personal (1)", "Work (2)", "Private (0)"),
            appProfileFilterOptionsFor(
                apps =
                    listOf(
                        app("Camera", profile = AppProfile.personal()),
                        app("Docs", profile = AppProfile.work()),
                        app("Mail", profile = AppProfile.work()),
                    ),
            ).map { option -> option.label },
        )
    }

    @Test
    fun detectsAndCoercesUnavailableProfileFilters() {
        val apps = listOf(app("Camera", profile = AppProfile.personal()))

        assertEquals(false, AppDrawerProfileFilter.WORK.availableFor(apps))
        assertEquals(AppDrawerProfileFilter.ALL, AppDrawerProfileFilter.WORK.coerceAvailableFor(apps))
    }

    @Test
    fun keepsSelectedFilterWhenProfileDisappears() {
        assertEquals(
            listOf(
                AppDrawerProfileFilter.ALL,
                AppDrawerProfileFilter.PERSONAL,
                AppDrawerProfileFilter.WORK,
                AppDrawerProfileFilter.PRIVATE,
            ),
            appProfileFiltersFor(
                selectedFilter = AppDrawerProfileFilter.PRIVATE,
            ),
        )
    }

    @Test
    fun emptyAppsShowOnlyAllAndSelectedFilter() {
        assertEquals(
            listOf(
                AppDrawerProfileFilter.ALL,
                AppDrawerProfileFilter.PERSONAL,
                AppDrawerProfileFilter.WORK,
                AppDrawerProfileFilter.PRIVATE,
            ),
            appProfileFiltersFor(),
        )
        assertEquals(
            listOf(
                AppDrawerProfileFilter.ALL,
                AppDrawerProfileFilter.PERSONAL,
                AppDrawerProfileFilter.WORK,
                AppDrawerProfileFilter.PRIVATE,
            ),
            appProfileFiltersFor(
                selectedFilter = AppDrawerProfileFilter.WORK,
            ),
        )
    }

    private fun app(
        label: String,
        profile: AppProfile,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = label,
        )
}
