package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellViewModelTest {
    @Test
    fun startsWithDefaultHomePromptVisible() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        assertTrue(viewModel.state.value.shouldShowDefaultHomePrompt)
    }

    @Test
    fun recordsDefaultHomeRequestInProgress() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onDefaultHomeRequestStarted()

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
    }

    @Test
    fun hidesPromptWhenAppBecomesDefaultHome() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        viewModel.onHomeRoleStatusChanged(HomeRoleStatus.DEFAULT_HOME)

        assertFalse(viewModel.state.value.shouldShowDefaultHomePrompt)
        assertTrue(viewModel.state.value.shouldShowEmptyHome)
        assertTrue(repository.isFirstRunComplete())
    }

    @Test
    fun restoresCompletedFirstRunFromRepository() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(isComplete = true),
            )

        assertEquals(FirstRunStatus.COMPLETE, viewModel.state.value.firstRunStatus)
        assertTrue(viewModel.state.value.shouldShowEmptyHome)
    }

    @Test
    fun navigatesBetweenShellDestinations() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenAppDrawer)
        assertEquals(ShellDestination.APP_DRAWER, viewModel.state.value.destination)

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenHome)
        assertEquals(ShellDestination.HOME, viewModel.state.value.destination)
    }

    @Test
    fun loadsVisibleInstalledAppsIntoInitialState() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Hidden", visibility = AppVisibility.HIDDEN),
                                app(label = "Camera"),
                                app(label = "Browser"),
                            ),
                    ),
            )

        assertEquals(
            listOf("Browser", "Camera"),
            viewModel.state.value.installedApps.map { app -> app.label },
        )
    }

    @Test
    fun refreshesInstalledApps() {
        val repository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )

        repository.apps = listOf(app(label = "Calendar"))
        viewModel.refreshInstalledApps()

        assertEquals(listOf("Calendar"), viewModel.state.value.installedApps.map { app -> app.label })
    }

    @Test
    fun filtersSearchResultsWhenQueryChanges() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Camera"),
                                app(label = "Calendar"),
                                app(label = "Maps"),
                            ),
                    ),
            )

        viewModel.onSearchQueryChanged("cam")

        assertEquals("cam", viewModel.state.value.searchQuery)
        assertEquals(listOf("Camera"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    @Test
    fun refreshesSearchResultsForCurrentQuery() {
        val repository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )
        viewModel.onSearchQueryChanged("cal")

        repository.apps = listOf(app(label = "Calendar"))
        viewModel.refreshInstalledApps()

        assertEquals("cal", viewModel.state.value.searchQuery)
        assertEquals(listOf("Calendar"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    private class FakeFirstRunRepository(
        private var isComplete: Boolean = false,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = isComplete

        override fun setFirstRunComplete() {
            isComplete = true
        }
    }

    private class FakeInstalledAppRepository(
        var apps: List<InstalledApp> = emptyList(),
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    private fun app(
        label: String,
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
            visibility = visibility,
        )
}
