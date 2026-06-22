package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellSearchViewModelTest {
    @Test
    fun filtersSearchResultsBySelectedProfile() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Camera", profile = AppProfile.personal()),
                                app(label = "Docs", profile = AppProfile.work()),
                                app(label = "Sheets", profile = AppProfile.work()),
                            ),
                    ),
            )

        viewModel.onAppQueryChanged(
            LauncherShellAction.SearchProfileFilterSelected(AppDrawerProfileFilter.WORK),
        )

        assertEquals(AppDrawerProfileFilter.WORK, viewModel.state.value.searchProfileFilter)
        assertEquals(listOf("Docs", "Sheets"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    @Test
    fun combinesSearchQueryAndProfileFilter() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Calendar", profile = AppProfile.personal()),
                                app(label = "Calendar", profile = AppProfile.work()),
                                app(label = "Camera", profile = AppProfile.work()),
                            ),
                    ),
            )

        viewModel.onAppQueryChanged(LauncherShellAction.SearchQueryChanged("cal"))
        viewModel.onAppQueryChanged(
            LauncherShellAction.SearchProfileFilterSelected(AppDrawerProfileFilter.WORK),
        )

        assertEquals("cal", viewModel.state.value.searchQuery)
        assertEquals(AppDrawerProfileFilter.WORK, viewModel.state.value.searchProfileFilter)
        assertEquals(listOf("Calendar"), viewModel.state.value.searchResults.map { app -> app.label })
        assertEquals(listOf(AppProfile.work()), viewModel.state.value.searchResults.map { app -> app.identity.profile })
    }

    @Test
    fun refreshesSearchResultsForCurrentProfileFilter() {
        val repository =
            FakeInstalledAppRepository(
                apps = listOf(app(label = "Docs", profile = AppProfile.work())),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )
        viewModel.onAppQueryChanged(
            LauncherShellAction.SearchProfileFilterSelected(AppDrawerProfileFilter.WORK),
        )

        repository.apps =
            listOf(
                app(label = "Camera", profile = AppProfile.personal()),
                app(label = "Sheets", profile = AppProfile.work()),
            )
        viewModel.refreshInstalledApps()

        assertEquals(AppDrawerProfileFilter.WORK, viewModel.state.value.searchProfileFilter)
        assertEquals(listOf("Sheets"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeInstalledAppRepository(
        var apps: List<InstalledApp> = emptyList(),
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    private fun app(
        label: String,
        profile: AppProfile = AppProfile.personal(),
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = label,
        )
}
