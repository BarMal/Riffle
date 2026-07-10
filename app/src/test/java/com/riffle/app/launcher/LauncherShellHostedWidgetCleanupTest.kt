package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellHostedWidgetCleanupTest {
    @Test
    fun deletingSelectedHomePageDeletesHostedWidgetsFromThatPage() {
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages =
                    listOf(
                        HomeLayoutDefaults.standard().selectedPage,
                        HomeLayoutDefaults.standard().selectedPage.copy(
                            id = LauncherPageId("widgets"),
                            items = listOf(widget(id = "widget:42", hostedWidgetId = 42)),
                        ),
                    ),
                selectedPageId = LauncherPageId("widgets"),
            )
        val repository = FakeHomeLayoutRepository(savedLayout = layout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        deleteHostedWidgetId = deletedHostedWidgetIds::add,
                    ),
            )

        viewModel.onHomePageEdited(LauncherShellAction.DeleteSelectedHomePage)

        assertEquals(listOf(HostedWidgetId(42)), deletedHostedWidgetIds)
        assertEquals(listOf(LauncherPageId("home")), viewModel.state.value.homeLayout.pageIds)
    }

    @Test
    fun rejectedSelectedHomePageDeletionDoesNotDeleteHostedWidgets() {
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages =
                    listOf(
                        HomeLayoutDefaults.standard().selectedPage.copy(
                            items = listOf(widget(id = "widget:42", hostedWidgetId = 42)),
                        ),
                    ),
            )
        val repository = FakeHomeLayoutRepository(savedLayout = layout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        deleteHostedWidgetId = deletedHostedWidgetIds::add,
                    ),
            )

        viewModel.onHomePageEdited(LauncherShellAction.DeleteSelectedHomePage)

        assertTrue(deletedHostedWidgetIds.isEmpty())
        assertEquals(layout, viewModel.state.value.homeLayout)
    }

    @Test
    fun removingSelectedPageWidgetDeletesHostedWidgetAfterSuccessfulRemoval() {
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages =
                    listOf(
                        HomeLayoutDefaults.standard().selectedPage.copy(
                            items = listOf(widget(id = "widget:42", hostedWidgetId = 42)),
                        ),
                    ),
            )
        val repository = FakeHomeLayoutRepository(savedLayout = layout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        deleteHostedWidgetId = deletedHostedWidgetIds::add,
                    ),
            )

        viewModel.onHomeShortcutEdited(LauncherShellAction.RemoveHomeShortcut(LauncherItemId("widget:42")))

        assertEquals(listOf(HostedWidgetId(42)), deletedHostedWidgetIds)
        assertTrue(viewModel.state.value.homeLayout.selectedPage.items.isEmpty())
    }

    @Test
    fun rejectedSelectedPageWidgetRemovalDoesNotDeleteHostedWidget() {
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()
        val layout = HomeLayoutDefaults.standard()
        val repository = FakeHomeLayoutRepository(savedLayout = layout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        deleteHostedWidgetId = deletedHostedWidgetIds::add,
                    ),
            )

        viewModel.onHomeShortcutEdited(LauncherShellAction.RemoveHomeShortcut(LauncherItemId("widget:42")))

        assertTrue(deletedHostedWidgetIds.isEmpty())
        assertEquals(layout, viewModel.state.value.homeLayout)
    }

    @Test
    fun removingDockWidgetDeletesHostedWidgetAfterSuccessfulRemoval() {
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock =
                    HomeLayoutDefaults.standard().dock.copy(
                        items = listOf(widget(id = "dock-widget:43", hostedWidgetId = 43)),
                    ),
            )
        val repository = FakeHomeLayoutRepository(savedLayout = layout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        deleteHostedWidgetId = deletedHostedWidgetIds::add,
                    ),
            )

        viewModel.onDockEdited(LauncherShellAction.RemoveDockShortcut(LauncherItemId("dock-widget:43")))

        assertEquals(listOf(HostedWidgetId(43)), deletedHostedWidgetIds)
        assertTrue(viewModel.state.value.homeLayout.dock.items.isEmpty())
    }

    @Test
    fun rejectedDockWidgetRemovalDoesNotDeleteHostedWidget() {
        val deletedHostedWidgetIds = mutableListOf<HostedWidgetId>()
        val layout = HomeLayoutDefaults.standard()
        val repository = FakeHomeLayoutRepository(savedLayout = layout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        deleteHostedWidgetId = deletedHostedWidgetIds::add,
                    ),
            )

        viewModel.onDockEdited(LauncherShellAction.RemoveDockShortcut(LauncherItemId("dock-widget:43")))

        assertTrue(deletedHostedWidgetIds.isEmpty())
        assertEquals(layout, viewModel.state.value.homeLayout)
    }

    private fun widget(
        id: String,
        hostedWidgetId: Int,
    ): WidgetItem =
        WidgetItem(
            id = LauncherItemId(id),
            appWidgetId = HostedWidgetId(hostedWidgetId),
            label = "Weather",
            placement = GridPlacement(cell = GridCell(column = 0, row = 0), span = GridSpan(columns = 2, rows = 2)),
        )

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        private var savedLayout: HomeLayout,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }

    private val HomeLayout.pageIds: List<LauncherPageId>
        get() = pages.map { page -> page.id }
}
