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
    fun showsOnlyProfilesPresentInApps() {
        assertEquals(
            listOf(AppDrawerProfileFilter.ALL, AppDrawerProfileFilter.PERSONAL, AppDrawerProfileFilter.WORK),
            appProfileFiltersFor(
                apps =
                    listOf(
                        app("Camera", profile = AppProfile.personal()),
                        app("Docs", profile = AppProfile.work()),
                    ),
            ),
        )
    }

    @Test
    fun keepsSelectedFilterWhenProfileDisappears() {
        assertEquals(
            listOf(AppDrawerProfileFilter.ALL, AppDrawerProfileFilter.PERSONAL, AppDrawerProfileFilter.PRIVATE),
            appProfileFiltersFor(
                apps = listOf(app("Camera", profile = AppProfile.personal())),
                selectedFilter = AppDrawerProfileFilter.PRIVATE,
            ),
        )
    }

    @Test
    fun emptyAppsShowOnlyAllAndSelectedFilter() {
        assertEquals(
            listOf(AppDrawerProfileFilter.ALL),
            appProfileFiltersFor(apps = emptyList()),
        )
        assertEquals(
            listOf(AppDrawerProfileFilter.ALL, AppDrawerProfileFilter.WORK),
            appProfileFiltersFor(
                apps = emptyList(),
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
