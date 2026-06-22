package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellAppDrawerViewModelTest {
    @Test
    fun loadsVisibleInstalledAppsIntoDrawer() {
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

        assertEquals(listOf("Browser", "Camera"), viewModel.state.value.appDrawerApps.map { app -> app.label })
    }

    @Test
    fun filtersAppDrawerAppsWhenDrawerQueryChanges() {
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

        viewModel.onAppQueryChanged(LauncherShellAction.AppDrawerQueryChanged("cam"))

        assertEquals("cam", viewModel.state.value.appDrawerQuery)
        assertEquals(listOf("Camera"), viewModel.state.value.appDrawerApps.map { app -> app.label })
        assertEquals("", viewModel.state.value.searchQuery)
    }

    @Test
    fun refreshesAppDrawerAppsForCurrentQuery() {
        val repository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )
        viewModel.onAppQueryChanged(LauncherShellAction.AppDrawerQueryChanged("cal"))

        repository.apps = listOf(app(label = "Calendar"))
        viewModel.refreshInstalledApps()

        assertEquals("cal", viewModel.state.value.appDrawerQuery)
        assertEquals(listOf("Calendar"), viewModel.state.value.appDrawerApps.map { app -> app.label })
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
