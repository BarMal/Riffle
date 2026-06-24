package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellMoveHomeShortcutToCellViewModelTest {
    @Test
    fun movesHomeShortcutToCellAndSavesLayout() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)
        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem

        viewModel.onHomeShortcutEdited(
            LauncherShellAction.MoveHomeShortcutToCell(
                itemId = shortcut.id,
                cell = GridCell(column = 2, row = 1),
            ),
        )

        assertEquals(
            GridPlacement(cell = GridCell(column = 2, row = 1)),
            viewModel.state.value.homeLayout.selectedPage.items.single().placement,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeInstalledAppRepository(
        private val apps: List<InstalledApp>,
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
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
