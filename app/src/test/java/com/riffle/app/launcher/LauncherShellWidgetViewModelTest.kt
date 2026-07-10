package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class LauncherShellWidgetViewModelTest {
    @Test
    fun refreshLoadsSortedWidgetProviders() {
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock")
        val calendar = widgetProvider(label = "Calendar", packageName = "com.example.calendar")
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        widgetProviderRepository = FakeWidgetProviderRepository(providers = listOf(clock, calendar)),
                    ),
            )

        runBlocking { viewModel.refreshWidgetProviders().join() }

        assertEquals(listOf(calendar, clock), viewModel.state.value.installedWidgetProviders)
    }

    @Test
    fun refreshInstalledAppsDoesNotRefreshWidgetProviders() {
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock")
        val calendar = widgetProvider(label = "Calendar", packageName = "com.example.calendar")
        val widgetProviderRepository = FakeWidgetProviderRepository(providers = listOf(clock))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        widgetProviderRepository = widgetProviderRepository,
                    ),
            )
        runBlocking { viewModel.refreshWidgetProviders().join() }
        widgetProviderRepository.providers = listOf(calendar)
        widgetProviderRepository.providerReadCount = 0

        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(0, widgetProviderRepository.providerReadCount)
        assertEquals(listOf(clock), viewModel.state.value.installedWidgetProviders)
    }

    @Test
    fun refreshWidgetProvidersUpdatesSortedProviders() {
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock")
        val calendar = widgetProvider(label = "Calendar", packageName = "com.example.calendar")
        val widgetProviderRepository = FakeWidgetProviderRepository(providers = listOf(clock))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        widgetProviderRepository = widgetProviderRepository,
                    ),
            )

        widgetProviderRepository.providers = listOf(clock, calendar)
        runBlocking { viewModel.refreshWidgetProviders().join() }

        assertEquals(listOf(calendar, clock), viewModel.state.value.installedWidgetProviders)
    }

    @Test
    fun refreshWidgetProvidersPreservesExistingProvidersWhenRepositoryFails() {
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock")
        val widgetProviderRepository = FakeWidgetProviderRepository(providers = listOf(clock))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        widgetProviderRepository = widgetProviderRepository,
                    ),
            )
        runBlocking { viewModel.refreshWidgetProviders().join() }

        widgetProviderRepository.failReads = true
        val refreshJob = viewModel.refreshWidgetProviders()
        runBlocking { refreshJob.join() }

        assertEquals(false, refreshJob.isCancelled)
        assertEquals(listOf(clock), viewModel.state.value.installedWidgetProviders)
    }

    @Test
    fun refreshWidgetProvidersCoalescesQueuedRefreshes() {
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock")
        val calendar = widgetProvider(label = "Calendar", packageName = "com.example.calendar")
        val widgetProviderRepository = FakeWidgetProviderRepository()
        val dispatcher = QueuedDispatcher()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        widgetProviderRepository = widgetProviderRepository,
                        loadInitialPlatformState = false,
                    ),
                refreshDispatcher = dispatcher,
            )

        widgetProviderRepository.providers = listOf(clock)
        val firstRefresh = viewModel.refreshWidgetProviders()
        widgetProviderRepository.providers = listOf(calendar)
        val secondRefresh = viewModel.refreshWidgetProviders()

        dispatcher.runQueued()
        runBlocking {
            firstRefresh.join()
            secondRefresh.join()
        }

        assertEquals(1, widgetProviderRepository.providerReadCount)
        assertEquals(listOf(calendar), viewModel.state.value.installedWidgetProviders)
    }

    @Test
    fun openingWidgetPickerDefersWidgetProviderRefresh() {
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock")
        val widgetProviderRepository = FakeWidgetProviderRepository(providers = listOf(clock))
        val dispatcher = QueuedDispatcher()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        widgetProviderRepository = widgetProviderRepository,
                        loadInitialPlatformState = false,
                    ),
                refreshDispatcher = dispatcher,
            )

        val refreshJob = viewModel.onAppActionSelected(LauncherShellAction.OpenWidgetPicker)

        assertEquals(true, viewModel.state.value.isWidgetPickerOpen)
        assertEquals(0, widgetProviderRepository.providerReadCount)

        dispatcher.runQueued()
        runBlocking { refreshJob?.join() }

        assertEquals(1, widgetProviderRepository.providerReadCount)
        assertEquals(listOf(clock), viewModel.state.value.installedWidgetProviders)
    }

    @Test
    fun addsHostedWidgetToHomeAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.AddHostedWidgetToHome(
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
            ),
        )

        assertEquals(
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            ),
            viewModel.state.value.homeLayout.selectedPage.items.single(),
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun resizesHostedWidgetAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomeShortcutEdited(
            LauncherShellAction.AddHostedWidgetToHome(
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
            ),
        )

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.ResizeHomeWidget(
                itemId = LauncherItemId("widget:42"),
                span = GridSpan(columns = 2, rows = 2),
            ),
        )

        assertEquals(
            GridPlacement(
                cell = GridCell(column = 0, row = 0),
                span = GridSpan(columns = 2, rows = 2),
            ),
            viewModel.state.value.homeLayout.selectedPage.items.single().placement,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun rejectingNonWidgetResizeLeavesLayoutAndSavedStateUnchanged() {
        val shortcut =
            shortcutItem(
                id = "app:camera:1",
                label = "Camera",
                cell = GridCell(column = 0, row = 0),
            )
        val initialLayout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(shortcut))),
            )
        val repository = FakeHomeLayoutRepository(savedLayout = initialLayout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.ResizeHomeWidget(
                itemId = shortcut.id,
                span = GridSpan(columns = 2, rows = 2),
            ),
        )

        assertEquals(initialLayout, viewModel.state.value.homeLayout)
        assertEquals(initialLayout, repository.savedLayout)
    }

    @Test
    fun addsHostedWidgetToTargetCellAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.AddHostedWidgetToHome(
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
                preferredSpan = GridSpan(columns = 2, rows = 2),
                targetCell = GridCell(column = 1, row = 2),
            ),
        )

        assertEquals(
            GridPlacement(
                cell = GridCell(column = 1, row = 2),
                span = GridSpan(columns = 2, rows = 2),
            ),
            viewModel.state.value.homeLayout.selectedPage.items.single().placement,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }

    private class FakeWidgetProviderRepository(
        var providers: List<InstalledWidgetProvider> = emptyList(),
    ) : InstalledWidgetProviderRepository {
        var providerReadCount: Int = 0
        var failReads: Boolean = false

        override fun installedWidgetProviders(): List<InstalledWidgetProvider> {
            providerReadCount += 1
            if (failReads) {
                error("Widget provider query failed")
            }
            return providers
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

    private fun widgetProvider(
        label: String,
        packageName: String,
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName(packageName),
                    className = WidgetProviderClassName(".WidgetProvider"),
                ),
            label = label,
            dimensions = WidgetProviderDimensions(minWidthDp = 100, minHeightDp = 50),
        )

    private fun shortcutItem(
        id: String,
        label: String,
        cell: GridCell,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                com.riffle.core.domain.launcher.apps.AppIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase()}"),
                    activityName = com.riffle.core.domain.launcher.apps.AppActivityName(".MainActivity"),
                ),
            label = label,
            placement = GridPlacement(cell = cell),
        )
}
