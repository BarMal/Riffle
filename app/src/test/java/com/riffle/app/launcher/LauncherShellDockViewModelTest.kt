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
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellDockViewModelTest {
    @Test
    fun updatesDockVisibilityAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockEnabled(enabled = false))

        assertEquals(false, viewModel.state.value.homeLayout.dock.isEnabled)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun updatesDockCapacityAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockCapacity(capacity = 7))

        assertEquals(7, viewModel.state.value.homeLayout.dock.capacity)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun updatesDockCapacityBelowCurrentItemCountAndSavesLayout() {
        val phone = app(label = "Phone")
        val camera = app(label = "Camera")
        val savedLayout =
            HomeLayoutDefaults.standard().copy(
                dock =
                    HomeLayoutDefaults.standard().dock.copy(
                        items = listOf(appShortcut(app = phone), appShortcut(app = camera)),
                    ),
            )
        val repository = FakeHomeLayoutRepository(savedLayout = savedLayout)
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(listOf(phone, camera)),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockCapacity(capacity = 1))

        assertEquals(1, viewModel.state.value.homeLayout.dock.capacity)
        assertEquals(savedLayout.dock.items, viewModel.state.value.homeLayout.dock.items)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun updatesDockIconSizeAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockIconSize(sizeDp = 52))

        assertEquals(52, viewModel.state.value.homeLayout.dock.iconSizeDp)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun updatesDockBackgroundAlphaAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockBackgroundAlpha(alphaPercent = 85))

        assertEquals(85, viewModel.state.value.homeLayout.dock.backgroundAlphaPercent)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun updatesDockItemSpacingAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockItemSpacing(spacingDp = 14))

        assertEquals(14, viewModel.state.value.homeLayout.dock.itemSpacingDp)
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

    private class FakeInstalledAppRepository(
        private val apps: List<InstalledApp>,
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
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

    private fun appShortcut(app: InstalledApp): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("dock:${app.label.lowercase()}"),
            appIdentity = app.identity,
            label = app.label,
        )
}
