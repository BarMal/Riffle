package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellPageOverviewViewModelTest {
    @Test
    fun entersPageOverviewAndSelectsPagesDirectly() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(LauncherShellAction.EnterHomePageOverview)
        viewModel.onHomePageEdited(LauncherShellAction.SelectHomePage(LauncherPageId("home")))

        assertEquals(HomeEditMode.ManagingPages, viewModel.state.value.homeLayout.editMode)
        assertEquals(LauncherPageId("home"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun editsSelectedPageFromOverview() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.EnterHomePageOverview)

        viewModel.onHomePageEdited(LauncherShellAction.EnterHomeEditMode)

        assertEquals(
            HomeEditMode.EditingPage(pageId = LauncherPageId("home")),
            viewModel.state.value.homeLayout.editMode,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun configuredGestureEntryRoutesToPageOverview() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val handler =
            LauncherActivityActionHandler(
                requestDefaultHome = {},
                navigate = {},
                editHomePage = viewModel::onHomePageEdited,
                editHomeShortcut = {},
                editDock = {},
                hostedWidgetIdForRemovedItem = { null },
                deleteHostedWidget = {},
            )

        val action =
            homeSwipeActionForDrag(
                pointerCount = 2,
                horizontalDragPx = 0f,
                verticalDragPx = 0f,
                scaleDelta = 0.2f,
                settings =
                    HomeGestureSettings(
                        actions = mapOf(HomeGesture.PINCH_OUT to LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW),
                    ),
                interpreter = HomeSwipeGestureInterpreter(thresholdPx = 80f),
            )

        assertNotNull(action)
        assertTrue(handler.handle(checkNotNull(action)))
        assertEquals(HomeEditMode.ManagingPages, viewModel.state.value.homeLayout.editMode)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun updatesSelectedPageTypeFromOverview() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.EnterHomePageOverview)

        viewModel.onHomePageEdited(LauncherShellAction.SelectSelectedHomePageType(LauncherPageType.AllApps))

        assertEquals(LauncherPageType.AllApps, viewModel.state.value.homeLayout.selectedPage.type)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun updatesSelectedPageGridFromOverviewWithoutChangingSiblingOrDefaultGrid() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)
        viewModel.onHomePageEdited(LauncherShellAction.EnterHomePageOverview)
        val defaultGrid = viewModel.state.value.homeLayout.settings.grid.dimensions

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectSelectedHomePageGridDimensions(GridDimensions(columns = 5, rows = 6)),
        )

        assertEquals(defaultGrid, viewModel.state.value.homeLayout.pages[0].grid)
        assertEquals(GridDimensions(columns = 5, rows = 6), viewModel.state.value.homeLayout.selectedPage.grid)
        assertEquals(defaultGrid, viewModel.state.value.homeLayout.settings.grid.dimensions)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun duplicatesSelectedPageWhileStayingInOverview() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)
        viewModel.onHomePageEdited(LauncherShellAction.EnterHomePageOverview)
        viewModel.onHomePageEdited(LauncherShellAction.SelectHomePage(LauncherPageId("home-2")))

        viewModel.onHomePageEdited(LauncherShellAction.DuplicateSelectedHomePage)

        assertEquals(HomeEditMode.ManagingPages, viewModel.state.value.homeLayout.editMode)
        assertEquals(3, viewModel.state.value.homeLayout.pages.size)
        assertEquals(LauncherPageId("home-3"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun deletesSelectedPageWhileStayingInOverview() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)
        viewModel.onHomePageEdited(LauncherShellAction.EnterHomePageOverview)
        viewModel.onHomePageEdited(LauncherShellAction.SelectHomePage(LauncherPageId("home-2")))

        viewModel.onHomePageEdited(LauncherShellAction.DeleteSelectedHomePage)

        assertEquals(HomeEditMode.ManagingPages, viewModel.state.value.homeLayout.editMode)
        assertEquals(listOf(LauncherPageId("home")), viewModel.state.value.homeLayout.pages.map { page -> page.id })
        assertEquals(LauncherPageId("home"), viewModel.state.value.homeLayout.selectedPageId)
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
}
