package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellHomeGridViewModelTest {
    @Test
    fun updatesHomeGridDimensionsAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeGridDimensions(GridDimensions(columns = 5, rows = 6)),
        )

        assertEquals(
            listOf(GridDimensions(columns = 5, rows = 6), GridDimensions(columns = 5, rows = 6)),
            viewModel.state.value.homeLayout.pages.map { page -> page.grid },
        )
        assertEquals(
            GridDimensions(columns = 5, rows = 6),
            viewModel.state.value.homeLayout.settings.grid.dimensions,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresHomeGridDimensionsThatWouldClipItems() {
        val camera = app(label = "Camera")
        val shortcut =
            AppShortcutItem(
                id = LauncherItemId("app:camera:home"),
                appIdentity = camera.identity,
                label = camera.label,
                placement = GridPlacement(cell = GridCell(column = 3, row = 4)),
            )
        val savedLayout =
            HomeLayoutDefaults.standard().let { layout ->
                layout.copy(
                    pages = listOf(layout.selectedPage.copy(items = listOf(shortcut))),
                )
            }
        val repository = FakeHomeLayoutRepository(savedLayout = savedLayout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val layoutBeforeResize = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeGridDimensions(GridDimensions(columns = 3, rows = 5)),
        )

        assertEquals(layoutBeforeResize, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeResize, repository.savedLayout)
    }

    private class FakeFirstRunRepository(
        private var isComplete: Boolean = false,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = isComplete

        override fun setFirstRunComplete() {
            isComplete = true
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

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )
}
