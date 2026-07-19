package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockEditRejectionReason
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDockEditReducerTest {
    @Test
    fun appliesDockEditsToActiveLayout() {
        val repository = FakeHomeLayoutRepository()
        val state = LauncherShellState()

        val updatedState =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.AddAppToDock(phone),
            )

        assertEquals(listOf("Phone"), updatedState.homeLayout.dock.items.filterIsInstance<AppShortcutItem>().labels)
        assertEquals(updatedState.homeLayout, repository.savedLayoutSet?.activeLayout)
    }

    @Test
    fun exposesRejectedDockEditsWithoutSavingTheLayout() {
        val repository = FakeHomeLayoutRepository()
        val state = LauncherShellState()

        val updatedState =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.OpenHome,
            )

        assertEquals(DockEditRejectionReason.ITEM_NOT_FOUND, updatedState.dockEditRejectionReason)
        assertSame(state.homeLayout, updatedState.homeLayout)
        assertEquals(null, repository.savedLayout)
    }

    @Test
    fun exposesDisabledDockReasonForRejectedHomeToDockTransfer() {
        val repository = FakeHomeLayoutRepository()
        val shortcut = AppShortcutItem(LauncherItemId("phone"), phoneIdentity, "Phone")
        val state =
            LauncherShellState(
                homeLayout =
                    HomeLayoutDefaults.standard().copy(
                        dock = HomeLayoutDefaults.standard().dock.copy(isEnabled = false),
                        pages =
                            listOf(
                                HomeLayoutDefaults.standard().selectedPage.copy(
                                    items = listOf(shortcut.copy(placement = GridPlacement(GridCell(0, 0)))),
                                ),
                            ),
                    ),
            )

        val updatedState = reducer(repository).reduce(state, LauncherShellAction.MoveHomeItemToDock(shortcut.id))

        assertEquals(DockEditRejectionReason.DOCK_DISABLED, updatedState.dockEditRejectionReason)
        assertEquals(state.homeLayout, updatedState.homeLayout)
        assertEquals(null, repository.savedLayout)
    }

    @Test
    fun persistsExactDockReorderOnTheActiveLayout() {
        val repository = FakeHomeLayoutRepository()
        val phoneShortcut = AppShortcutItem(LauncherItemId("phone"), phoneIdentity, "Phone")
        val cameraShortcut = AppShortcutItem(LauncherItemId("camera"), phoneIdentity, "Camera")
        val state =
            LauncherShellState(
                homeLayout =
                    HomeLayoutDefaults.standard().copy(
                        dock = HomeLayoutDefaults.standard().dock.copy(items = listOf(phoneShortcut, cameraShortcut)),
                    ),
            )

        val updatedState =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.MoveDockShortcutToIndex(cameraShortcut.id, targetIndex = 0),
            )

        assertEquals(listOf(cameraShortcut.id, phoneShortcut.id), updatedState.homeLayout.dock.items.map { it.id })
        assertEquals(updatedState.homeLayout, repository.savedLayoutSet?.activeLayout)
    }

    @Test
    fun persistsHomeToDockTransferOnTheActiveLayout() {
        val repository = FakeHomeLayoutRepository()
        val shortcut = AppShortcutItem(LauncherItemId("phone"), phoneIdentity, "Phone")
        val state =
            LauncherShellState(
                homeLayout =
                    HomeLayoutDefaults.standard().copy(
                        pages =
                            listOf(
                                HomeLayoutDefaults.standard().selectedPage.copy(
                                    items =
                                        listOf(
                                            shortcut.copy(
                                                placement = GridPlacement(GridCell(0, 0)),
                                            ),
                                        ),
                                ),
                            ),
                    ),
            )

        val updatedState = reducer(repository).reduce(state, LauncherShellAction.MoveHomeItemToDock(shortcut.id))

        assertEquals(listOf(shortcut.id), updatedState.homeLayout.dock.items.map { it.id })
        assertTrue(updatedState.homeLayout.selectedPage.items.isEmpty())
        assertEquals(updatedState.homeLayout, repository.savedLayoutSet?.activeLayout)
    }

    @Test
    fun appliesDockConfigurationToSettingsTargetLayout() {
        val layout = HomeLayoutDefaults.standard()
        val layoutSet = HomeLayoutSet.fromLayout(layout)
        val repository = FakeHomeLayoutRepository(savedLayoutSet = layoutSet)
        val state =
            LauncherShellState(
                destination = ShellDestination.SETTINGS,
                homeLayout = layout,
                homeLayoutSet = layoutSet,
            )

        val updatedState =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.SelectDockCapacity(capacity = 7),
            )

        assertEquals(7, updatedState.homeLayout.dock.capacity)
        assertEquals(7, repository.savedLayoutSet?.activeLayout?.dock?.capacity)
    }

    @Test
    fun appliesDockConfigurationToSelectedSettingsDeviceClass() {
        val phoneKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
        val foldableKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.FOLDABLE)
        val phoneLayout = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE)
        val foldableLayout = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE)
        val layoutSet =
            HomeLayoutSet(
                activeKey = phoneKey,
                layouts =
                    mapOf(
                        phoneKey to phoneLayout,
                        foldableKey to foldableLayout,
                    ),
            )
        val repository = FakeHomeLayoutRepository(savedLayoutSet = layoutSet)
        val state =
            LauncherShellState(
                destination = ShellDestination.SETTINGS,
                homeLayout = phoneLayout,
                homeLayoutSet = layoutSet,
                settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
                availableLayoutDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            )

        val updatedState =
            reducer(repository).reduce(
                state = state,
                action = LauncherShellAction.SelectDockIconSize(sizeDp = 40),
            )

        val savedLayoutSet = checkNotNull(repository.savedLayoutSet)
        assertEquals(44, savedLayoutSet.layoutFor(phoneKey).dock.iconSizeDp)
        assertEquals(40, savedLayoutSet.layoutFor(foldableKey).dock.iconSizeDp)
        assertEquals(phoneKey, savedLayoutSet.activeKey)
        assertEquals(44, updatedState.homeLayout.dock.iconSizeDp)
    }

    private fun reducer(repository: HomeLayoutRepository): LauncherDockEditReducer =
        LauncherDockEditReducer(
            dockEngine = DockEngine(),
            homeLayoutRepository = repository,
        )

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
        var savedLayoutSet: HomeLayoutSet? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = savedLayoutSet ?: super.loadHomeLayoutSet()

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
        }
    }

    private companion object {
        val phoneIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.phone"),
                activityName = AppActivityName(".MainActivity"),
            )
        val phone = InstalledApp(identity = phoneIdentity, label = "Phone")
    }

    private val List<AppShortcutItem>.labels: List<String>
        get() = map { item -> item.label }
}
