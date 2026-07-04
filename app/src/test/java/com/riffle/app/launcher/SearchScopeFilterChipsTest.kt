package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchScopeFilterChipsTest {
    @Test
    fun labelsSearchContentFilters() {
        assertEquals("Apps", AppSearchContentFilter.APPS.label)
        assertEquals("Shortcuts", AppSearchContentFilter.SHORTCUTS.label)
    }

    @Test
    fun labelsSearchProfileFiltersWithCounts() {
        assertEquals(
            listOf("Personal (1)", "Work (2)"),
            searchProfileFilterOptionsFor(
                apps =
                    listOf(
                        app("Camera", profile = AppProfile.personal()),
                        app("Docs", profile = AppProfile.work()),
                        app("Mail", profile = AppProfile.work()),
                    ),
                filters = AppSearchFilters(),
            ).map { option -> option.label },
        )
    }

    @Test
    fun keepsSelectedProfileWhenNoMatchingAppsExist() {
        assertEquals(
            listOf(AppProfileType.PERSONAL, AppProfileType.WORK),
            searchProfileFilterOptionsFor(
                apps = listOf(app("Camera", profile = AppProfile.personal())),
                filters = AppSearchFilters(profiles = setOf(AppProfileType.PERSONAL, AppProfileType.WORK)),
            ).map { option -> option.profileType },
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
