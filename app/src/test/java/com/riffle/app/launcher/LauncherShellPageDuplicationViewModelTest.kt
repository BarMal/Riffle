package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellPageDuplicationViewModelTest {
    @Test
    fun duplicatesSelectedHomePageAndSavesLayout() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)
        val originalShortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem

        viewModel.onHomePageEdited(LauncherShellAction.DuplicateSelectedHomePage)

        val duplicatedPage = viewModel.state.value.homeLayout.selectedPage
        val duplicatedShortcut = duplicatedPage.items.single() as AppShortcutItem
        assertEquals(listOf(LauncherPageId("home"), LauncherPageId("home-2")), viewModel.state.value.homeLayout.pageIds)
        assertEquals(LauncherPageId("home-2"), duplicatedPage.id)
        assertEquals(originalShortcut.appIdentity, duplicatedShortcut.appIdentity)
        assertEquals(originalShortcut.placement, duplicatedShortcut.placement)
        assertTrue(originalShortcut.id != duplicatedShortcut.id)
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

    private val HomeLayout.pageIds: List<LauncherPageId>
        get() = pages.map { page -> page.id }
}
