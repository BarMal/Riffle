package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.CoroutineContext

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

        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAppActionSelected(LauncherShellAction.SearchQueryChanged(""))

        assertEquals(listOf(camera.identity), viewModel.state.value.installedApps.map { app -> app.identity })
        assertEquals(listOf(docs.identity), viewModel.state.value.hiddenApps.map { app -> app.identity })
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
        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(listOf(docs.identity), viewModel.state.value.installedApps.map { app -> app.identity })
    }

    @Test
    fun hidesAppAndRefreshesLauncherAppSurfaces() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val appVisibilityRepository = FakeAppVisibilityRepository()
        val dispatcher = QueuedDispatcher()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = appVisibilityRepository,
                refreshDispatcher = dispatcher,
            )

        runQueuedRefresh(
            refreshJob = viewModel.onAppActionSelected(LauncherShellAction.HideApp(camera.identity)),
            dispatcher = dispatcher,
        )

        assertEquals(setOf(camera.identity), appVisibilityRepository.hiddenApps)
        assertEquals(listOf(docs.identity), viewModel.state.value.installedApps.map { app -> app.identity })
        assertEquals(listOf(camera.identity), viewModel.state.value.hiddenApps.map { app -> app.identity })
        assertEquals(listOf(docs.identity), viewModel.state.value.appDrawerApps.map { app -> app.identity })
    }

    @Test
    fun hidingAppDefersVisibilityWriteAndRefresh() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val appVisibilityRepository = FakeAppVisibilityRepository()
        val dispatcher = QueuedDispatcher()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = appVisibilityRepository,
                platformDependencies = LauncherShellPlatformDependencies(loadInitialPlatformState = false),
                refreshDispatcher = dispatcher,
            )

        val refreshJob = viewModel.onAppActionSelected(LauncherShellAction.HideApp(camera.identity))

        assertEquals(emptySet<AppIdentity>(), appVisibilityRepository.hiddenApps)

        dispatcher.runQueued()
        runBlocking { refreshJob?.join() }

        assertEquals(setOf(camera.identity), appVisibilityRepository.hiddenApps)
        assertEquals(listOf(docs.identity), viewModel.state.value.installedApps.map { app -> app.identity })
    }

    @Test
    fun hidingHomeAppPreservesItsGridPlacement() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val maps = app(label = "Maps")
        val dispatcher = QueuedDispatcher()
        val homeLayoutRepository =
            FakeHomeLayoutRepository(
                savedLayout =
                    HomeLayoutDefaults.standard().copy(
                        pages =
                            listOf(
                                LauncherPage(
                                    id = LauncherPageId("home"),
                                    grid = GridDimensions(columns = 1, rows = 1),
                                    items =
                                        listOf(
                                            shortcut(
                                                id = "camera",
                                                app = camera,
                                                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                            ),
                                        ),
                                ),
                            ),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs, maps)),
                appVisibilityRepository = FakeAppVisibilityRepository(),
                homeLayoutRepository = homeLayoutRepository,
                refreshDispatcher = dispatcher,
            )

        runQueuedRefresh(
            refreshJob = viewModel.onAppActionSelected(LauncherShellAction.HideApp(camera.identity)),
            dispatcher = dispatcher,
        )
        viewModel.onAddAppToHome(maps)

        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem
        assertEquals(camera.identity, shortcut.appIdentity)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), shortcut.placement)
        assertEquals(viewModel.state.value.homeLayout, homeLayoutRepository.savedLayout)
    }

    @Test
    fun hidingDockAppPreservesItsDockPlacement() {
        val phone = app(label = "Phone")
        val camera = app(label = "Camera")
        val dispatcher = QueuedDispatcher()
        val homeLayoutRepository =
            FakeHomeLayoutRepository(
                savedLayout =
                    HomeLayoutDefaults.standard().copy(
                        dock = DockModel(capacity = 1, items = listOf(shortcut(id = "phone", app = phone))),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(phone, camera)),
                appVisibilityRepository = FakeAppVisibilityRepository(),
                homeLayoutRepository = homeLayoutRepository,
                refreshDispatcher = dispatcher,
            )

        runQueuedRefresh(
            refreshJob = viewModel.onAppActionSelected(LauncherShellAction.HideApp(phone.identity)),
            dispatcher = dispatcher,
        )

        val shortcut = viewModel.state.value.homeLayout.dock.items.single() as AppShortcutItem
        assertEquals(phone.identity, shortcut.appIdentity)
        assertEquals(viewModel.state.value.homeLayout, homeLayoutRepository.savedLayout)
    }

    @Test
    fun authoritativeRefreshDoesNotPruneRemovedHomeAppWithoutConfirmedRemoval() {
        val camera = app(label = "Camera")
        val maps = app(label = "Maps")
        val installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, maps))
        val homeLayoutRepository =
            FakeHomeLayoutRepository(
                savedLayout =
                    HomeLayoutDefaults.standard().copy(
                        pages =
                            listOf(
                                LauncherPage(
                                    id = LauncherPageId("home"),
                                    grid = GridDimensions(columns = 1, rows = 1),
                                    items =
                                        listOf(
                                            shortcut(
                                                id = "camera",
                                                app = camera,
                                                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                            ),
                                        ),
                                ),
                            ),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = installedAppRepository,
                homeLayoutRepository = homeLayoutRepository,
            )

        installedAppRepository.apps = listOf(maps)
        runBlocking { viewModel.refreshInstalledApps().join() }
        viewModel.onAddAppToHome(maps)

        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem
        assertEquals(camera.identity, shortcut.appIdentity)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), shortcut.placement)
        assertEquals(viewModel.state.value.homeLayout, homeLayoutRepository.savedLayout)
    }

    @Test
    fun authoritativeRefreshDoesNotPruneRemovedDockAppWithoutConfirmedRemoval() {
        val phone = app(label = "Phone")
        val camera = app(label = "Camera")
        val installedAppRepository = FakeInstalledAppRepository(apps = listOf(phone, camera))
        val homeLayoutRepository =
            FakeHomeLayoutRepository(
                savedLayout =
                    HomeLayoutDefaults.standard().copy(
                        dock = DockModel(capacity = 1, items = listOf(shortcut(id = "phone", app = phone))),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = installedAppRepository,
                homeLayoutRepository = homeLayoutRepository,
            )

        installedAppRepository.apps = listOf(camera)
        runBlocking { viewModel.refreshInstalledApps().join() }

        val shortcut = viewModel.state.value.homeLayout.dock.items.single() as AppShortcutItem
        assertEquals(phone.identity, shortcut.appIdentity)
        assertEquals(viewModel.state.value.homeLayout, homeLayoutRepository.savedLayout)
    }

    @Test
    fun unhidesAppAndRefreshesLauncherAppSurfaces() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val appVisibilityRepository = FakeAppVisibilityRepository(hiddenApps = setOf(camera.identity))
        val dispatcher = QueuedDispatcher()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = appVisibilityRepository,
                refreshDispatcher = dispatcher,
            )

        runQueuedRefresh(
            refreshJob = viewModel.onAppActionSelected(LauncherShellAction.UnhideApp(camera.identity)),
            dispatcher = dispatcher,
        )

        assertEquals(emptySet<AppIdentity>(), appVisibilityRepository.hiddenApps)
        assertEquals(
            listOf(camera.identity, docs.identity),
            viewModel.state.value.installedApps.map { app -> app.identity },
        )
        assertEquals(emptyList<AppIdentity>(), viewModel.state.value.hiddenApps.map { app -> app.identity })
    }

    private fun runQueuedRefresh(
        refreshJob: Job?,
        dispatcher: QueuedDispatcher,
    ) {
        dispatcher.runQueued()
        runBlocking { refreshJob?.join() }
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

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val blocks = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            blocks += block
        }

        fun runQueued() {
            while (blocks.isNotEmpty()) {
                blocks.removeFirst().run()
            }
        }
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
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

    private fun shortcut(
        id: String,
        app: InstalledApp,
        placement: GridPlacement? = null,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = app.identity,
            label = app.label,
            placement = placement,
        )
}
