package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockEditRejectionReason
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherShellDockViewModelTest {
    @Test
    fun clearsDockRejectionFeedbackWhenDismissedNavigatingOrLeavingEditMode() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = FakeHomeLayoutRepository(),
            )

        viewModel.onDockEdited(LauncherShellAction.OpenHome)
        assertEquals(DockEditRejectionReason.ITEM_NOT_FOUND, viewModel.state.value.dockEditRejectionReason)

        viewModel.onDockEditFeedbackDismissed()
        assertNull(viewModel.state.value.dockEditRejectionReason)

        viewModel.onDockEdited(LauncherShellAction.OpenHome)
        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenSettings)
        assertNull(viewModel.state.value.dockEditRejectionReason)

        viewModel.onDockEdited(LauncherShellAction.OpenHome)
        viewModel.onHomePageEdited(LauncherShellAction.EnterHomeEditMode)
        viewModel.onHomePageEdited(LauncherShellAction.ExitHomeEditMode)
        assertNull(viewModel.state.value.dockEditRejectionReason)
    }

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
    fun updatesDockNotificationCardsVisibilityAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockNotificationCardsEnabled(enabled = false))

        assertEquals(false, viewModel.state.value.homeLayout.dock.showNotificationCards)
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
    fun doesNotAddDockShortcutToEnabledZeroCapacityDock() {
        val phone = app(label = "Phone")
        val repository =
            FakeHomeLayoutRepository(
                savedLayout = HomeLayoutDefaults.standard().copy(dock = DockModel(capacity = 0)),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))

        assertEquals(0, viewModel.state.value.homeLayout.dock.capacity)
        assertEquals(emptyList<AppShortcutItem>(), viewModel.state.value.homeLayout.dock.items)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun addDockShortcutEnablesDockAndSavesLayout() {
        val phone = app(label = "Phone")
        val repository =
            FakeHomeLayoutRepository(
                savedLayout =
                    HomeLayoutDefaults.standard().copy(
                        dock = DockModel(capacity = 0, isEnabled = false),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))

        assertEquals(true, viewModel.state.value.homeLayout.dock.isEnabled)
        assertEquals(1, viewModel.state.value.homeLayout.dock.capacity)
        assertEquals(
            listOf("Phone"),
            viewModel.state.value.homeLayout.dock.items.filterIsInstance<AppShortcutItem>().labels,
        )
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
    fun updatesDockVisualEffectAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockVisualEffect(DockVisualEffect.ELEVATED))

        assertEquals(DockVisualEffect.ELEVATED, viewModel.state.value.homeLayout.dock.visualEffect)
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

    @Test
    fun updatesDockShapeAndGridControlsSpacingAndSavesLayout() {
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.SelectDockCornerRadius(cornerRadiusDp = 18))
        viewModel.onDockEdited(LauncherShellAction.SelectDockHomeControlsSpacing(spacingDp = 20))

        assertEquals(18, viewModel.state.value.homeLayout.dock.cornerRadiusDp)
        assertEquals(20, viewModel.state.value.homeLayout.dock.homeControlsSpacingDp)
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

    private val List<AppShortcutItem>.labels: List<String>
        get() = map { item -> item.label }
}
