package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellAppShortcutHomeViewModelTest {
    @Test
    fun addsPlatformAppShortcutToFirstAvailableHomeCell() {
        val camera = app(label = "Camera")
        val shortcut =
            AppShortcut(
                id = AppShortcutId("selfie"),
                appIdentity = camera.identity,
                shortLabel = "Selfie",
                longLabel = "Take selfie",
            )
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(LauncherShellAction.AddAppShortcutToHome(shortcut))

        val item = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem
        assertEquals(camera.identity, item.appIdentity)
        assertEquals(shortcut.id, item.appShortcutId)
        assertEquals("Take selfie", item.label)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), item.placement)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresDuplicatePlatformAppShortcut() {
        val camera = app(label = "Camera")
        val shortcut =
            AppShortcut(
                id = AppShortcutId("selfie"),
                appIdentity = camera.identity,
                shortLabel = "Selfie",
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
            )

        viewModel.onHomeShortcutEdited(LauncherShellAction.AddAppShortcutToHome(shortcut))
        val layoutBeforeDuplicate = viewModel.state.value.homeLayout
        viewModel.onHomeShortcutEdited(LauncherShellAction.AddAppShortcutToHome(shortcut))

        assertEquals(layoutBeforeDuplicate, viewModel.state.value.homeLayout)
        assertEquals(1, viewModel.state.value.homeLayout.selectedPage.items.size)
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
