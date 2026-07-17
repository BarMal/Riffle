package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
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
    fun defaultSearchIncludesShortcutLabels() {
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

        assertEquals(emptyList<String>(), viewModel.state.value.searchResults.map { app -> app.label })
        assertEquals(
            listOf("New tab"),
            viewModel.state.value.searchShortcutResults.map { shortcut -> shortcut.shortLabel },
        )
    }

    @Test
    fun shortcutFilterCanExcludeShortcutLabelMatching() {
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
        viewModel.onAppActionSelected(
            LauncherShellAction.ToggleSearchContentFilter(AppSearchContentFilter.SHORTCUTS),
        )

        assertEquals(
            setOf(AppSearchContentFilter.APPS),
            viewModel.state.value.searchFilters.content,
        )
        assertEquals(emptyList<String>(), viewModel.state.value.searchResults.map { app -> app.label })
        assertEquals(
            emptyList<String>(),
            viewModel.state.value.searchShortcutResults.map { shortcut -> shortcut.shortLabel },
        )
    }

    @Test
    fun defaultsSearchResultsToAllAvailableProfiles() {
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

        assertEquals(
            setOf(AppProfileType.PERSONAL, AppProfileType.WORK),
            viewModel.state.value.searchFilters.profiles,
        )
        assertEquals(listOf("Camera", "Docs", "Sheets"), viewModel.state.value.searchResults.map { app -> app.label })
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
            LauncherShellAction.ToggleSearchProfileFilter(AppProfileType.PERSONAL),
        )

        assertEquals("cal", viewModel.state.value.searchQuery)
        assertEquals(setOf(AppProfileType.WORK), viewModel.state.value.searchFilters.profiles)
        assertEquals(listOf("Calendar"), viewModel.state.value.searchResults.map { app -> app.label })
        assertEquals(listOf(AppProfile.work()), viewModel.state.value.searchResults.map { app -> app.identity.profile })
    }

    @Test
    fun refreshesSearchResultsForCurrentProfileFilter() {
        val repository =
            FakeInstalledAppRepository(
                apps =
                    listOf(
                        app(label = "Camera", profile = AppProfile.personal()),
                        app(label = "Docs", profile = AppProfile.work()),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )
        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAppActionSelected(
            LauncherShellAction.ToggleSearchProfileFilter(AppProfileType.PERSONAL),
        )

        repository.apps =
            listOf(
                app(label = "Camera", profile = AppProfile.personal()),
                app(label = "Sheets", profile = AppProfile.work()),
            )
        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(setOf(AppProfileType.WORK), viewModel.state.value.searchFilters.profiles)
        assertEquals(listOf("Sheets"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    @Test
    fun refreshCoercesSearchProfileFiltersWhenWorkAppsDisappear() {
        val repository =
            FakeInstalledAppRepository(
                apps =
                    listOf(
                        app(label = "Camera", profile = AppProfile.personal()),
                        app(label = "Docs", profile = AppProfile.work()),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )
        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAppActionSelected(
            LauncherShellAction.ToggleSearchProfileFilter(AppProfileType.PERSONAL),
        )
        viewModel.onAppActionSelected(
            LauncherShellAction.ToggleSearchProfileFilter(AppProfileType.WORK),
        )

        repository.apps = listOf(app(label = "Camera", profile = AppProfile.personal()))
        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(setOf(AppProfileType.PERSONAL), viewModel.state.value.searchFilters.profiles)
        assertEquals(listOf("Camera"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    @Test
    fun clearsSearchQueryWhenLeavingSearch() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Camera"),
                                app(label = "Calendar"),
                            ),
                    ),
            )

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenSearch)
        viewModel.onAppActionSelected(LauncherShellAction.SearchQueryChanged("cam"))

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenHome)

        assertEquals(ShellDestination.HOME, viewModel.state.value.destination)
        assertEquals("", viewModel.state.value.searchQuery)
        assertEquals(listOf("Calendar", "Camera"), viewModel.state.value.searchResults.map { app -> app.label })
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
