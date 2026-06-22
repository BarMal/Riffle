package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellHiddenAppsViewModelTest {
    @Test
    fun excludesHiddenAppPreferencesFromLauncherAppSurfaces() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs", profile = AppProfile.work())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = FakeAppVisibilityRepository(hiddenApps = setOf(docs.identity)),
            )

        viewModel.onAppQueryChanged(LauncherShellAction.SearchQueryChanged(""))

        assertEquals(listOf(camera.identity), viewModel.state.value.installedApps.map { app -> app.identity })
        assertEquals(listOf(camera.identity), viewModel.state.value.appDrawerApps.map { app -> app.identity })
        assertEquals(listOf(camera.identity), viewModel.state.value.searchResults.map { app -> app.identity })
    }

    @Test
    fun refreshUsesLatestHiddenAppPreferences() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val appVisibilityRepository = FakeAppVisibilityRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = appVisibilityRepository,
            )

        appVisibilityRepository.hiddenApps = setOf(camera.identity)
        viewModel.refreshInstalledApps()

        assertEquals(listOf(docs.identity), viewModel.state.value.installedApps.map { app -> app.identity })
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

    private class FakeAppVisibilityRepository(
        var hiddenApps: Set<AppIdentity> = emptySet(),
    ) : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = hiddenApps

        override fun hideApp(identity: AppIdentity) {
            hiddenApps = hiddenApps + identity
        }

        override fun showApp(identity: AppIdentity) {
            hiddenApps = hiddenApps - identity
        }
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
