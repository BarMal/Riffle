package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellSettingsDockViewModelTest {
    @Test
    fun settingsDockEditsStayIndependentAcrossTabAndActiveDeviceSwitches() {
        val phoneKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
        val foldableKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.FOLDABLE)
        val repository =
            FakeHomeLayoutRepository().also { repo ->
                repo.savedLayoutSet =
                    HomeLayoutSet(
                        activeKey = phoneKey,
                        layouts =
                            mapOf(
                                phoneKey to HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE),
                                foldableKey to HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE),
                            ),
                    )
            }
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = InstalledAppRepository { emptyList() },
                homeLayoutRepository = repository,
            )
        val router = routerFor(viewModel)
        val foldedDock = DockModel(capacity = 4, iconSizeDp = 36, itemSpacingDp = 4)
        val unfoldedDock = DockModel(capacity = 8, iconSizeDp = 56, itemSpacingDp = 18)

        assertTrue(router.handle(LauncherShellAction.OpenSettings))
        assertTrue(router.handle(LauncherShellAction.SelectDockIconSize(foldedDock.iconSizeDp)))
        assertTrue(router.handle(LauncherShellAction.SelectDockItemSpacing(foldedDock.itemSpacingDp)))
        assertTrue(router.handle(LauncherShellAction.SelectDockCapacity(foldedDock.capacity)))
        assertEquals(foldedDock, viewModel.state.value.settingsSurfaceState().homeLayout.dock)

        assertTrue(router.handle(LauncherShellAction.SelectSettingsLayoutDeviceClass(HomeLayoutDeviceClass.FOLDABLE)))
        assertEquals(
            HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE).dock,
            viewModel.state.value.settingsSurfaceState().homeLayout.dock,
        )
        assertTrue(router.handle(LauncherShellAction.SelectDockIconSize(unfoldedDock.iconSizeDp)))
        assertTrue(router.handle(LauncherShellAction.SelectDockItemSpacing(unfoldedDock.itemSpacingDp)))
        assertTrue(router.handle(LauncherShellAction.SelectDockCapacity(unfoldedDock.capacity)))
        assertEquals(unfoldedDock, viewModel.state.value.settingsSurfaceState().homeLayout.dock)

        assertTrue(router.handle(LauncherShellAction.SelectSettingsLayoutDeviceClass(HomeLayoutDeviceClass.PHONE)))
        assertEquals(foldedDock, viewModel.state.value.settingsSurfaceState().homeLayout.dock)
        assertTrue(
            router.handle(
                LauncherShellAction.SelectHomeLayoutDeviceClass(
                    deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                    availableDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
                ),
            ),
        )
        assertEquals(foldedDock, viewModel.state.value.settingsSurfaceState().homeLayout.dock)
        assertTrue(router.handle(LauncherShellAction.SelectSettingsLayoutDeviceClass(HomeLayoutDeviceClass.FOLDABLE)))
        assertEquals(unfoldedDock, viewModel.state.value.settingsSurfaceState().homeLayout.dock)

        val savedLayoutSet = checkNotNull(repository.savedLayoutSet)
        assertEquals(foldedDock, savedLayoutSet.layoutFor(phoneKey).dock)
        assertEquals(unfoldedDock, savedLayoutSet.layoutFor(foldableKey).dock)
        assertNotEquals(
            savedLayoutSet.layoutFor(phoneKey).dock.iconSizeDp,
            savedLayoutSet.layoutFor(foldableKey).dock.iconSizeDp,
        )
        assertEquals(foldableKey, savedLayoutSet.activeKey)
    }

    private fun routerFor(viewModel: LauncherShellViewModel): LauncherActionRouter =
        LauncherActionRouter(
            activityActionHandler =
                LauncherActivityActionHandler(
                    requestDefaultHome = {},
                    navigate = viewModel::onNavigationActionSelected,
                    editHomePage = viewModel::onHomePageEdited,
                    editHomeShortcut = viewModel::onHomeShortcutEdited,
                    editDock = viewModel::onDockEdited,
                    hostedWidgetIdForRemovedShortcut = { null },
                    deleteHostedWidget = {},
                ),
            notificationActionHandler = LauncherNotificationActionHandler { false },
            settingsActionHandler =
                DefaultLauncherSettingsActionHandler(
                    callbacks =
                        LauncherSettingsActionCallbacks(
                            applySettingsState = viewModel::onLauncherSettingsActionSelected,
                            requestNotificationAccess = {},
                            requestOverlayDockPermission = {},
                            changeWallpaper = {},
                            exportBackup = {},
                            importBackup = {},
                        ),
                ),
            appActionHandler =
                LauncherAppActionHandler(
                    callbacks =
                        LauncherAppActionCallbacks(
                            launch =
                                LauncherAppLaunchCallbacks(
                                    launchApp = {},
                                    launchAppShortcut = {},
                                    openAppInfo = {},
                                    uninstallApp = {},
                                ),
                            addAppToHome = {},
                            requestAddWidget = {},
                            applyAppState = { viewModel.onAppActionSelected(it) },
                            appListRefreshed = {},
                        ),
                ),
        )

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository : HomeLayoutRepository {
        var savedLayoutSet: HomeLayoutSet? = null

        override fun loadHomeLayout(): HomeLayout? = savedLayoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayoutSet =
                savedLayoutSet
                    ?.withActiveLayout(layout)
                    ?: HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = savedLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
        }
    }
}
