package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
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
            listOf("Personal (1)", "Work (2)", "Private (0)"),
            searchProfileFilterOptionsFor(
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
    fun showsEveryProfileOptionWhenNoMatchingAppsExist() {
        assertEquals(
            listOf(AppProfileType.PERSONAL, AppProfileType.WORK, AppProfileType.PRIVATE),
            searchProfileFilterOptionsFor(
                apps = listOf(app("Camera", profile = AppProfile.personal())),
            ).map { option -> option.profileType },
        )
    }

    @Test
    fun hidesEmptyUnselectedProfilesToKeepSearchControlsCompact() {
        assertEquals(
            listOf(AppProfileType.PERSONAL),
            searchProfileFilterOptionsFor(
                apps = listOf(app("Camera", profile = AppProfile.personal())),
                selectedProfiles = setOf(AppProfileType.PERSONAL),
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
