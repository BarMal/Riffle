package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppSearchScope
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellSearchViewModelTest {
    @Test
    fun filtersSearchResultsByShortcutLabels() {
        val camera = app(label = "Camera")
        val browser = app(label = "Browser")
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps = listOf(camera, browser),
                        shortcuts =
                            mapOf(
                                camera.identity to listOf(shortcut(app = camera, label = "Selfie")),
                                browser.identity to listOf(shortcut(app = browser, label = "New tab")),
                            ),
                    ),
            )

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAppActionSelected(LauncherShellAction.SearchQueryChanged("new tab"))

        assertEquals(listOf("Browser"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    @Test
    fun searchScopeControlsShortcutLabelMatching() {
        val browser = app(label = "Browser")
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps = listOf(browser),
                        shortcuts =
                            mapOf(
                                browser.identity to listOf(shortcut(app = browser, label = "New tab")),
                            ),
                    ),
            )

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAppActionSelected(LauncherShellAction.SearchQueryChanged("new tab"))
        viewModel.onAppActionSelected(LauncherShellAction.SearchScopeSelected(AppSearchScope.APPS))

        assertEquals(AppSearchScope.APPS, viewModel.state.value.searchScope)
        assertEquals(emptyList<String>(), viewModel.state.value.searchResults.map { app -> app.label })
    }

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

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAppActionSelected(
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

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAppActionSelected(LauncherShellAction.SearchQueryChanged("cal"))
        viewModel.onAppActionSelected(
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
        viewModel.onAppActionSelected(
            LauncherShellAction.SearchProfileFilterSelected(AppDrawerProfileFilter.WORK),
        )

        repository.apps =
            listOf(
                app(label = "Camera", profile = AppProfile.personal()),
                app(label = "Sheets", profile = AppProfile.work()),
            )
        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(AppDrawerProfileFilter.WORK, viewModel.state.value.searchProfileFilter)
        assertEquals(listOf("Sheets"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeInstalledAppRepository(
        var apps: List<InstalledApp> = emptyList(),
        private val shortcuts: Map<AppIdentity, List<AppShortcut>> = emptyMap(),
    ) : InstalledAppRepository,
        AppShortcutRepository {
        override fun installedApps(): List<InstalledApp> = apps

        override fun shortcutsFor(apps: List<InstalledApp>): AppShortcutsByApp = shortcuts
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

    private fun shortcut(
        app: InstalledApp,
        label: String,
    ): AppShortcut =
        AppShortcut(
            id = AppShortcutId("${app.identity.packageName.value}:$label"),
            appIdentity = app.identity,
            shortLabel = label,
        )
}
