package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import kotlinx.coroutines.runBlocking
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

        runBlocking { viewModel.refreshInstalledApps().join() }

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

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAppActionSelected(LauncherShellAction.AppDrawerQueryChanged("cam"))

        assertEquals("cam", viewModel.state.value.appDrawerQuery)
        assertEquals(listOf("Camera"), viewModel.state.value.appDrawerApps.map { app -> app.label })
        assertEquals("", viewModel.state.value.searchQuery)
    }

    @Test
    fun filtersAppDrawerAppsByShortcutLabels() {
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
        viewModel.onAppActionSelected(LauncherShellAction.AppDrawerQueryChanged("selfie"))

        assertEquals(listOf("Camera"), viewModel.state.value.appDrawerApps.map { app -> app.label })
    }

    @Test
    fun refreshesAppDrawerAppsForCurrentQuery() {
        val repository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )
        viewModel.onAppActionSelected(LauncherShellAction.AppDrawerQueryChanged("cal"))

        repository.apps = listOf(app(label = "Calendar"))
        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals("cal", viewModel.state.value.appDrawerQuery)
        assertEquals(listOf("Calendar"), viewModel.state.value.appDrawerApps.map { app -> app.label })
    }

    @Test
    fun clearsAppDrawerQueryWhenLeavingDrawer() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Browser"),
                                app(label = "Camera"),
                            ),
                    ),
            )

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenAppDrawer)
        viewModel.onAppActionSelected(LauncherShellAction.AppDrawerQueryChanged("cam"))

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenHome)

        assertEquals(ShellDestination.HOME, viewModel.state.value.destination)
        assertEquals("", viewModel.state.value.appDrawerQuery)
        assertEquals(listOf("Browser", "Camera"), viewModel.state.value.appDrawerApps.map { app -> app.label })
    }

    @Test
    fun loadsAppShortcutsForVisibleApps() {
        val hidden = app(label = "Hidden", visibility = AppVisibility.HIDDEN)
        val camera = app(label = "Camera")
        val repository =
            FakeInstalledAppRepository(
                apps = listOf(hidden, camera),
                shortcuts =
                    mapOf(
                        hidden.identity to listOf(shortcut(app = hidden, label = "Hidden action")),
                        camera.identity to listOf(shortcut(app = camera, label = "Scan")),
                    ),
            )

        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )

        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(listOf(camera), repository.requestedShortcutApps)
        assertEquals(
            listOf("Scan"),
            viewModel.state.value.appShortcutsByApp.getValue(camera.identity).map { shortcut -> shortcut.shortLabel },
        )
        assertEquals(false, hidden.identity in viewModel.state.value.appShortcutsByApp)
    }

    @Test
    fun refreshesAppShortcutsWithInstalledApps() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val repository =
            FakeInstalledAppRepository(
                apps = listOf(camera),
                shortcuts =
                    mapOf(
                        camera.identity to listOf(shortcut(app = camera, label = "Scan")),
                        calendar.identity to listOf(shortcut(app = calendar, label = "Event")),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )

        repository.apps = listOf(calendar)
        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(listOf(calendar), repository.requestedShortcutApps)
        assertEquals(
            mapOf(calendar.identity to listOf(shortcut(app = calendar, label = "Event"))),
            viewModel.state.value.appShortcutsByApp,
        )
    }

    @Test
    fun filtersAppDrawerAppsBySelectedProfile() {
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
                                app(label = "Vault", profile = AppProfile.private()),
                            ),
                    ),
            )

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAppActionSelected(
            LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.WORK),
        )

        assertEquals(AppDrawerProfileFilter.WORK, viewModel.state.value.appDrawerProfileFilter)
        assertEquals(listOf("Docs", "Sheets"), viewModel.state.value.appDrawerApps.map { app -> app.label })

        viewModel.onAppActionSelected(
            LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.PRIVATE),
        )

        assertEquals(AppDrawerProfileFilter.PRIVATE, viewModel.state.value.appDrawerProfileFilter)
        assertEquals(listOf("Vault"), viewModel.state.value.appDrawerApps.map { app -> app.label })
    }

    @Test
    fun combinesAppDrawerQueryAndProfileFilter() {
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
        viewModel.onAppActionSelected(LauncherShellAction.AppDrawerQueryChanged("cal"))
        viewModel.onAppActionSelected(
            LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.WORK),
        )

        assertEquals("cal", viewModel.state.value.appDrawerQuery)
        assertEquals(AppDrawerProfileFilter.WORK, viewModel.state.value.appDrawerProfileFilter)
        assertEquals(listOf("Calendar"), viewModel.state.value.appDrawerApps.map { app -> app.label })
        assertEquals(listOf(AppProfile.work()), viewModel.state.value.appDrawerApps.map { app -> app.identity.profile })
    }

    @Test
    fun refreshesAppDrawerAppsForCurrentProfileFilter() {
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
            LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.WORK),
        )

        repository.apps =
            listOf(
                app(label = "Camera", profile = AppProfile.personal()),
                app(label = "Sheets", profile = AppProfile.work()),
            )
        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(AppDrawerProfileFilter.WORK, viewModel.state.value.appDrawerProfileFilter)
        assertEquals(listOf("Sheets"), viewModel.state.value.appDrawerApps.map { app -> app.label })
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
        var requestedShortcutApps: List<InstalledApp> = emptyList()

        override fun installedApps(): List<InstalledApp> = apps

        override fun shortcutsFor(apps: List<InstalledApp>): AppShortcutsByApp {
            requestedShortcutApps = apps
            return shortcuts
        }
    }

    private fun app(
        label: String,
        visibility: AppVisibility = AppVisibility.VISIBLE,
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
            visibility = visibility,
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
